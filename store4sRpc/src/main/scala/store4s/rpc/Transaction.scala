package store4s.rpc

import com.google.datastore.v1.datastore.Mutation
import com.google.datastore.v1.datastore.Mutation.Operation.Delete
import com.google.datastore.v1.datastore.Mutation.Operation.Insert
import com.google.datastore.v1.datastore.Mutation.Operation.Update
import com.google.datastore.v1.datastore.Mutation.Operation.Upsert
import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.entity.Key.PathElement.IdType
import com.google.protobuf.ByteString

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class Transaction(id: ByteString, ds: Datastore) {
  var ops = Seq.empty[Mutation.Operation]

  def insert(entities: Entity*) =
    Future.successful(ops ++= entities.map(Insert))
  def upsert(entities: Entity*) =
    Future.successful(ops ++= entities.map(Upsert))
  def update(entities: Entity*) =
    Future.successful(ops ++= entities.map(Update))
  def deleteById[T: Encoder](ids: Long*) = Future.successful {
    ops ++= ids.map(id => Delete(ds.buildKey[T](IdType.Id(id))))
  }
  def deleteByName[T: Encoder](names: String*) = Future.successful {
    ops ++= names.map(name => Delete(ds.buildKey[T](IdType.Name(name))))
  }

  def lookupById[T: Decoder: Encoder](ids: Long*)(implicit
      ec: ExecutionContext
  ) = ds.lookup[T](ids.map(id => ds.buildKey[T](IdType.Id(id))), id)

  def lookupByName[T: Decoder: Encoder](names: String*)(implicit
      ec: ExecutionContext
  ) = ds.lookup[T](names.map(name => ds.buildKey[T](IdType.Name(name))), id)

  def runQuery[S <: Selector](
      query: Query[S]
  )(implicit enc: Encoder[query.selector.R], ec: ExecutionContext) = {
    ds.runQuery(query, id)
  }
}
