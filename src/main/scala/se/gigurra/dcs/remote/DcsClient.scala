package se.gigurra.dcs.remote

import java.net.InetSocketAddress

import com.twitter.util.Future
import se.gigurra.dcs.remote.dcsclient.Request
import se.gigurra.dcs.remote.dcsclient.TcpClient

case class DcsClient(name: String, port: Int) {

  private val addr = new InetSocketAddress("127.0.0.1", port)
  private val client = TcpClient(name, port)

  def get(luaMethod: String): Future[String] = {
    client.request(Request(s"return $luaMethod"))
  }

  def delete(luaMethod: String): Future[Unit] = {
    client.request(Request(s"$luaMethod = nil")).map(_ => ())
  }

  def post(script: String): Future[Unit] = {
    client.request(Request(script)).map(_ => ())
  }

}

object DcsClient {
  def createClients(config: Configuration): Map[String, DcsClient] = {
    config.mappings.map { env =>
      env.name -> DcsClient(env.name, env.port)
    }.toMap
  }
}