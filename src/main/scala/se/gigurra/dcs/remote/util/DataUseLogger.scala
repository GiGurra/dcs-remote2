package se.gigurra.dcs.remote.util

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.concurrent.atomic.AtomicLong
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.Charsets
import com.twitter.util.Duration
import scala.util.Try
import scala.collection.JavaConversions._

case class DataUseLogger(outputFilePath: String, dtFlush: Duration, enabled: Boolean) {

  private val atomicDelta = new AtomicLong(0L)
  private val path = Paths.get(outputFilePath)

  if (enabled) {
    DefaultTimer.twitter.schedule(dtFlush) {
      val delta = atomicDelta.getAndSet(0)
      val prevBytes = Try(Files.readAllLines(path, Charsets.Utf8).head.toLong).getOrElse(0L)
      val newVal = (prevBytes + delta).toString.getBytes(Charsets.Utf8)
      Files.write(path, newVal, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }
  }

  def log(nBytes: Long): Unit = {
    atomicDelta.addAndGet(nBytes)
  }

}
