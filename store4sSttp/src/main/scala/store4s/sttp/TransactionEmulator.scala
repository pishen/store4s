package store4s.sttp

import store4s.sttp.model.Entity
import store4s.sttp.model.Key
import store4s.sttp.model.LookupRequest
import store4s.sttp.model.LookupResponse
import store4s.sttp.model.RunQueryRequest
import store4s.sttp.model.RunQueryResponse
import sttp.client3.BodySerializer
import sttp.monad.MonadError

case class TransactionEmulator[F[_]](ds: DatastoreEmulator[F])
    extends Transaction[F] {
  val projectId: String = ds.projectId
  implicit val responseMonad: MonadError[F] = ds.responseMonad

  def lookup(keys: Seq[Key])(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse]
  ): F[Seq[Entity]] = {
    // ReadConsistency is not used here
    ds.lookup(keys, ReadConsistency.STRONG)
  }

  def runQuery[S <: Selector](query: Query[S], namespace: String)(implicit
      serializer: BodySerializer[RunQueryRequest],
      deserializer: BodyDeserializer[RunQueryResponse]
  ): F[Query.Result[query.selector.R]] = {
    ds.runQuery[S](query, namespace)
  }
}
