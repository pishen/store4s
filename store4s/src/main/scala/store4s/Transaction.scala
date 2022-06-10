package store4s

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.EntityQuery
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.{Transaction => GTransaction}

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe._

case class Transaction(underlying: GTransaction, ds: Datastore) {
  def add(entity: FullEntity[_]) = underlying.add(entity)
  def add(entities: Seq[FullEntity[_]]) = underlying.add(entities: _*)
  def put(entity: FullEntity[_]) = underlying.put(entity)
  def put(entities: Seq[FullEntity[_]]) = underlying.put(entities: _*)
  def get(key: Key) = Option(underlying.get(key))
  def get(keys: Seq[Key]) = underlying.get(keys: _*).asScala
  def getEither[A: WeakTypeTag: EntityDecoder](name: String) = {
    get(ds.keyFactory[A].newKey(name)).map(decodeEntity[A])
  }
  def getEither[A: WeakTypeTag: EntityDecoder](id: Long) = {
    get(ds.keyFactory[A].newKey(id)).map(decodeEntity[A])
  }
  def getEithersByName[A: WeakTypeTag: EntityDecoder](names: Seq[String]) = {
    get(names.map(name => ds.keyFactory[A].newKey(name))).map(decodeEntity[A])
  }
  def getEithersById[A: WeakTypeTag: EntityDecoder](ids: Seq[Long]) = {
    get(ids.map(id => ds.keyFactory[A].newKey(id))).map(decodeEntity[A])
  }
  def getRight[A: WeakTypeTag: EntityDecoder](name: String) = {
    getEither[A](name).map(_.toTry.get)
  }
  def getRight[A: WeakTypeTag: EntityDecoder](id: Long) = {
    getEither[A](id).map(_.toTry.get)
  }
  def getRightsByName[A: WeakTypeTag: EntityDecoder](names: Seq[String]) = {
    getEithersByName[A](names).map(_.toTry.get)
  }
  def getRightsById[A: WeakTypeTag: EntityDecoder](ids: Seq[Long]) = {
    getEithersById[A](ids).map(_.toTry.get)
  }
  def delete(keys: Key*) = underlying.delete(keys: _*)
  def update(entities: Entity*) = underlying.update(entities: _*)
  def run(query: EntityQuery) = underlying.run(query)
}
