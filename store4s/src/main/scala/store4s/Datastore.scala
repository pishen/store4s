package store4s

import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.EntityQuery
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.ReadOption
import com.google.cloud.datastore.{Datastore => GDatastore}

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe._

case class Datastore(underlying: GDatastore) {
  def keyFactory[A: WeakTypeTag] = underlying
    .newKeyFactory()
    .setKind(weakTypeOf[A].typeSymbol.name.toString())

  def add(entity: FullEntity[_]) = underlying.add(entity)
  def add(entity: Seq[FullEntity[_]]) = underlying.add(entity: _*)
  def put(entity: FullEntity[_]) = underlying.put(entity)
  def put(entity: Seq[FullEntity[_]]) = underlying.put(entity: _*)
  def get(key: Key) = Option(
    underlying.get(key, Seq.empty[ReadOption]: _*)
  )
  def get(key: Seq[Key]) = underlying.get(key.asJava, Seq.empty[ReadOption]: _*)
  def getEither[A: WeakTypeTag: EntityDecoder](name: String) = {
    get(keyFactory[A].newKey(name)).map(decodeEntity[A])
  }
  def getEither[A: WeakTypeTag: EntityDecoder](id: Long) = {
    get(keyFactory[A].newKey(id)).map(decodeEntity[A])
  }
  def getEithers[A: WeakTypeTag: EntityDecoder](nameList: Seq[String]) = {
    get(nameList.map(name => keyFactory[A].newKey(name))).asScala
      .map(decodeEntity[A])
  }
  def getRight[A: WeakTypeTag: EntityDecoder](name: String) = {
    getEither[A](name).map(_.toTry.get)
  }
  def getRight[A: WeakTypeTag: EntityDecoder](id: Long) = {
    getEither[A](id).map(_.toTry.get)
  }
  def getRights[A: WeakTypeTag: EntityDecoder](name: Seq[String]) = {
    getEithers[A](name).map(_.toTry.get)
  }
  def delete(key: Key) = underlying.delete(key)
  def update(entity: Entity) = underlying.update(entity)
  def run(query: EntityQuery) = underlying.run(query, Seq.empty[ReadOption]: _*)
}

object Datastore {
  def defaultInstance = {
    Datastore(DatastoreOptions.getDefaultInstance().getService())
  }

  def apply(options: DatastoreOptions): Datastore = {
    Datastore(options.getService())
  }
}
