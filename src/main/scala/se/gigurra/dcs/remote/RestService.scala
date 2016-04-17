package se.gigurra.dcs.remote

import java.io.File
import java.nio.file.StandardWatchEventKinds

import com.twitter.finagle.http._
import com.twitter.finagle.http.path.{Path, Root, _}
import com.twitter.finagle.{Service, http}
import com.twitter.io.{Buf, Charsets}
import com.twitter.util.{Future, NonFatal, Time}
import se.gigurra.dcs.remote.dcsclient.DcsClient
import se.gigurra.heisenberg.MapData.SourceData
import se.gigurra.serviceutils.filemon.FileMonitor
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.service.{Responses, ServiceErrorsWithoutAutoLogging, ServiceExceptionFilter}

import scala.util.Try
import RestService._
import com.fasterxml.jackson.databind.ObjectMapper
import se.gigurra.dcs.remote.util.FastByteArrayOutputStream

case class RestService(config: Configuration,
                       cache: ResourceCache,
                       clients: Map[String, DcsClient])
  extends Service[Request, Response]
    with ServiceErrorsWithoutAutoLogging {

  @volatile var staticData: StaticData = StaticData.readFromFile()
  @volatile var staticDataByteMap: Map[String, Buf] = makeBufMap(staticData.source)
  @volatile var allStaticDataAsBytes: Buf = Buf.ByteArray.Owned(JSON.writeMap(staticData).utf8)
  private val buffers = new ThreadLocal[FastByteArrayOutputStream] {
    override def initialValue(): FastByteArrayOutputStream = {
      FastByteArrayOutputStream()
    }
  }
  private val fileMonitor = makeStaticDataFileMonitor()

  override def apply(request: Request) = ServiceExceptionFilter {
    request.method -> Path(request.path) match {
      case Method.Get     -> Root / "static-data"            => handleGetAllStaticData(request)
      case Method.Get     -> Root / "static-data" / resource => handleGetStaticData(request, resource)
      case Method.Get     -> Root / env                      => handleGetAllFromCache(request, env)
      case Method.Get     -> Root / env / resource           => handleGet(request, env, resource)
      case Method.Delete  -> Root / env / resource           => handleDelete(request, env, resource)
      case Method.Post    -> Root / env                      => handlePost(request, env)
      case Method.Put     -> Root / env / resource           => handlePut(request, env, resource)
      case _                                                 => NotFound("No such route")
    }
  }

  private def getMaxCacheAge(request: Request, default: Double): Double = {
    request.params.get(MAX_CACHE_AGE_KEY).map(_.toDouble / 1000.0).getOrElse(default)
  }

  private def handleGetAllStaticData(request: Request): Future[Response] = {
    Future(allStaticDataAsBytes.toResponse)
  }

  private def handleGetStaticData(request: Request, resource: String): Future[Response] = {
    staticDataByteMap.get(resource) match {
      case Some(buf) => Future(buf.toResponse)
      case None => Future(Responses.notFound(s"Resource static-data/$resource not found"))
    }
  }

  object jsonBytes {

    val BEGIN_OBJECT = "{".utf8
    val END_OBJECT = "}".utf8

    val COMMA = ",".utf8
    val COLON = ":".utf8
    val QUOTE = "\"".utf8

    val AGE = "\"age\"".utf8
    val TIMESTAMP = "\"timestamp\"".utf8
    val DATA = "\"data\"".utf8
  }

  private def handleGetAllFromCache(request: Request, env: String): Future[Response] = {
    import jsonBytes._

    val buffer = buffers.get()
    buffer.reset()

    def writeKeyBytes(name: Array[Byte], prependComma: Boolean, addQuotes: Boolean): Unit = {
      if (prependComma)
        buffer.write(COMMA)
      if (addQuotes)
        buffer.write(QUOTE)
      buffer.write(name)
      if (addQuotes)
        buffer.write(QUOTE)
      buffer.write(COLON)
    }

    def writeKeyString(name: String, prependComma: Boolean, addQuotes: Boolean): Unit = {
      writeKeyBytes(name.utf8, prependComma, addQuotes)
    }

    def writeNumericValue(v: Number): Unit = {
      buffer.write(v.toString.utf8)
    }

    val itemsRequested = cache.getCategory(env, getMaxCacheAge(request, 10.0))
    var iItem = 0
    buffer.write(BEGIN_OBJECT)
    itemsRequested.foreach { item =>
      writeKeyString(item.id, prependComma = iItem > 0, addQuotes = true)
      buffer.write(BEGIN_OBJECT)
      writeKeyBytes(AGE, prependComma = false, addQuotes = false)
      writeNumericValue(item.age)
      writeKeyBytes(TIMESTAMP, prependComma = true, addQuotes = false)
      writeNumericValue(item.timestamp)
      writeKeyBytes(DATA, prependComma = true, addQuotes = false)
      buffer.write(item.data)
      buffer.write(END_OBJECT)
      iItem += 1
    }
    buffer.write(END_OBJECT)

    Future(Buf.ByteArray.Owned(buffer.toByteArray).toResponse)
  }

  private def handleGet(request: Request,
                        env: String,
                        resource: String): Future[Response] = {

    cache.get(env, resource, getMaxCacheAge(request, 0.04)) match {
      case Some(data) => Future.value(data.data.toResponse)
      case None =>
        clientOf(env).get(resource).map { data =>
          cache.put(env, resource, data)
          data.toResponse
        }
    }
  }

  private def handleDelete(request: Request,
                           env: String,
                           resource: String): Future[Response] = {
    (request.getBooleanParam("cache_only") match {
      case true => Future.Unit
      case false => clientOf(env).delete(resource)
    }).map { _ =>
      cache.delete(env, resource)
      Responses.ok(s"Resource $env/$resource deleted")
    }
  }

  private def handlePost(request: Request,
                         env: String): Future[Response] = {
    clientOf(env).post(request.contentString).map (_ => Responses.ok(s"Resource added"))
  }

  private def handlePut(request: Request,
                        env: String,
                        resource: String): Future[Response] = {
    if (validJson(request.content)) {
      cache.put(env, resource, request.content)
      Responses.Ok(s"Resource stored in cache")
    } else {
      Responses.BadRequest(s"Malformated json content string")
    }
  }

  private def clientOf(env: String): DcsClient = {
    clients.getOrElse(env, throw notFound(s"No dcs client configured to environment named $env. Check your DcsRemote configuration"))
  }

  implicit class RichJsonString(val data: Buf) {
    def toResponse: Response = {
      val response = http.Response(Version.Http11, Status.Ok)
      response.setContentTypeJson()
      response.content = data
      response.contentLength = response.content.length
      response
    }
  }

  private def makeBufMap(source: SourceData): Map[String, Buf] = {
    source.mapValues(v => Buf.ByteArray.Owned(JSON.writeMap(v.asInstanceOf[Map[String, Any]]).utf8))
  }

  private def makeStaticDataFileMonitor(): FileMonitor = {
    FileMonitor.apply(new File("static-data.json").toPath) { (_, event) =>
      event.kind() match {
        case StandardWatchEventKinds.ENTRY_CREATE | StandardWatchEventKinds.ENTRY_MODIFY => Try {
          logger.info(s"Updating from changes in static-data.json ..")
          staticData = StaticData.readFromFile()
          allStaticDataAsBytes = Buf.ByteArray.Owned.apply(JSON.writeMap(staticData).utf8)
          staticDataByteMap = makeBufMap(staticData.source)
          logger.info(s"OK! (Updated successfully from changes in static-data.json)")
        }.recover {
          case NonFatal(e) => logger.error(e, s"Failed updating data from static-data.json .. $e")
        }
        case _ =>
      }
    }
  }

  override def close(deadline: Time): Future[Unit] = {
    fileMonitor.kill()
    super.close(deadline)
  }

  val MAX_CACHE_AGE_KEY = "max_cached_age"

  val mapper = new ObjectMapper

  def validJson(buf: Buf): Boolean = {
    try {
      val parser = mapper.getFactory.createParser(Buf.ByteArray.Owned.extract(buf))
      while (parser.nextToken() != null) {}
      true
    } catch {
      case NonFatal(e) =>
        false
    }
  }

}

object RestService {
  implicit class StringAsUtf(val str: String) extends AnyVal {
    def utf8: Array[Byte] = {
      str.getBytes(Charsets.Utf8)
    }
  }
}