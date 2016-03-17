package se.gigurra.dcs.remote.dcsclient

import java.net.InetSocketAddress

import akka.actor._
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.Logging
import se.gigurra.serviceutils.twitter.service.ServiceErrors

import scala.collection.concurrent
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by kjolh on 3/17/2016.
  */
class AkkaTcpClient(address: InetSocketAddress)
  extends Actor
    with ServiceErrors
    with Logging {
  import context.system
  implicit val _ec = system.dispatcher
  val pendingRequests = new concurrent.TrieMap[String, Request]()
  val lineBuffer = new LineSplitter
  connect()

  ////////////////////////////////////////////////////

  def receive: Receive = {
    case CommandFailed(_: Connect) => connect()
    case Connected(_, _)           => connected()
    case request: Request          => bounce(request)
  }

  case class receiveOnline(dcs: ActorRef) {
    def apply: Receive = {
      case CommandFailed(_: Write) => writeFailed()
      case request: Request        => send(dcs, request)
      case Received(data)          => received(data)
      case _: ConnectionClosed     => disconnected()
    }
  }

  ////////////////////////////////////////////////////

  def connected(): Unit = {
    logger.info(s"Connected to DCS!")
    val dcs = sender()
    dcs ! Register(self)
    context become receiveOnline(dcs).apply
  }

  def disconnected(): Unit = {
    logger.info(s"Disconnected from DCS!")
    context.unbecome()
    connect()
  }

  def send(dcs: ActorRef, request: Request): Unit = {

    pendingRequests.put(request.id, request)
    system.scheduler.scheduleOnce(request.timeOut.inSeconds seconds) {
      pendingRequests.remove(request.id).foreach { _ =>
        request.promise.setException(timeout(s"Request ${request.id} to dcs timed out!"))
      }
    }

    val jsonString = JSON.writeMap(Map("script" -> request.script, "requestId" -> request.id.toString))
    dcs ! Write(ByteString(jsonString + "\n"))
  }

  def received(data: ByteString): Unit = {
    val lines = lineBuffer.apply(data.asByteBuffer)
    lines foreach { line =>
      Try {
        val reply = JSON.read[Reply](line)
        pendingRequests.remove(reply.requestId).foreach { request =>
          request.promise.setValue(line)
        }
      } match {
        case Success(result) =>
        case Failure(e) =>
          logger.error(e, s"Unable to handle reply from DCS")
      }
    }
  }

  def bounce(request: Request): Unit = {
    logger.warning(s"Not connected to dcs: Skipping request ${request.id}")
    system.scheduler.scheduleOnce(1 seconds) {
      request.promise.setException(unavailable(s"Not connected to dcs: Skipping request ${request.id}"))
    }
  }

  def writeFailed(): Unit = {
    logger.warning(s"AkkaTcpClient Write failed - Output buffer likely full - ignoring request!")
    system.scheduler.scheduleOnce(1 seconds)(IO(Tcp) ! Connect(address))
  }

  def connect(): Unit = {
    system.scheduler.scheduleOnce(1 seconds)(IO(Tcp) ! Connect(address))
  }
}

object AkkaTcpClient {
  private val actorSystem = ActorSystem()
  def apply(address: InetSocketAddress): ActorRef = actorSystem.actorOf(Props(new AkkaTcpClient(address)))
}