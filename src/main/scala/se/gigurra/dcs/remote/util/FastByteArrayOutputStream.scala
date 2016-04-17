package se.gigurra.dcs.remote.util

import java.io.{IOException, OutputStream, UnsupportedEncodingException}
import java.util

import com.twitter.io.Buf

/**
  * Created by kjolh on 4/17/2016.
  */
case class FastByteArrayOutputStream(initSize: Int = 32) extends OutputStream {
  protected var buf: Array[Byte] = new Array[Byte](initSize)
  protected var count: Int = 0

  private def ensureCapacity(minCapacity: Int) {
    if (minCapacity - buf.length > 0) grow(minCapacity)
  }

  private val MAX_ARRAY_SIZE: Int = Integer.MAX_VALUE - 8

  private def grow(minCapacity: Int) {
    val oldCapacity: Int = buf.length
    var newCapacity: Int = oldCapacity << 1
    if (newCapacity - minCapacity < 0) newCapacity = minCapacity
    if (newCapacity - MAX_ARRAY_SIZE > 0) newCapacity = hugeCapacity(minCapacity)
    buf = util.Arrays.copyOf(buf, newCapacity)
  }

  private def hugeCapacity(minCapacity: Int): Int = {
    if (minCapacity < 0) throw new OutOfMemoryError
    if (minCapacity > MAX_ARRAY_SIZE) Integer.MAX_VALUE
    else MAX_ARRAY_SIZE
  }

  def underlying: Array[Byte] = {
    buf
  }

  def write(input: Buf): Unit = {
    ensureCapacity(input.length + size + 10)
    input.write(buf, size)
    count += input.length
  }

  def write(b: Int) {
    ensureCapacity(count + 1)
    buf(count) = b.toByte
    count += 1
  }

  override def write(b: Array[Byte], off: Int, len: Int) {
    if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) - b.length > 0)) {
      throw new IndexOutOfBoundsException
    }
    ensureCapacity(count + len)
    System.arraycopy(b, off, buf, count, len)
    count += len
  }

  @throws[IOException]
  def writeTo(out: OutputStream) {
    out.write(buf, 0, count)
  }

  def reset(): Unit = {
    count = 0
  }

  def toByteArray: Array[Byte] = {
    util.Arrays.copyOf(buf, count)
  }

  def size: Int = {
    count
  }

  override def toString: String = {
    new String(buf, 0, count)
  }

  @throws[UnsupportedEncodingException]
  def toString(charsetName: String): String = {
    new String(buf, 0, count, charsetName)
  }

  @deprecated def toString(hibyte: Int): String = {
    new String(buf, hibyte, 0, count)
  }

  @throws[IOException]
  override def close {
  }
}
