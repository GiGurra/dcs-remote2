package se.gigurra.dcs.remote

import java.net.InetSocketAddress
import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor.{Props, ActorSystem}
import com.twitter.util.Future
import se.gigurra.dcs.remote.dcsclient.DcsClientActor
import se.gigurra.dcs.remote.dcsclient.DcsClientActor.Request
import se.gigurra.serviceutils.twitter.future.TwitterFutures

import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

case class DcsClient(name: String, port: Int) {

  private val addr = new InetSocketAddress("127.0.0.1", port)
  private val clientActor = DcsClient.actorSystem.actorOf(Props(new DcsClientActor(addr)))
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

}

object DcsClient {
  def createClients(config: Configuration): Map[String, DcsClient] = {
    config.mappings.map { env =>
      env.name -> DcsClient(env.name, env.port)
    }.toMap
  }

  val actorSystem = ActorSystem()
}