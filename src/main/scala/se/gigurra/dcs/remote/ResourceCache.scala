package se.gigurra.dcs.remote

import java.time.Instant

import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache
import com.twitter.io.Buf

import scala.collection.concurrent
import scala.collection.JavaConversions._

case class ResourceCache(maxItemsPerCategory: Int) {

  private val categories = new concurrent.TrieMap[String, Cache[String, CacheItem]]

  def put(category: String, id: String, _data: Buf): Unit = {
    val data = Buf.ByteArray.coerce(_data)
    categories.getOrElseUpdate(category, newCategory()).put(id, CacheItem(id, data, time))
  }

  def get(category: String, id: String, maxAgeSeconds: Double): Option[CacheItem] = {
    categories.get(category).flatMap(c => Option(c.getIfPresent(id))).filter(_.age <= maxAgeSeconds)
  }

  def delete(category: String, id: String): Unit = {
    categories.get(category).foreach(_.invalidate(id))
  }

  def getCategory(category: String, maxAgeSeconds: Double): Seq[CacheItem] = {
    categories.get(category).map { c =>
      c.asMap().values().filter(_.age <= maxAgeSeconds).toSeq
    }.getOrElse(Nil)
  }

  def categoryNames: Seq[String] = {
    categories.keys.toSeq
  }

  private def time: Double = Instant.now.toEpochMilli.toDouble / 1000.0
  private def newCategory() = CacheBuilder.newBuilder().maximumSize(maxItemsPerCategory).build[String, CacheItem]()
}

case class CacheItem(id: String, data: Buf, timestamp: Double) {
  def age: Double = time - timestamp
  private def time: Double = Instant.now.toEpochMilli.toDouble / 1000.0
}
