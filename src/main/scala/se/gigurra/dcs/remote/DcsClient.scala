package se.gigurra.dcs.remote

import java.net.InetSocketAddress
import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor.{Props, ActorSystem}
import com.twitter.util.{Duration, Future}
import se.gigurra.dcs.remote.dcsclient.DcsClientActor
import se.gigurra.dcs.remote.dcsclient.DcsClientActor.Request
import se.gigurra.dcs.remote.tcpClient.TcpClient
import se.gigurra.serviceutils.twitter.future.TwitterFutures

import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

case class DcsClient(name: String, port: Int) {

  private val addr = new InetSocketAddress("127.0.0.1", port)
  private val clientActor = DcsClient.actorSystem.actorOf(Props(new DcsClientActor(addr)).withDispatcher("my-dispatcher"))
  private val timeout: FiniteDuration = FiniteDuration.apply(2, TimeUnit.SECONDS)

  def get(luaMethod: String): Future[String] = {

    val p = Promise[String]()
    clientActor ! Request(
      s"return $luaMethod",
      msg => p.complete(Try(msg)),
      id => p.failure(new TimeoutException(s"Get-Request to DCS/$name/$luaMethod timed out(id=$id)")),
      timeout)

    TwitterFutures.scalaToTwitterFuture(p.future)
  }

  def delete(luaMethod: String): Future[Unit] = {

    val p = Promise[Unit]()

    clientActor ! Request(
      s"$luaMethod = nil",
      msg => p.complete(Try(())),
      id => p.failure(new TimeoutException(s"Delete-Request to DCS/$name/$luaMethod timed out(id=$id)")),
      timeout)

    TwitterFutures.scalaToTwitterFuture(p.future)
  }

  def post(script: String): Future[Unit] = {

    val p = Promise[Unit]()

    clientActor ! Request(
      script,
      msg => p.complete(Try(())),
      id => p.failure(new TimeoutException(s"Post-Request to DCS/$name timed out(id=$id)")),
      timeout)

    TwitterFutures.scalaToTwitterFuture(p.future)
  }

  /*

  Alternative implementation.. if akka starts fucking up

  private val client = TcpClient(name, port)
  private val twitterTimeout = Duration.fromSeconds(2)

  def get(luaMethod: String): Future[String] = {

    val promise = com.twitter.util.Promise[String]

    client.request(tcpClient.Request(
      s"return $luaMethod",
      msg => promise.setValue(msg),
      id => promise.setException(new TimeoutException(s"Get-Request to DCS/$name/$luaMethod timed out(id=$id)")),
      twitterTimeout))

    promise
  }

  def delete(luaMethod: String): Future[Unit] = {

    val promise = com.twitter.util.Promise[Unit]

    client.request(tcpClient.Request(
      s"$luaMethod = nil",
      msg => promise.setValue(()),
      id => promise.setException(new TimeoutException(s"Delete-Request to DCS/$name/$luaMethod timed out(id=$id)")),
      twitterTimeout))

    promise
  }

  def post(script: String): Future[Unit] = {

    val promise = com.twitter.util.Promise[Unit]

    client.request(tcpClient.Request(
      script,
      msg => promise.setValue(()),
      id => promise.setException(new TimeoutException(s"Post-Request to DCS/$name timed out(id=$id)")),
      twitterTimeout))

    promise
  }*/

}

object DcsClient {
  def createClients(config: Configuration): Map[String, DcsClient] = {
    config.mappings.map { env =>
      env.name -> DcsClient(env.name, env.port)
    }.toMap
  }

  lazy val actorSystem = ActorSystem()
}