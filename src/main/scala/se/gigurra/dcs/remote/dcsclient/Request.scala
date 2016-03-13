package se.gigurra.dcs.remote.dcsclient

import java.util.UUID

import com.twitter.util.{Future, Promise, Duration}

case class Request(script: String,
                   timeOut: Duration = Duration.fromSeconds(2),
                   promise: Promise[String] = Promise[String],
                   id: String = UUID.randomUUID().toString) {
  def reply: Future[String] = promise
}
