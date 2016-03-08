package se.gigurra.dcs.remote

import com.twitter.finagle.Service
import com.twitter.finagle.http.path.{Path, Root, _}
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.util.Future
import se.gigurra.serviceutils.twitter.service.{ServiceErrors, ServiceExceptionFilter}

case class RestService(config: Configuration)
  extends Service[Request, Response]
    with ServiceErrors {

  override def apply(request: Request) = ServiceExceptionFilter {
    request.method -> Path(request.path) match {
      case Method.Get   -> Root / name / luaMethod  => handleGet(request, name, luaMethod)
      case Method.Post  -> Root / name              => handlePost(request, name)
      case _                                        => NotFound("No such route")
    }
  }

  private def handleGet(request: Request,
                        name: String,
                        luaMethod: String): Future[Response] = {
    ???
  }

  private def handlePost(request: Request,
                         name: String): Future[Response] = {
    ???
  }

}
