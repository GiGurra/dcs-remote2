package se.gigurra.dcs.remote.dcsclient

import se.gigurra.heisenberg.MapData.SourceData
import se.gigurra.heisenberg.{Schema, Parsed}

case class Reply(source: SourceData = Map.empty)
  extends Parsed[Reply.type] {
  val requestId = parse(schema.requestId)
}

object Reply extends Schema[Reply] {
  val requestId = required[String]("requestId")
}
