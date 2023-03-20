package store4s.async

import store4s.async.model.{Query => _, _}
import sttp.client3._
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.reflect.runtime.universe._

trait Transaction[F[_]] {
  val projectId: String

  implicit val responseMonad: MonadError[F]

  def insert(entity: Entity) = Mutation(insert = Some(entity))
  def upsert(entity: Entity) = Mutation(upsert = Some(entity))
  def update(entity: Entity) = Mutation(update = Some(entity))
  def delete(key: Key) = Mutation(delete = Some(key))

  def deleteById[A: WeakTypeTag](id: Long, namespace: String = null) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val key = Key(
      PartitionId(projectId, Option(namespace)),
      Seq(PathElement(kind, Some(id.toString), None))
    )
    delete(key)
  }

  def deleteByName[A: WeakTypeTag](name: String, namespace: String = null) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val key = Key(
      PartitionId(projectId, Option(namespace)),
      Seq(PathElement(kind, None, Some(name)))
    )
    delete(key)
  }

  def lookup(keys: Seq[Key])(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse]
  ): F[Seq[Entity]]

  def lookupByIds[A: WeakTypeTag](
      ids: Seq[Long],
      namespace: String = null
  )(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = ids.map(id =>
      Key(
        PartitionId(projectId, Option(namespace)),
        Seq(PathElement(kind, Some(id.toString), None))
      )
    )
    lookup(keys).map(_.map(e => dec.decode(e).toTry.get))
  }

  def lookupById[A: WeakTypeTag](
      id: Long,
      namespace: String = null
  )(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = lookupByIds(Seq(id), namespace).map(_.headOption)

  def lookupByNames[A: WeakTypeTag](
      names: Seq[String],
      namespace: String = null
  )(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = names.map(name =>
      Key(
        PartitionId(projectId, Option(namespace)),
        Seq(PathElement(kind, None, Some(name)))
      )
    )
    lookup(keys).map(_.map(e => dec.decode(e).toTry.get))
  }

  def lookupByName[A: WeakTypeTag](
      name: String,
      namespace: String = null
  )(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = lookupByNames(Seq(name), namespace).map(_.headOption)

  def runQuery[S <: Selector](
      query: Query[S],
      namespace: String = null
  )(implicit
      serializer: BodySerializer[RunQueryRequest],
      respAs: RespAs[RunQueryResponse]
  ): F[Query.Result[query.selector.R]]
}

case class TransactionImpl[F[_], P](
    id: String,
    ds: DatastoreImpl[F, P]
) extends Transaction[F] {
  val projectId: String = ds.projectId

  implicit val responseMonad: MonadError[F] = ds.responseMonad

  def lookup(keys: Seq[Key])(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse]
  ) = {
    ds.authRequest
      .body(LookupRequest(ReadOptions(None, Some(id)), keys))
      .post(ds.buildUri("lookup"))
      .response(respAs.value.getRight)
      .send(ds.backend)
      .map(_.body.found.getOrElse(Seq.empty[EntityResult]).map(_.entity))
  }

  def runQuery[S <: Selector](
      query: Query[S],
      namespace: String = null
  )(implicit
      serializer: BodySerializer[RunQueryRequest],
      respAs: RespAs[RunQueryResponse]
  ) = {
    val body = RunQueryRequest(
      PartitionId(ds.projectId, Option(namespace)),
      ReadOptions(None, Some(id)),
      query.query
    )
    ds.authRequest
      .body(body)
      .post(ds.buildUri("runQuery"))
      .response(respAs.value.getRight)
      .send(ds.backend)
      .map(resp => Query.Result[query.selector.R](resp.body.batch))
  }
}
