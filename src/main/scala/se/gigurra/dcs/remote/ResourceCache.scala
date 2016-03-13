package se.gigurra.dcs.remote

import scala.collection.mutable

case class ResourceCache(_maxSizeMb: Int) {
  val maxBytes: Long = _maxSizeMb.toLong * 1024L * 1024L

  def put(id: String, data: String): Unit = synchronized {

    val item = CacheItem(id, data, time)

    while(nonEmpty && (item.byteSize + byteSize > maxBytes))
      deleteOldest()

    add(item)
  }

  def get(id: String, maxAgeSeconds: Double): Option[String] = synchronized {
    store.get(id).filter(_.age < maxAgeSeconds).map(_.data)
  }

  def delete(id: String): Unit = synchronized {
    store.remove(id).foreach(oldItem => _byteSize -= oldItem.byteSize)
  }

  def items: Array[CacheItem] = synchronized {
    val out = new Array[CacheItem](store.size)
    store.values.copyToArray(out)
    out
  }

  def byteSize: Long =  synchronized {
    _byteSize
  }

  def deleteOldest(): Unit = synchronized {
    if (store.nonEmpty)
      delete(store.head._1)
  }

  private def add(item: CacheItem): Unit = {
    delete(item.id) // Must be done explicitly to preserve insertion order
    store += item.id -> item
    _byteSize += item.byteSize
  }

  private var _byteSize: Long = 0L
  private def isEmpty: Boolean = store.isEmpty
  private def nonEmpty: Boolean = !isEmpty
  private def time: Double = System.nanoTime / 1e9

  private val store = new mutable.LinkedHashMap[String, CacheItem]()
}

case class CacheItem(id: String, data: String, timeStamp: Double) {
  def age: Double = time - timeStamp
  val byteSize: Long = data.length * 2
  private def time: Double = System.nanoTime / 1e9
}
