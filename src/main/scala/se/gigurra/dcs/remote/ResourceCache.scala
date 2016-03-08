package se.gigurra.dcs.remote

import scala.collection.mutable

case class ResourceCache(_maxSizeMb: Int) {
  val maxSize: Long = _maxSizeMb.toLong * 1024L * 1024L

  def put(id: String, data: String): Unit = synchronized {

    val newDataSize = approxSizeOf(data)

    while(nonEmpty && (newDataSize + size > maxSize))
      deleteOldest()

    val newItem = Item(id, data, time)
    store += id -> newItem
    ageQue += newItem
  }

  def get(id: String, maxAgeSeconds: Double): Option[String] = synchronized {
    store.get(id).filter(_.age < maxAgeSeconds).map(_.data)
  }

  def delete(id: String): Unit = synchronized {
    store.remove(id)
    ageQue.dequeueAll(_.id == id)
  }

  def size: Long = synchronized(_size)
  def approxSizeOf(data: String): Long = synchronized (data.size.toLong * 120L / 100L)
  def isEmpty: Boolean = synchronized(store.isEmpty)
  def nonEmpty: Boolean = synchronized (!isEmpty)
  def time: Double = System.nanoTime / 1e9

  private def deleteOldest(): Unit = synchronized {
    val item = ageQue.dequeue()
    store.remove(item.id)
  }

  private case class Item(id: String, data: String, timeStamp: Double) {
    def age: Double = time - timeStamp
  }

  private val store = new mutable.HashMap[String, Item]()
  private val ageQue = new mutable.Queue[Item]()
  private var _size = 0L
}
