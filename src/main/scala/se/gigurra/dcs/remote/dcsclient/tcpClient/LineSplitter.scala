package se.gigurra.dcs.remote.tcpClient

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.util

import se.gigurra.dcs.remote.dcsclient.LastIndexOfNewline

class LineSplitter {

  private val newlineByte: Byte = 10
  private val charset = Charset.forName("UTF-8")

  object buffer extends ByteArrayOutputStream() {
    def data: Array[Byte] = buf
    val channel = Channels.newChannel(this)
  }

  def apply(newData: ByteBuffer): Seq[String] = {

    buffer.channel.write(newData)

    val i = LastIndexOfNewline.find(buffer.data, buffer.size) // Dont search whole buffer!
    if (i >= 0) {
      val linesBuffer = util.Arrays.copyOfRange(buffer.data, 0, i)
      val remain = util.Arrays.copyOfRange(buffer.data, i+1, buffer.size)
      buffer.reset()
      buffer.write(remain)
      new String(linesBuffer, charset).lines.toSeq
    } else {
      Seq.empty
    }
  }

  def apply(newData: Array[Byte], n: Int): Seq[String] = {
    apply(ByteBuffer.wrap(newData, 0, n))
  }

  def clear(): Unit = {
    buffer.reset()
  }

}