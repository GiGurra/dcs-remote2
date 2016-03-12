package se.gigurra.dcs.remote.tcpClient

import se.gigurra.heisenberg.MapData.SourceData
import se.gigurra.heisenberg.{Parsed, Schema}

case class Reply(source: SourceData = Map.empty)
  extends Parsed[Reply.type] {
  val requestId = parse(schema.requestId)
}

object Reply extends Schema[Reply] {
  val requestId = required[String]("requestId")
}
