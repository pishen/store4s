package store4s.async.testing

import store4s.async.model.AllocateIdBody
import store4s.async.model.CommitRequest
import store4s.async.model.CommitResponse
import store4s.async.model.Entity
import store4s.async.model.EntityResult
import store4s.async.model.Filter
import store4s.async.model.Key
import store4s.async.model.LookupRequest
import store4s.async.model.LookupResponse
import store4s.async.model.Mutation
import store4s.async.model.MutationResult
import store4s.async.model.QueryResultBatch
import store4s.async.model.ReadOptions
import store4s.async.model.RunQueryRequest
import store4s.async.model.RunQueryResponse
import store4s.async.model.Value
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method
import sttp.model.StatusCode

import java.time.ZonedDateTime
import scala.math.Ordering.Implicits._
import scala.util.Random

case class BackendSimulator(projectId: String)(implicit
    allocateIdDec: BodyDecoder[AllocateIdBody],
    allocateIdEnc: BodyEncoder[AllocateIdBody],
    commitDec: BodyDecoder[CommitRequest],
    commitEnc: BodyEncoder[CommitResponse],
    lookupDec: BodyDecoder[LookupRequest],
    lookupEnc: BodyEncoder[LookupResponse],
    queryDec: BodyDecoder[RunQueryRequest],
    queryEnc: BodyEncoder[RunQueryResponse]
) {
  var db = Map.empty[Key, Entity]
  var allocatedKeys = Set.empty[Key]

  implicit val valueOrdering = new Ordering[Value] {
    def compare(x: Value, y: Value): Int = {
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
        Ordering[ZonedDateTime].compare(
          ZonedDateTime.parse(x.timestampValue.get),
          ZonedDateTime.parse(y.timestampValue.get)
        )
      } else if (x.stringValue.isDefined) {
        Ordering[String].compare(x.stringValue.get, y.stringValue.get)
      } else {
        throw new Exception("Order not supported")
      }
    }
  }

  def buildUri(method: String) =
    uri"https://datastore.googleapis.com/v1/projects/${projectId}:${method}"

  val backend = SttpBackendStub.synchronous.whenRequestMatchesPartial {
    case r if r.uri == buildUri("allocateIds") =>
      assert(r.method == Method.POST)
      val in = allocateIdDec.decode(r.body.asInstanceOf[StringBody].s)
      val newAllocatedKeys = in.keys.foldLeft(allocatedKeys) {
        case (allocatedKeys, imcompleteKey) =>
          def gen(imcompleteKey: Key): Key = {
            val elem = imcompleteKey.path.head
              .copy(id = Some(Random.nextLong().toString()))
            val key = imcompleteKey.copy(path = Seq(elem))
            if (allocatedKeys.contains(key)) gen(imcompleteKey) else key
          }
          allocatedKeys + gen(imcompleteKey)
      }
      val newKeys = (newAllocatedKeys -- allocatedKeys).toSeq
      allocatedKeys = newAllocatedKeys
      Response.ok(allocateIdEnc.encode(AllocateIdBody(newKeys)))
    case r if r.uri == buildUri("commit") =>
      assert(r.method == Method.POST)
      val in = commitDec.decode(r.body.asInstanceOf[StringBody].s)
      assert(
        in.mode == "NON_TRANSACTIONAL" || (in.mode == "TRANSACTIONAL" && in.transaction.isDefined)
      )
      val res =
        in.mutations.foldLeft[Either[String, Map[Key, Entity]]](Right(db)) {
          case (Right(db), Mutation(Some(e), None, None, None)) =>
            // insert
            Either.cond(
              !db.contains(e.key.get),
              db + (e.key.get -> e),
              s"${e.key.get} already exists."
            )
          case (Right(db), Mutation(None, Some(e), None, None)) =>
            // update
            Either.cond(
              db.contains(e.key.get),
              db + (e.key.get -> e),
              s"${e.key.get} does not exist."
            )
          case (Right(db), Mutation(None, None, Some(e), None)) =>
            // upsert
            Right(db + (e.key.get -> e))
          case (Right(db), Mutation(None, None, None, Some(k))) =>
            // delete
            Right(db - k)
          case (Right(_), _)  => Left("Invalid Mutation format.")
          case (Left(msg), _) => Left(msg)
        }
      res match {
        case Right(newDB) =>
          db = newDB
          val res =
            CommitResponse(in.mutations.map(_ => MutationResult(None, "1")), 0)
          Response.ok(commitEnc.encode(res))
        case Left(msg) =>
          Response(msg, StatusCode.BadRequest)
      }
    case r if r.uri == buildUri("lookup") =>
      assert(r.method == Method.POST)
      val in = lookupDec.decode(r.body.asInstanceOf[StringBody].s)
      assert(
        in.readOptions match {
          case ReadOptions(Some("STRONG"), None)   => true
          case ReadOptions(Some("EVENTUAL"), None) => true
          case ReadOptions(None, Some(_))          => true
          case _                                   => false
        }
      )
      val found = Some(
        in.keys.flatMap(key => db.get(key)).map(e => EntityResult(e, None))
      )
      val res = LookupResponse(found, None, None)
      Response.ok(lookupEnc.encode(res))
    case r if r.uri == buildUri("runQuery") =>
      assert(r.method == Method.POST)
      val in = queryDec.decode(r.body.asInstanceOf[StringBody].s)
      assert(
        in.readOptions match {
          case ReadOptions(Some("STRONG"), None)   => true
          case ReadOptions(Some("EVENTUAL"), None) => true
          case ReadOptions(None, Some(_))          => true
          case _                                   => false
        }
      )
      def applyFilter(f: Filter, e: Entity): Boolean = f match {
        case Filter(Some(compositeFilter), None) =>
          assert(compositeFilter.op == "AND")
          compositeFilter.filters.forall(f => applyFilter(f, e))
        case Filter(None, Some(propertyFilter)) =>
          val name = propertyFilter.property.name
          e.properties.get(name) match {
            case Some(value) =>
              val qValue = propertyFilter.value
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
            case None => false
          }
        case _ => throw new Exception("Invalid Filter format")
      }
      val entities = db.values.toSeq
        .filter(_.key.get.partitionId == in.partitionId)
        .filter(_.key.get.path.head.kind == in.query.kind.head.name)
      val filteredEntities = in.query.filter match {
        case Some(f) => entities.filter(e => applyFilter(f, e))
        case None    => entities
      }
      val sortedEntities = in.query.order match {
        case Some(orders) =>
          val entityOrdering = orders
            .map { order =>
              val ordering =
                Ordering.by[Entity, Value](_.properties(order.property.name))
              if (order.direction == "ASCENDING") ordering else ordering.reverse
            }
            .reduceLeft(_ orElse _)
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
      Response.ok(queryEnc.encode(RunQueryResponse(batch)))
  }
}
