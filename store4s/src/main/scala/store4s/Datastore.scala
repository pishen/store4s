package store4s

import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.DatastoreReader
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.EntityQuery
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.ReadOption
import com.google.cloud.datastore.{Datastore => GDatastore}

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe._

case class Datastore(
    underlying: GDatastore,
    typeIdentifier: String = "_type",
    naming: Naming = CamelCase
) {
  def keyFactory[A: WeakTypeTag] = underlying
    .newKeyFactory()
    .setKind(naming.convert(weakTypeOf[A].typeSymbol.name.toString()))
  def allocateId[A: WeakTypeTag] =
    underlying.allocateId(keyFactory[A].newKey()).getId()

  def add(entity: FullEntity[_]) = underlying.add(entity)
  def add(entities: Seq[FullEntity[_]]) = underlying.add(entities: _*)
  def put(entity: FullEntity[_]) = underlying.put(entity)
  def put(entities: Seq[FullEntity[_]]) = underlying.put(entities: _*)
  def get(key: Key) = Option(
    underlying.get(key, Seq.empty[ReadOption]: _*)
  )
  def get(keys: Seq[Key]) =
    underlying.get(keys.asJava, Seq.empty[ReadOption]: _*).asScala
  def getEither[A: WeakTypeTag: EntityDecoder](name: String) = {
    get(keyFactory[A].newKey(name)).map(decodeEntity[A])
  }
  def getEither[A: WeakTypeTag: EntityDecoder](id: Long) = {
    get(keyFactory[A].newKey(id)).map(decodeEntity[A])
  }
  def getEithersByName[A: WeakTypeTag: EntityDecoder](names: Seq[String]) = {
    get(names.map(name => keyFactory[A].newKey(name))).map(decodeEntity[A])
  }
  def getEithersById[A: WeakTypeTag: EntityDecoder](ids: Seq[Long]) = {
    get(ids.map(id => keyFactory[A].newKey(id))).map(decodeEntity[A])
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
  def run(query: EntityQuery) = (underlying: DatastoreReader).run(query)
}

object Datastore {
  def defaultInstance = {
    Datastore(DatastoreOptions.getDefaultInstance().getService())
  }

  def apply(options: DatastoreOptions): Datastore = {
    Datastore(options.getService())
  }
}
