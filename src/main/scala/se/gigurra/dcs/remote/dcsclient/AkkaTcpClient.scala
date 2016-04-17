package se.gigurra.dcs.remote.dcsclient

import java.net.InetSocketAddress

import akka.actor._
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import com.fasterxml.jackson.core.{JsonFactory, JsonToken}
import com.twitter.io.Buf
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.Logging
import se.gigurra.serviceutils.twitter.service.ServiceErrorsWithoutAutoLogging

import scala.collection.concurrent
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by kjolh on 3/17/2016.
  */
class AkkaTcpClient(address: InetSocketAddress)
  extends Actor
    with ServiceErrorsWithoutAutoLogging
    with Logging {
  import context.system
  implicit val _ec = system.dispatcher
  val pendingRequests = new concurrent.TrieMap[String, Request]()
  val lineBuffer = new LineSplitter
  val saxParserFactory = new JsonFactory()
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
    val lines = lineBuffer.apply(data.asByteBuffers)
    lines foreach { line =>
      Try {
        val requestId = saxParseGetRequestId(line)
        pendingRequests.remove(requestId).foreach { request =>
          request.promise.setValue(Buf.ByteArray.Owned(line))
        }
      } match {
        case Success(result) =>
        case Failure(e) =>
          logger.error(e, s"Unable to handle reply from DCS")
      }
    }
  }

  def bounce(request: Request): Unit = {
    system.scheduler.scheduleOnce(1 seconds) {
      request.promise.setException(unavailable(s"Not connected to dcs: Skipping request ${request.id}"))
    }
  }

  def writeFailed(): Unit = {
    logger.warning(s"AkkaTcpClient Write failed - Output buffer likely full - ignoring request!")
  }

  def connect(): Unit = {
    system.scheduler.scheduleOnce(1 seconds)(IO(Tcp) ! Connect(address))
  }

  def saxParseGetRequestId(line: Array[Byte]): String = {

    val saxParser = saxParserFactory.createParser(line)
    var requestId: Option[String] = None

    if (saxParser.nextToken() != JsonToken.START_OBJECT)
      throw new RuntimeException(s"Unable to parse Json from DCS: $line")

    while (requestId.isEmpty && saxParser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = saxParser.getCurrentName
      saxParser.nextToken() // move from field name to field value
      if (fieldName == "requestId") {
        requestId = Some(saxParser.getValueAsString)
      } else {
        saxParser.skipChildren()
      }
    }

    requestId.getOrElse(throw new RuntimeException("Could not find .requestId field in response from DCS!"))
  }
}

object AkkaTcpClient {
  private val actorSystem = ActorSystem()
  def apply(address: InetSocketAddress): ActorRef = actorSystem.actorOf(Props(new AkkaTcpClient(address)))
}