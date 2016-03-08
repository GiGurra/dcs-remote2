package se.gigurra.dcs.remote

import se.gigurra.heisenberg.MapData.SourceData
import se.gigurra.heisenberg.{Schema, Parsed}

case class LuaEnvironmentMap(source: SourceData = Map.empty)
  extends Parsed[LuaEnvironmentMap.type] {
  val name  = parse(schema.name)
  val port  = parse(schema.port)
}

object LuaEnvironmentMap extends Schema[LuaEnvironmentMap] {
  val name  = required[String]("name", default = "export")
  val port  = required[Int]("port", default = 13465)
}

case class Configuration(source: SourceData = Map.empty)
  extends Parsed[Configuration.type] {
  val rest_port     = parse(schema.rest_port)
  val cache_size_mb = parse(schema.cache_size_mb)
  val mappings      = parse(schema.mappings)
}

object Configuration extends Schema[Configuration] {
  val rest_port     = required[Int]("rest_port", default = 12340)
  val cache_size_mb = required[Int]("cache_size_mb", default = 50)
  val mappings      = required[Seq[LuaEnvironmentMap]]("mappings", default = Seq(LuaEnvironmentMap()))

  def readFromFile(): Configuration = {
    // TODO: implement file read
    Configuration()
  }
}
