package se.gigurra.dcs.remote

import java.net.InetSocketAddress

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.Http
import com.twitter.util.Await
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.Logging
import se.gigurra.serviceutils.twitter.service.ExceptionFilter

object DcsRemote extends Logging {

  def main(args: Array[String]): Unit = {
    val config = Configuration.readFromFile()
    logger.info(s"Config:\n ${JSON.write(config)}")

    val trayIcon = TrayIcon.setup()
    val cache = new ResourceCache(config.cache_size_mb)
    val clients = DcsClient.createClients(config)
    val service = ExceptionFilter[Exception]() andThen new RestService(config, cache, clients)
    val server = ServerBuilder()
      // .tls(certificatePath = "", keyPath = "")
      .codec(Http())
      .bindTo(new InetSocketAddress(config.rest_port))
      .name("Valhalla Server")
      .build(service)

    Await.ready(server)
  }
}
