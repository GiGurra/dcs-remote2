package se.gigurra.dcs.remote.dcsclient

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util

import com.twitter.io.Charsets

import scala.collection.mutable.ArrayBuffer

class LineSplitter {

  object buffer extends ByteArrayOutputStream() {
    def data: Array[Byte] = buf
    def setSize(n: Int): Unit = count = n
    val channel = Channels.newChannel(this)
  }

  def apply(newData: ByteBuffer): Seq[Array[Byte]] = {

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
    val nLeft = buffer.size() - offs
    System.arraycopy(buffer.data, offs, buffer.data, 0, nLeft)
    buffer.setSize(nLeft)

    // Clean up
    out map { line =>
      if (line.contains('\n'))
        line filter (_ != '\n')
      else
        line
    } filter (_.nonEmpty)
  }

  def apply(newData: Array[Byte], n: Int): Seq[Array[Byte]] = {
    apply(ByteBuffer.wrap(newData, 0, n))
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