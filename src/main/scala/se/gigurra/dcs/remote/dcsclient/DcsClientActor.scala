package se.gigurra.dcs.remote.dcsclient

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{Actor, ActorRef, actorRef2Scala}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.Logging

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object DcsClientActor extends Logging {
  case class RegisterListener(listener: DcsRemoteListener)
  case class Request(
    script: String,
    onComplete: String => Unit,
    onTimeout: String => Unit,
    timeOut: FiniteDuration,
    id: String = UUID.randomUUID().toString) {}
  case class RequestTimedOut(requesId: String)
  case object GetStatus
}

class DcsClientActor(address: InetSocketAddress) extends Actor with Logging {
  import DcsClientActor._
  import Tcp._
  import context.system

  ////////////////////////

  private var status: ConnStatus = DISCONNECTED
  private var server: Option[ActorRef] = None
  private val buffer = new LineSplitter
  private val listeners = new mutable.ArrayBuffer[DcsRemoteListener]
  private val pendingRequests = new mutable.HashMap[String, Request]
  reconnect()

  ////////////////////////

  def receive = {
    case CommandFailed(_: Connect)  => handleConnectFailed()
    case Connected(remote, local)   => handleConnected()
    case CommandFailed(w: Write)    => handleWriteFailed()
    case Received(data)             => handleReceived(data)
    case RegisterListener(listener) => handleRegisterListener(listener)
    case request: Request           => handleRequest(request)
    case RequestTimedOut(id)        => handleRequestTimeout(id)
    case GetStatus                  => handleGetStatus()
    case _: ConnectionClosed        => handleDisconnected()
  }

  ////////////////////////////////////////

  def reconnect() {
    system.scheduler.scheduleOnce(1 seconds)(IO(Tcp) ! Connect(address))(system.dispatcher)
  }

  def send(script: String, requestId: String) {
    val jsonString = JSON.writeMap(Map("script" -> script, "requestId" -> requestId))
    server.foreach { _ ! Write(ByteString(jsonString + "\n")) }
  }

  def isConnected: Boolean = {
    status == CONNECTED
  }

  def handleMsg(msg: String) {
    Try {
      handleRequestReply(JSON.read[Reply](msg), msg)
    } match {
      case Success(_) =>
      case Failure(e) => e.printStackTrace()
    }
  }

  def handleConnectFailed() {
    listeners.foreach { _.onFailedConnect() }
    reconnect()
  }

  def handleConnected() {
    logger.info(s"DCS Client connected")
    status = CONNECTED
    buffer.clear()
    listeners.foreach { _.onConnect() }
    server = Some(sender())
    server.get ! Register(self)
  }

  def handleWriteFailed() {
    // O/S buffer was full
    logger.warning("DcsRemote socket:write failed")
  }

  def handleReceived(data: ByteString) {
    buffer(data.utf8String) foreach handleMsg
  }

  def handleRegisterListener(listener: DcsRemoteListener) {
    listeners += listener
  }

  def handleRequest(request: Request) {
    pendingRequests.put(request.id, request)
    system.scheduler.scheduleOnce(request.timeOut)(self ! RequestTimedOut(request.id))(system.dispatcher)
    send(request.script, request.id)
  }

  def handleRequestReply(reply: Reply, msg: String) {
    pendingRequests.remove(reply.requestId) foreach (_.onComplete(msg))
  }

  def handleRequestTimeout(requestId: String) {
    pendingRequests.remove(requestId) foreach (_.onTimeout(requestId))
  }

  def handleGetStatus() {
    sender() ! status
  }

  def handleDisconnected() {
    logger.info(s"DCS Client disconnected")
    status = DISCONNECTED
    listeners.foreach { _.onDisconnect() }
    reconnect()
  }

}