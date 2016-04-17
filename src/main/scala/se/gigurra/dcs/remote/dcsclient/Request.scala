package se.gigurra.dcs.remote.dcsclient

import java.util.UUID

import com.twitter.io.Buf
import com.twitter.util.{Duration, Future, Promise}

case class Request(script: String,
                   timeOut: Duration = Duration.fromSeconds(2),
                   promise: Promise[Buf] = Promise[Buf],
                   id: String = UUID.randomUUID().toString) {
  def reply: Future[Buf] = promise
}
