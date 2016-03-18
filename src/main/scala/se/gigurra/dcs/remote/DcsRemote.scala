package se.gigurra.dcs.remote

import java.io.File
import java.net.InetSocketAddress

import com.sun.jna.Platform
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.{Method, Request, Http}
import com.twitter.util.Await
import se.gigurra.dcs.remote.dcsclient.DcsClient
import se.gigurra.dcs.remote.keyboard.{LLWindowsKeyboardEvent, KeyInput}
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.{Capture, Logging}
import se.gigurra.serviceutils.twitter.service.ExceptionFilter

import scala.util.{Failure, Success, Try}

object DcsRemote extends Logging {

  def main(args: Array[String]): Unit = Try {

    Capture.stdOutToFile(s"dcs-remote-debug-log.txt", append = true)
    Capture.stdErrToFile(s"dcs-remote-log.txt", append = true)

    if (!new File("static-data.json").canRead)
      throw new RuntimeException(s"Could not open configuration file file 'static-data.json'")

    val config = Configuration.readFromFile()
    logger.info(s"Config:\n ${JSON.write(config)}")

    if (config.show_tray_icon)
      TrayIcon.setup()

    val cache = new ResourceCache(config.cache_size_mb)
    val clients = DcsClient.createClients(config, config.connect_to_dcs)
    val restService = new RestService(config, cache, clients)
    val service = ExceptionFilter[Exception]() andThen restService
    val server = ServerBuilder()
      // .tls(certificatePath = "", keyPath = "")
      .codec(Http())
      .bindTo(new InetSocketAddress(config.rest_port))
      .name("DCS Remote")
      .build(service)

    if (Platform.isWindows) {
      val kbClient = KeyboardHandler(restService)
      KeyInput.enterKeyboardHookMessageLoop(kbClient.apply)
    }

    Await.ready(server)

  } match {
    case Success(_) =>
    case Failure(e) =>
      logger.error(e, s"DCS Remote failed: $e")
      System.exit(1)
  }


  case class KeyboardHandler(restService: RestService) {
    def apply(event: LLWindowsKeyboardEvent): Unit = {
      val url = s"keyboard/${event.vkCode}"
      if (!event.isRepeat) {
        if (event.isKeyDown) {
          val request = Request(Method.Put, url)
          request.contentString = "{}"
          restService.apply(request)
        } else {
          val request = Request(Method.Delete, url + "?cache_only=true")
          restService.apply(request)
        }
      }
    }
  }

}
