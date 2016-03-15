package se.gigurra.dcs.remote.dcsclient

import java.io.{BufferedInputStream, InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket}
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue

import com.twitter.util._
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.Logging
import se.gigurra.serviceutils.twitter.service.ServiceErrors

import scala.collection.JavaConversions._
import scala.collection.{concurrent, mutable}
import scala.util.{Failure, Success, Try}

/**
  * Why? Because akka was too heavy weight and nio is too cumbersome
  */
case class TcpClient(env: String, port: Int) extends Logging with ServiceErrors {
  private val inetSockAddr = new InetSocketAddress("127.0.0.1", port)
  private val timer = new JavaTimer(isDaemon = true)
  private val charset = Charset.forName("UTF-8")
  @volatile private var socket: Option[Socket] = None
  private val maxPendingRequests = 1000
  private val unsentRequests = new ConcurrentLinkedQueue[Request]
  private val sentRequests = new concurrent.TrieMap[String, Request]
  @volatile private var timedOutRequestsInARow = 0

  def request(req: Request): Future[String] = {
    if (!isConnected) {
      timer.schedule(Time.now + Duration.fromSeconds(2))(req.promise.setException(unavailable(s"Request dropped - not connected to dcs!")))
    } else if (size >= maxPendingRequests) {
      timer.schedule(Time.now + Duration.fromSeconds(2))(req.promise.setException(tooManyRequests(s"Request dropped - to many pending requests!")))
    } else {
      unsentRequests.add(req)
      writeThread.wakeUp()
    }
    req.reply
  }

  // Only the reconnectThread loop may set socket = None, for race reasons
  private val reconnectThread = new WorkerThread[Any]("reconnect") {
    override def update(s: Socket) {}

    override def run(): Unit = {
      try {
        while (true) {
          if (!isConnected) {
            val s = new Socket()
            s.setTcpNoDelay(true)
            Try {
              s.connect(inetSockAddr)
            } match {
              case Success(_) =>
                logger.info(s"Connected to dcs environment $env on port $port!")
                socket = Some(s)
              case Failure(e) =>
                logger.warning(s"Failed to connect ($e) to dcs environment $env! on port $port")
                s.kill()
            }
          }
          sleep()
        }
      } catch {
        case NonFatal(e) =>
          logger.fatal(e, s"reconnect/$this crashed! - shutting down Dcs Remote")
          System.exit(1)
      }
    }
    start()
  }

  private val writeThread = new WorkerThread[OutputStream]("write") {
    override def update(socket: Socket): Unit = {
      while (unsentRequests.nonEmpty && socket.isAlive) {
        val req = unsentRequests.poll()
        sentRequests.put(req.id, req)
        timer.schedule(Time.now + Duration.fromSeconds(2)){
          sentRequests.remove(req.id) foreach { req =>
            timedOutRequestsInARow+=1
            req.promise.setException(timeout(s"Request id ${req.id} to dcs environment $env timed out!"))
            if (timedOutRequestsInARow >= 3) {
              timedOutRequestsInARow = 0
              logger.warning(s"Restarting connection to dcs due to too many timed out requests in a row")
              socket.kill()
            }
          }
        }

        Try {
          val stream = streams.getOrElseUpdate(socket, socket.getOutputStream)
          val jsonString = JSON.writeMap(Map("script" -> req.script, "requestId" -> req.id.toString)) + "\n"
          val bytes = jsonString.getBytes(charset)
          stream.write(bytes)
        } match {
          case Success(_) => // Ok
          case Failure(e) =>
            logger.warning(s"Could not send request to dcs environment $env")
            socket.kill()
        }
      }
    }
    start()
  }


  private val readThread = new WorkerThread[InputStream]("read") {

    val lineSplitter = new LineSplitter
    val readbuf = new Array[Byte](2048)

    override def update(socket: Socket): Unit = {
      lineSplitter.clear()
      while (socket.isAlive) {
        Try {
          val stream = streams.getOrElseUpdate(socket, new BufferedInputStream(socket.getInputStream))
          val nRead = stream.read(readbuf)
          if (nRead > 0) {
            val newLines = lineSplitter.apply(readbuf, nRead)
            val replies = newLines.map(line => (line, JSON.read[Reply](line)))
            for {
              (line, reply) <- replies
              req <- sentRequests.remove(reply.requestId)
            } {
              try {
                req.promise.setValue(line)
                timedOutRequestsInARow = 0
              } catch {
                case NonFatal(e) =>
                  logger.error(e, s"Failed handling request ${req.id}: $e")
              }
            }
          } else if (nRead < 0) {
            logger.warning(s"Could not read requests from dcs environment $env, reason: nRead = $nRead")
            socket.kill()
          }
        } match {
          case Success(_) => // Ok
          case Failure(e) =>
            logger.warning(s"Could not read requests from dcs environment $env, reason: $e")
            socket.kill()
        }
      }
    }
    start()
  }


  def isConnected: Boolean = {
    socket.fold(false)(_.isAlive)
  }

  implicit class RichSocket(s: Socket) {
    def isAlive: Boolean = s.synchronized {
      !s.isClosed && s.isConnected && !s.isInputShutdown && !s.isOutputShutdown
    }

    def kill(): Unit = s.synchronized {
      Try(s.close())
    }
  }

  private abstract class WorkerThread[StreamType](name: String) extends Thread {
    private var hasWork = false
    protected val streams = new mutable.WeakHashMap[Socket, StreamType]

    override def run(): Unit = {
      try {
        while (true) {
          socket.foreach(update)
          sleep()
        }
      } catch {
        case NonFatal(e) =>
          logger.fatal(e, s"$name/$this crashed! - shutting down Dcs Remote")
          System.exit(1)
      }
    }

    def update(socket: Socket): Unit

    def wakeUp(): Unit = synchronized {
      hasWork = true
      notify()
    }

    def sleep(): Unit = synchronized {
      if (hasWork) {
        // do the work
        hasWork = false
      } else {
        wait(2000)
      }
    }
  }

  private def size: Int = {
    unsentRequests.size + sentRequests.size
  }
}