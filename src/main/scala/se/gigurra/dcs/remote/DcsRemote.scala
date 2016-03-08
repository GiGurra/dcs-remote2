package se.gigurra.dcs.remote

import java.net.InetSocketAddress

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.Http
import se.gigurra.serviceutils.json.JSON

object DcsRemote {
  val logger = com.twitter.logging.Logger.get(this.getClass)
  val config = Configuration.readFromFile()

  def main(args: Array[String]): Unit = {

    logger.info(s"Starting DCS Remote REST proxy ..")

    println(JSON.write(config))

    val server = ServerBuilder()
      // .tls(certificatePath = "", keyPath = "")
      .codec(Http())
      .bindTo(new InetSocketAddress(config.rest_port))
      .name("Valhalla Server")
      //.build(service)

  }

}
