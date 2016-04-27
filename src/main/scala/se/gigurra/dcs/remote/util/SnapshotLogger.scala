package se.gigurra.dcs.remote.util

import java.nio.file.{Files, Paths, StandardOpenOption}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.Charsets
import com.twitter.util.Duration

case class SnapshotLogger(outputFilePath: String,
                          dtFlush: Duration,
                          enabled: Boolean,
                          fGetSnapshot: () => String) {

  private val path = Paths.get(outputFilePath)

  if (enabled) {
    DefaultTimer.twitter.schedule(dtFlush) {
      Files.write(path, fGetSnapshot().getBytes(Charsets.Utf8), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }
  }

}
