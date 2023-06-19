package store4s.sttp

import store4s.sttp.model.{Query => _, _}
import sttp.client3.BodySerializer
import sttp.client3.Identity
import sttp.client3.monad.IdMonad
import sttp.monad.FutureMonad
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.math.Ordering.Implicits._
import scala.reflect.runtime.universe._
import scala.util.Random

case class DatastoreEmulator[F[_]](projectId: String, me: MonadError[F])
    extends Datastore[F] {
  implicit val responseMonad: MonadError[F] = me

  var db = Map.empty[Key, Entity]

  def allocateIds[A: WeakTypeTag](numOfIds: Int, namespace: String = null)(
      implicit
      serializer: BodySerializer[AllocateIdBody],
      deserializer: BodyDeserializer[AllocateIdBody]
  ): F[Seq[Long]] = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    def key(id: Long) = Key(
      PartitionId(projectId, Option(namespace)),
      Seq(PathElement(kind, Some(id.toString()), None))
    )
    val ids = Iterator
      .continually(Random.nextLong())
      .filterNot(id => db.contains(key(id)))
      .take(numOfIds)
      .toSeq
    db = db ++ ids.map(id => key(id) -> null)
    responseMonad.unit(ids)
  }

  def commit(mutations: Seq[Mutation], txOpt: Option[String])(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ): F[Seq[Option[Key]]] = {
    // TODO: txOpt is not handled yet
    mutations.foreach {
      case Mutation(Some(e), None, None, None) =>
        // insert
        if (db.contains(e.key.get) && db(e.key.get) != null) {
          sys.error("Key already exists: " + e.key.get)
        } else {
          db = db + (e.key.get -> e)
        }
      case Mutation(None, Some(e), None, None) =>
        // update
        if (db.contains(e.key.get) && db(e.key.get) != null) {
          db = db + (e.key.get -> e)
        } else {
          sys.error("Key does not exist: " + e.key.get)
        }
      case Mutation(None, None, Some(e), None) =>
        // upsert
        db = db + (e.key.get -> e)
      case Mutation(None, None, None, Some(k)) =>
        // delete
        db = db - k
      case _ => sys.error("Invalid Mutation")
    }
    responseMonad.unit(mutations.map(_ => None))
  }

  def lookup(keys: Seq[Key], readConsistency: ReadConsistency.Value)(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse]
  ): F[Seq[Entity]] = {
    responseMonad.unit(
      keys.flatMap(key => db.get(key)).filterNot(_ == null)
    )
  }

  implicit val valueOrdering: Ordering[Value] = (x, y) => {
    if (x.booleanValue.isDefined) {
      Ordering[Boolean].compare(x.booleanValue.get, y.booleanValue.get)
    } else if (x.integerValue.isDefined) {
      Ordering[Long].compare(
        x.integerValue.get.toLong,
        y.integerValue.get.toLong
      )
    } else if (x.doubleValue.isDefined) {
      Ordering[Double].compare(x.doubleValue.get, y.doubleValue.get)
    } else if (x.timestampValue.isDefined) {
      Ordering[Long].compare(
        ZonedDateTime.parse(x.timestampValue.get).toInstant().toEpochMilli(),
        ZonedDateTime.parse(y.timestampValue.get).toInstant().toEpochMilli()
      )
    } else if (x.stringValue.isDefined) {
      Ordering[String].compare(x.stringValue.get, y.stringValue.get)
    } else {
      sys.error("Order not supported")
    }
  }

  def applyFilter(f: Filter, e: Entity): Boolean = f match {
    case Filter(Some(compositeFilter), None) =>
      compositeFilter.filters.forall(f => applyFilter(f, e))
    case Filter(None, Some(propertyFilter)) =>
      val path = propertyFilter.property.name
      val names = path.split("\\.").toSeq
      // Traverse nested properties and expand array value
      val values = names.foldLeft(Seq(Value(entityValue = Some(e)))) {
        (values, name) =>
          values
            .flatMap(_.entityValue)
            .flatMap(_.properties.get(name))
            .flatMap { value =>
              value.arrayValue.map(_.values).getOrElse(Seq(value))
            }
      }
      val qValue = propertyFilter.value
      /* Follow the rule of multiple equality filters, where tags.exists(_ == "A") && tags.exists(_ == "B")
         will return true for Entity(tags = Seq("A"))
         https://cloud.google.com/datastore/docs/concepts/queries#multiple_equality_filters
         Also note that rule of multiple inequality filters is not followed yet.
         https://cloud.google.com/datastore/docs/concepts/queries#inequality_filters
       */
      values.exists { value =>
        propertyFilter.op match {
          case "EQUAL" =>
            value == qValue
          case "LESS_THAN" =>
            value < qValue
          case "LESS_THAN_OR_EQUAL" =>
            value <= qValue
          case "GREATER_THAN" =>
            value > qValue
          case "GREATER_THAN_OR_EQUAL" =>
            value >= qValue
          case _ => ???
        }
      }
    case _ => sys.error("Invalid Filter format")
  }

  def runQuery[S <: Selector](
      query: Query[S],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[RunQueryRequest],
      deserializer: BodyDeserializer[RunQueryResponse]
  ): F[Query.Result[query.selector.R]] = {
    val partitionId = PartitionId(projectId, Option(namespace))
    val q = query.query
    val entities = db.values.toSeq
      .filter(_.key.get.partitionId == partitionId)
      .filter(_.key.get.path.head.kind == q.kind.head.name)
    val filteredEntities = q.filter match {
      case Some(f) => entities.filter(e => applyFilter(f, e))
      case None    => entities
    }
    val sortedEntities = q.order match {
      case Some(orders) =>
        val entityOrdering = orders
          .map { order =>
            val ordering =
              Ordering.by[Entity, Value](_.properties(order.property.name))
            if (order.direction == "ASCENDING") ordering else ordering.reverse
          }
          .reduceLeft { (ord1, ord2) => (x, y) =>
            val res1 = ord1.compare(x, y)
            if (res1 != 0) res1 else ord2.compare(x, y)
          }
        filteredEntities.sorted(entityOrdering)
      case None => filteredEntities
    }
    // TODO: handle cursor, offset, limit
    val entityResults = sortedEntities.map(e => EntityResult(e, None))
    val batch = QueryResultBatch(
      Some(0),
      None,
      "FULL",
      Some(entityResults),
      "endCursor",
      "NO_MORE_RESULTS"
    )
    responseMonad.unit(Query.Result[query.selector.R](batch))
  }

  def transaction[R](
      f: Transaction[F] => F[(R, Seq[Mutation])]
  )(implicit
      txSerializer: BodySerializer[BeginTransactionRequest],
      txDeserializer: BodyDeserializer[BeginTransactionResponse],
      commitSerializer: BodySerializer[CommitRequest],
      commitDeserializer: BodyDeserializer[CommitResponse],
      rollbackSerializer: BodySerializer[RollbackRequest]
  ): F[R] = {
    f(TransactionEmulator(this))
      .flatMap { case (res, mutations) =>
        commit(mutations, None).map(_ => res)
      }
      .handleError { case t: Throwable => responseMonad.error(t) }
  }

  def transactionReadOnly[R](f: Transaction[F] => F[R])(implicit
      txSerializer: BodySerializer[BeginTransactionRequest],
      txDeserializer: BodyDeserializer[BeginTransactionResponse],
      commitSerializer: BodySerializer[CommitRequest],
      commitDeserializer: BodyDeserializer[CommitResponse],
      rollbackSerializer: BodySerializer[RollbackRequest]
  ): F[R] = {
    f(TransactionEmulator(this))
      .handleError { case t: Throwable => responseMonad.error(t) }
  }
}

object DatastoreEmulator {
  def synchronous(projectId: String): Datastore[Identity] =
    DatastoreEmulator(projectId, IdMonad)
  def asynchronousFuture(projectId: String)(implicit
      ec: ExecutionContext
  ): Datastore[Future] = DatastoreEmulator(projectId, new FutureMonad())
}
