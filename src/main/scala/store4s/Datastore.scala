package store4s

import com.google.cloud.datastore.{Datastore => GDatastore}
import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.ReadOption

case class Datastore(underlying: GDatastore) {
  def keyFactory = underlying.newKeyFactory()
  def add(entity: FullEntity[_]) = underlying.add(entity)
  def put(entity: FullEntity[_]) = underlying.put(entity)
  def get(key: Key) = Option(
    underlying.get(key, Seq.empty[ReadOption]: _*)
  )
  def delete(key: Key) = underlying.delete(key)
  def update(entity: Entity) = underlying.update(entity)
}

object Datastore {
  def defaultInstance = {
    Datastore(DatastoreOptions.getDefaultInstance().getService())
  }

  def apply(options: DatastoreOptions): Datastore = {
    Datastore(options.getService())
  }
}
