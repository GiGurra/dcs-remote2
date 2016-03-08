package se.gigurra.dcs.remote

import java.net.InetSocketAddress

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.Http
import com.twitter.util.Await
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.service.ExceptionFilter

object DcsRemote {
  val logger = com.twitter.logging.Logger.get(this.getClass)
  val config = Configuration.readFromFile()

  def main(args: Array[String]): Unit = {

    logger.info(s"Starting DCS Remote REST proxy with config:\n ${JSON.write(config)}")

    val service = ExceptionFilter[Exception]() andThen new RestService(config)
    val server = ServerBuilder()
      // .tls(certificatePath = "", keyPath = "")
      .codec(Http())
      .bindTo(new InetSocketAddress(config.rest_port))
      .name("Valhalla Server")
      .build(service)

    Await.ready(server)
  }

}
