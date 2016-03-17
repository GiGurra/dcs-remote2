package se.gigurra.dcs.remote.dcsclient

import java.net.InetSocketAddress

import com.twitter.util.Future
import se.gigurra.dcs.remote.Configuration
import se.gigurra.serviceutils.twitter.logging.Logging

case class DcsClient(name: String, port: Int) {

  private val addr = new InetSocketAddress("127.0.0.1", port)
  private val client = AkkaTcpClient.apply(addr)

  private def request(s: String): Future[String] = {
    val request = Request(s)
    client ! request
    request.reply
  }

  def get(luaMethod: String): Future[String] = {
    request(s"return $luaMethod")
  }

  def delete(luaMethod: String): Future[Unit] = {
    request(s"$luaMethod = nil").map(_ => ())
  }

  def post(script: String): Future[Unit] = {
    request(script).map(_ => ())
  }
}

object DcsClient extends Logging {
  def createClients(config: Configuration, connectToDcs: Boolean): Map[String, DcsClient] = {
    if (connectToDcs) {
      config.mappings.map { env =>
        env.name -> DcsClient(env.name, env.port)
      }.toMap
    } else {
      logger.info(s"connect_to_dcs = false. NOT creating any connection to DCS!")
      Map.empty
    }
  }
}