package se.gigurra.dcs.remote.dcsclient

import java.net.InetSocketAddress

import com.twitter.io.Buf
import com.twitter.util.Future
import se.gigurra.dcs.remote.{Configuration, RelayConfig}
import se.gigurra.serviceutils.twitter.logging.Logging
import com.twitter.finagle.http.{Request => FinagleRequest}

trait DcsClient {
  def get(originalRequest: FinagleRequest, luaMethod: String): Future[Buf]
  def delete(originalRequest: FinagleRequest, luaMethod: String): Future[Unit]
  def post(originalRequest: FinagleRequest, script: String): Future[Unit]
}

case class DcsClientDirect(name: String, port: Int) extends DcsClient {

  private val addr = new InetSocketAddress("127.0.0.1", port)
  private val client = AkkaTcpClient.apply(addr)

  private def request(s: String): Future[Buf] = {
    val request = Request(s)
    client ! request
    request.reply
  }

  def get(originalRequest: FinagleRequest, luaMethod: String): Future[Buf] = {
    request(s"return $luaMethod")
  }

  def delete(originalRequest: FinagleRequest, luaMethod: String): Future[Unit] = {
    request(s"$luaMethod = nil").map(_ => ())
  }

  def post(originalRequest: FinagleRequest, script: String): Future[Unit] = {
    request(script).map(_ => ())
  }
}

case class DcsClientRelayed(cfg: RelayConfig) extends DcsClient with Logging {

  logger.info(s"Relay mode enabled - forwarding all dcs calls to $cfg")
  val service = com.twitter.finagle.Http.client.newService(s"${cfg.host}:${cfg.port}")

  override def get(req: FinagleRequest, luaMethod: String): Future[Buf] = {
    req.host = cfg.host
    service.apply(req).map(_.content)
  }

  override def delete(req: FinagleRequest, luaMethod: String): Future[Unit] = {
    req.host = cfg.host
    service.apply(req).unit
  }

  override def post(req: FinagleRequest, script: String): Future[Unit] = {
    req.host = cfg.host
    service.apply(req).unit
  }
}

object DcsClient extends Logging {
  def createClients(config: Configuration, connectToDcs: Boolean): Map[String, DcsClient] = {
    if (connectToDcs) {
      config.mappings.map { env =>
        env.name -> (config.relay match {
          case Some(relay) => DcsClientRelayed(relay)
          case None => DcsClientDirect(env.name, env.port)
        })
      }.toMap
    } else {
      logger.info(s"connect_to_dcs = false. NOT creating any connections to DCS!")
      Map.empty
    }
  }
}