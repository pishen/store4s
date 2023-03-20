package store4s.sttp

import org.scalatest.flatspec.AnyFlatSpec
import store4s.sttp.model.CompositeFilter
import store4s.sttp.model.Entity
import store4s.sttp.model.EntityResult
import store4s.sttp.model.Filter
import store4s.sttp.model.KindExpression
import store4s.sttp.model.PropertyFilter
import store4s.sttp.model.PropertyOrder
import store4s.sttp.model.PropertyReference
import store4s.sttp.model.QueryResultBatch
import store4s.sttp.model.Value

import java.time.Instant

class QuerySpec extends AnyFlatSpec {
  "A Query" should "build a model.Query" in {
    val f1 = Filter(propertyFilter =
      Some(
        PropertyFilter(
          PropertyReference("done"),
          "EQUAL",
          Value(Some(false), booleanValue = Some(false))
        )
      )
    )
    val f2 = Filter(propertyFilter =
      Some(
        PropertyFilter(
          PropertyReference("priority"),
          "GREATER_THAN_OR_EQUAL",
          Value(Some(false), integerValue = Some("4"))
        )
      )
    )
    val ans = model.Query(
      Seq(KindExpression("Task")),
      Some(Filter(compositeFilter = Some(CompositeFilter("AND", Seq(f1, f2))))),
      Some(Seq(PropertyOrder(PropertyReference("priority"), "DESCENDING")))
    )
    case class Task(done: Boolean, priority: Int)
    val res = Query
      .from[Task]
      .filter(t => !t.done && t.priority >= 4)
      .sortBy(_.priority.desc)
      .query
    assert(res == ans)
  }

  it should "support multiple filters" in {
    val f1 = Filter(propertyFilter =
      Some(
        PropertyFilter(
          PropertyReference("done"),
          "EQUAL",
          Value(Some(false), booleanValue = Some(false))
        )
      )
    )
    val f2 = Filter(propertyFilter =
      Some(
        PropertyFilter(
          PropertyReference("priority"),
          "GREATER_THAN_OR_EQUAL",
          Value(Some(false), integerValue = Some("4"))
        )
      )
    )
    val ans = model.Query(
      Seq(KindExpression("Task")),
      Some(Filter(compositeFilter = Some(CompositeFilter("AND", Seq(f1, f2)))))
    )
    case class Task(done: Boolean, priority: Int)
    val res = Query
      .from[Task]
      .filter(_.done == false)
      .filter(_.priority >= 4)
      .query
    assert(res == ans)
  }

  it should "support multiple sort orders" in {
    val ans = model.Query(
      Seq(KindExpression("Task")),
      order = Some(
        Seq(
          PropertyOrder(PropertyReference("priority"), "DESCENDING"),
          PropertyOrder(PropertyReference("created"), "ASCENDING")
        )
      )
    )
    case class Task(priority: Int, created: Instant)
    val res = Query
      .from[Task]
      .sortBy(_.priority.desc, _.created.asc)
      .query
    assert(res == ans)
  }

  it should "support limit" in {
    val ans = model.Query(Seq(KindExpression("Task")), limit = Some(5))
    case class Task(done: Boolean)
    val res = Query.from[Task].take(5).query
    assert(res == ans)
  }

  it should "support array exists" in {
    val f1 = Filter(propertyFilter =
      Some(
        PropertyFilter(
          PropertyReference("tag"),
          "EQUAL",
          Value(Some(false), stringValue = Some("fun"))
        )
      )
    )
    val f2 = Filter(propertyFilter =
      Some(
        PropertyFilter(
          PropertyReference("tag"),
          "EQUAL",
          Value(Some(false), stringValue = Some("programming"))
        )
      )
    )
    val ans = model.Query(
      Seq(KindExpression("Task")),
      Some(Filter(compositeFilter = Some(CompositeFilter("AND", Seq(f1, f2)))))
    )
    case class Task(tag: Seq[String])
    val res = Query
      .from[Task]
      .filter(_.tag.exists(_ == "fun"))
      .filter(_.tag.exists(_ == "programming"))
      .query
    assert(res == ans)
  }

  it should "support nested entity" in {
    val ans = model.Query(
      Seq(KindExpression("Zombie")),
      Some(
        Filter(propertyFilter =
          Some(
            PropertyFilter(
              PropertyReference("hometown.city"),
              "EQUAL",
              Value(Some(false), stringValue = Some("Saga"))
            )
          )
        )
      )
    )
    case class Hometown(country: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val res = Query
      .from[Zombie]
      .filter(_.hometown.city == "Saga")
      .query
    assert(res == ans)
  }

  it should "support exists for entity array" in {
    val ans = model.Query(
      Seq(KindExpression("Group")),
      Some(
        Filter(propertyFilter =
          Some(
            PropertyFilter(
              PropertyReference("members.name"),
              "EQUAL",
              Value(Some(false), stringValue = Some("Sakura Minamoto"))
            )
          )
        )
      )
    )
    case class Member(name: String)
    case class Group(members: Seq[Member])
    val res = Query
      .from[Group]
      .filter(_.members.exists(_.name == "Sakura Minamoto"))
      .query
    assert(res == ans)
  }

  it should "support nullable value" in {
    val ansNull = model.Query(
      Seq(KindExpression("User")),
      Some(
        Filter(propertyFilter =
          Some(
            PropertyFilter(
              PropertyReference("name"),
              "EQUAL",
              Value(Some(false), nullValue = Some("NULL_VALUE"))
            )
          )
        )
      )
    )
    val ansSome = model.Query(
      Seq(KindExpression("User")),
      Some(
        Filter(propertyFilter =
          Some(
            PropertyFilter(
              PropertyReference("name"),
              "EQUAL",
              Value(Some(false), stringValue = Some("Sakura Minamoto"))
            )
          )
        )
      )
    )
    case class User(name: Option[String])
    val resNull = Query
      .from[User]
      .filter(_.name == None)
      .query
    val resSome = Query
      .from[User]
      .filter(_.name == Some("Sakura Minamoto"))
      .query
    assert(resNull == ansNull)
    assert(resSome == ansSome)
  }

  it should "decode QueryResults" in {
    case class User(id: Int)
    val ans = Seq(1, 2, 3).map(id => User(id))
    val entityResults = Seq(1, 2, 3).map { id =>
      EntityResult(
        Entity(
          None,
          Map("id" -> Value(Some(false), integerValue = Some(id.toString)))
        ),
        None
      )
    }
    val queryResult = QueryResultBatch(
      None,
      None,
      "FULL",
      Some(entityResults),
      "end_cursor",
      "NO_MORE_RESULTS"
    )
    val res = Query.Result[User](queryResult).toSeq
    assert(res == ans)
  }
}
