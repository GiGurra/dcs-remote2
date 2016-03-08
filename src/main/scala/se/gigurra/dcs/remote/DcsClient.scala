package se.gigurra.dcs.remote

import com.twitter.util.Future

case class DcsClient(name: String, port: Int) {
  def get(luaMethod: String, methodParameters: Map[String, String]): Future[String] = ???
  def delete(luaMethod: String): Future[Unit] = ???
  def post(script: String): Future[Unit] = ???
}

object DcsClient {
  def createClients(config: Configuration): Map[String, DcsClient] = {
    config.mappings.map { env =>
      env.name -> DcsClient(env.name, env.port)
    }.toMap
  }
}