package se.gigurra.dcs.remote.dcsclient

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util

import com.twitter.io.Charsets
import se.gigurra.dcs.remote.util.FastByteArrayOutputStream
import se.gigurra.serviceutils.twitter.logging.Logging

import scala.collection.mutable.ArrayBuffer

case class LineSplitter(nMaxBytes: Int = 1 * 1024 * 1024) extends Logging {

  object buffer extends FastByteArrayOutputStream() {
    def data: Array[Byte] = buf
    def setSize(n: Int): Unit = count = n
    val channel = Channels.newChannel(this)
  }

  def apply(newDatas: Iterable[ByteBuffer]): Seq[Array[Byte]] = {

    for (newData <- newDatas)
      buffer.channel.write(newData)

    // Flush out new lines
    val out = new ArrayBuffer[Array[Byte]]

    var i = 0
    var offs = 0
    while (i < buffer.size) {
      val c = buffer.data(i)
      if (c == '\n') {
        out += util.Arrays.copyOfRange(buffer.data, offs, i)
        i += 1
        offs = i
      }

      i += 1
    }

    // Shove back data
    val nLeft = buffer.size - offs
    System.arraycopy(buffer.data, offs, buffer.data, 0, nLeft)
    buffer.setSize(nLeft)

    if (buffer.size > nMaxBytes) {
      logger.error(s"LineSplitter overflow - Force clearing. No splitting \\n from DCS?")
      buffer.reset()
    }

    // Clean up
    out map { line =>
      if (line.contains('\n'))
        line filter (_ != '\n')
      else
        line
    } filter (_.nonEmpty)
  }

  def clear(): Unit = {
    buffer.reset()
  }

}

/*
object LineSplitterTest {
  def main(args: Array[String]): Unit = {
    val data = ByteBuffer.wrap("\n\n\nabc\nadsegh\n\n123\n".getBytes(Charsets.Utf8))
    val result = new LineSplitter().apply(data)
    println(result)
  }
}*/