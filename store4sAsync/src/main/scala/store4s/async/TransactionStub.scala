package store4s.async

import store4s.async.model.Entity
import store4s.async.model.Key
import store4s.async.model.LookupRequest
import store4s.async.model.LookupResponse
import store4s.async.model.RunQueryRequest
import store4s.async.model.RunQueryResponse
import sttp.client3.BodySerializer
import sttp.monad.MonadError

case class TransactionStub[F[_]](ds: DatastoreStub[F]) extends Transaction[F] {
  val projectId: String = ds.projectId
  implicit val responseMonad: MonadError[F] = ds.responseMonad

  def lookup(keys: Seq[Key])(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse]
  ): F[Seq[Entity]] = {
    // ReadConsistency is not used here
    ds.lookup(keys, ReadConsistency.STRONG)
  }

  def runQuery[S <: Selector](query: Query[S], namespace: String)(implicit
      serializer: BodySerializer[RunQueryRequest],
      respAs: RespAs[RunQueryResponse]
  ): F[Query.Result[query.selector.R]] = {
    ds.runQuery[S](query, namespace)
  }
}
