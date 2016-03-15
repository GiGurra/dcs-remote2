package se.gigurra.dcs.remote

import java.net.InetSocketAddress

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.Http
import com.twitter.util.Await
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.{Capture, Logging}
import se.gigurra.serviceutils.twitter.service.ExceptionFilter

object DcsRemote extends Logging {

  def main(args: Array[String]): Unit = {

    Capture.stdOutToFile(s"dcs-remote-debug-log.txt", append = true)
    Capture.stdErrToFile(s"dcs-remote-log.txt", append = true)

    val config = Configuration.readFromFile()
    logger.info(s"Config:\n ${JSON.write(config)}")

    if (config.show_tray_icon)
      TrayIcon.setup()

    val cache = new ResourceCache(config.cache_size_mb)
    val clients = DcsClient.createClients(config, config.connect_to_dcs)
    val service = ExceptionFilter[Exception]() andThen new RestService(config, cache, clients)
    val server = ServerBuilder()
      // .tls(certificatePath = "", keyPath = "")
      .codec(Http())
      .bindTo(new InetSocketAddress(config.rest_port))
      .name("DCS Remote")
      .build(service)

    Await.ready(server)
  }
}
