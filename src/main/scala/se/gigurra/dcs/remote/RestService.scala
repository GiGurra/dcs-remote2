package se.gigurra.dcs.remote

import com.twitter.finagle.{http, Service}
import com.twitter.finagle.http.path.{Path, Root, _}
import com.twitter.finagle.http._
import com.twitter.util.Future
import org.json4s.jackson.JsonMethods
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.service.{Responses, ServiceErrors, ServiceExceptionFilter}

import scala.util.{Failure, Success, Try}

case class RestService(config: Configuration,
                       cache: ResourceCache,
                       clients: Map[String, DcsClient])
  extends Service[Request, Response]
    with ServiceErrors {

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
    Future(JSON.writeMap(StaticData.readFromFile().source).toResponse)
  }

  private def handleGetStaticData(request: Request, resource: String): Future[Response] = {
    StaticData.readFromFile().source.get(resource) match {
      case Some(data) => Future(JSON.writeMap(data.asInstanceOf[Map[String, Any]]).toResponse)
      case None => Future(Responses.notFound(s"Resource static-data/$resource not found"))
    }
  }

  private def handleGetAllFromCache(request: Request,
                                    env: String): Future[Response] = {

    val maxResourceAgeSeconds = getMaxCacheAge(request, 10.0)
    val itemsRequested = cache.items
        .filter(_.age <= maxResourceAgeSeconds)
        .filter(_.id.split('/').head == env)
        .map(item => s""""${item.id.substring(env.length+1)}":{"age":${item.age},"timestamp":${item.timestamp},"data":${item.data}}""")
    val concatenatedJsonString = itemsRequested.mkString("{", ",", "}")
    Future(concatenatedJsonString.toResponse)
  }

  private def handleGet(request: Request,
                        env: String,
                        resource: String): Future[Response] = {

    val maxResourceAgeSeconds = getMaxCacheAge(request, 0.04)
    val id = idOf(env, resource)
    cache.get(id, maxResourceAgeSeconds) match {
      case Some(data) => Future.value(data.toResponse)
      case None =>
        clientOf(env).get(resource).map { data =>
          cache.put(id, data)
          data.toResponse
        }
    }
  }

  private def handleDelete(request: Request,
                           env: String,
                           resource: String): Future[Response] = {
    val id = idOf(env, resource)
    cache.delete(id)
    clientOf(env).delete(resource).map (_ =>  Responses.ok(s"Resource $env/$resource deleted"))
  }

  private def handlePost(request: Request,
                         env: String): Future[Response] = {
    clientOf(env).post(request.contentString).map (_ => Responses.ok(s"Resource added"))
  }

  private def handlePut(request: Request,
                        env: String,
                        resource: String): Future[Response] = {
    val id = idOf(env, resource)

    Try(JsonMethods.parse(request.contentString)) match {
      case Success(_) =>
        cache.put(id, request.contentString)
        Responses.Ok(s"Resource stored in cache")
      case Failure(e) =>
        Responses.BadRequest(s"Malformated json content string")
    }
  }

  private def idOf(env: String, resource: String): String = {
    s"$env/$resource"
  }

  private def clientOf(env: String): DcsClient = {
    clients.getOrElse(env, throw notFound(s"No dcs client configured to environment named $env. Check your DcsRemote configuration"))
  }

  implicit class RichJsonString(val str: String) {
    def toResponse: Response = {
      val response = http.Response(Version.Http11, Status.Ok)
      response.setContentTypeJson()
      response.setContentString(str)
      response
    }
  }

  val MAX_CACHE_AGE_KEY = "max_cached_age"

}
