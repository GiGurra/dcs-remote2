package se.gigurra.dcs.remote

import com.twitter.finagle.{http, Service}
import com.twitter.finagle.http.path.{Path, Root, _}
import com.twitter.finagle.http._
import com.twitter.util.Future
import se.gigurra.serviceutils.twitter.service.{Responses, ServiceErrors, ServiceExceptionFilter}

case class RestService(config: Configuration,
                       cache: ResourceCache,
                       clients: Map[String, DcsClient])
  extends Service[Request, Response]
    with ServiceErrors {

  override def apply(request: Request) = ServiceExceptionFilter {
    request.method -> Path(request.path) match {
      case Method.Get     -> Root / name / luaMethod  => handleGet(request, name, luaMethod)
      case Method.Delete  -> Root / name / luaMethod  => handleDelete(request, name, luaMethod)
      case Method.Post    -> Root / name              => handlePost(request, name)
      case _                                          => NotFound("No such route")
    }
  }

  private def handleGet(request: Request,
                        name: String,
                        luaMethod: String): Future[Response] = {

    val maxResourceAgeSeconds = request.params.get(MAX_CACHE_AGE_KEY).map(_.toDouble / 1000.0).getOrElse(0.04)
    val id = idOf(name, luaMethod)
    cache.get(id, maxResourceAgeSeconds) match {
      case Some(resource) => Future(Responses.ok(resource))
      case None =>
        clientOf(name).get(luaMethod, request.params.toSeq.filterNot(_._1 == MAX_CACHE_AGE_KEY)).map { data =>
          cache.put(id, data)
          data.toResponse
        }
    }
  }

  private def handleDelete(request: Request,
                           name: String,
                           luaMethod: String): Future[Response] = {
    val id = idOf(name, luaMethod)
    cache.delete(id)
    clientOf(name).delete(luaMethod).map (_ =>  Responses.ok(s"Resource $name/$luaMethod deleted"))
  }

  private def handlePost(request: Request,
                         name: String): Future[Response] = {
    clientOf(name).post(request.contentString).map (_ => Responses.ok(s"Resource added"))
  }

  private def idOf(name: String, luaMethod: String): String = {
    s"$name/$luaMethod"
  }

  private def clientOf(name: String): DcsClient = {
    clients.getOrElse(name, throw notFound(s"No dcs client configured to environment named $name. Check your DcsRemote configuration"))
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
