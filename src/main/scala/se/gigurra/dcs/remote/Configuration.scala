package se.gigurra.dcs.remote

import java.io.FileNotFoundException

import se.gigurra.heisenberg.MapData.SourceData
import se.gigurra.heisenberg.{Schema, Parsed}
import se.gigurra.serviceutils.json.JSON
import se.gigurra.serviceutils.twitter.logging.Logging

import scala.util.{Failure, Success, Try}

case class StaticData(source: SourceData = Map.empty)
  extends Parsed[StaticData.type] {
}

object StaticData extends Schema[StaticData] {
  def readFromFile(s: String = "static-data.json"): StaticData = {
    JSON.read[StaticData](scala.io.Source.fromFile(s).mkString)
  }
}

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
  val rest_port      = parse(schema.rest_port)
  val connect_to_dcs = parse(schema.connect_to_dcs)
  val show_tray_icon = parse(schema.show_tray_icon)
  val keybHook       = parse(schema.keybHook)
  val mappings       = parse(schema.mappings)
}

object Configuration extends Schema[Configuration] with Logging {
  val rest_port      = required[Int]("rest_port", default = 12340)
  val connect_to_dcs = required[Boolean]("connect_to_dcs", default = true)
  val show_tray_icon = required[Boolean]("show_tray_icon", default = true)
  val keybHook       = required[Boolean]("use_windows_keyboard_hook", default = true)
  val mappings       = required[Seq[LuaEnvironmentMap]]("mappings", default = Seq(LuaEnvironmentMap()))

  def readFromFile(s: String = "dcs-remote-cfg.json"): Configuration = {
    logger.info(s"Loading configuration file: $s")
    Try(JSON.read[Configuration](scala.io.Source.fromFile(s).mkString)) match {
      case Success(cfg) => cfg
      case Failure(e: FileNotFoundException) =>
        logger.info(s"No config file found (path=$s) - using default configuration")
        Configuration()
      case Failure(e) =>
        logger.fatal(e, s"Failed to read config file ($e) '$s'")
        throw e
    }
  }
}
