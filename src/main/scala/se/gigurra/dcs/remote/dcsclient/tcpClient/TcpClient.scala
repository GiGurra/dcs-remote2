package se.gigurra.dcs.remote.tcpClient

import java.io.{BufferedInputStream, InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket}
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

import com.twitter.util.{Duration, JavaTimer, NonFatal}
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.Logging

import scala.collection.JavaConversions._
import scala.collection.{concurrent, mutable}
import scala.util.{Failure, Success, Try}

/**
  * Why? Because akka was too heavy weight and nio is too cumbersome
  */
case class TcpClient(env: String, port: Int) extends Logging {
  private val inetSockAddr = new InetSocketAddress("127.0.0.1", port)
  private val timer = new JavaTimer(isDaemon = true)
  private val charset = Charset.forName("UTF-8")
  @volatile private var socket: Option[Socket] = None
  private val maxPendingRequests = 1000
  private val unsentRequests = new ConcurrentLinkedQueue[Request]
  private val sentRequests = new concurrent.TrieMap[String, Request]

  def request(req: Request): Unit = {
    if (!isConnected)
      req.onFailure(s"Request dropped - not connected to dcs!")
    if (size >= maxPendingRequests)
      req.onFailure(s"Request dropped - to many pending requests!")
    unsentRequests.add(req)
    writeThread.wakeUp()
  }

  // Only the reconnectThread loop may set socket = None, for race reasons
  private val reconnectThread = new WorkerThread[Any] {
    override def update(s: Socket) {}

    override def run(): Unit = {
      while (true) {
        println("Rec")
        if (!isConnected) {
          val s = new Socket()
          s.setTcpNoDelay(true)
          Try {
            s.connect(inetSockAddr)
          } match {
            case Success(_) => logger.info(s"Connected to dcs environment $env on port $port!")
              socket = Some(s)
            case Failure(e) => logger.warning(s"Failed to connect ($e) to dcs environment $env! on port $port")
              s.kill()
          }
        }
        sleep()
      }
    }
    start()
  }

  private val writeThread = new WorkerThread[OutputStream] {
    override def update(socket: Socket): Unit = {
      while (unsentRequests.nonEmpty && socket.isAlive) {
        val req = unsentRequests.poll()
        sentRequests.put(req.id, req)
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


  private val readThread = new WorkerThread[InputStream] {

    val lineSplitter = new LineSplitter
    val readbuf = new Array[Byte](2048)

    override def update(socket: Socket): Unit = {
      while (sentRequests.nonEmpty && socket.isAlive) {
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
                req.onComplete(line)
              } catch {
                case NonFatal(e) =>
                  logger.error(s"Failed handling request ${req.id}: $e", e)
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


  def isConnected(): Boolean = {
    socket.fold(false)(!_.isAlive)
  }

  implicit class RichSocket(s: Socket) {
    def isAlive: Boolean = s.synchronized {
      !s.isClosed && s.isConnected && !s.isInputShutdown && !s.isOutputShutdown
    }

    def kill(): Unit = s.synchronized {
      Try(s.close())
    }
  }

  private abstract class WorkerThread[StreamType]() extends Thread {
    private var hasWork = false
    protected val streams = new mutable.WeakHashMap[Socket, StreamType]

    override def run(): Unit = {
      socket.foreach(update)
      sleep()
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

case class Request(script: String,
                   onComplete: String => Unit,
                   onFailure: String => Unit,
                   timeOut: Duration,
                   id: String = UUID.randomUUID().toString) {}