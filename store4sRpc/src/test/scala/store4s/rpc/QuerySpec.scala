package store4s.rpc

import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.entity.Value
import com.google.datastore.v1.query.CompositeFilter
import com.google.datastore.v1.query.CompositeFilter.Operator.AND
import com.google.datastore.v1.query.EntityResult
import com.google.datastore.v1.query.Filter
import com.google.datastore.v1.query.KindExpression
import com.google.datastore.v1.query.PropertyFilter
import com.google.datastore.v1.query.PropertyFilter.Operator
import com.google.datastore.v1.query.PropertyOrder
import com.google.datastore.v1.query.PropertyOrder.Direction
import com.google.datastore.v1.query.PropertyReference
import com.google.datastore.v1.query.QueryResultBatch
import com.google.datastore.v1.query.{Query => GQuery}
import com.google.protobuf.struct.NullValue
import org.scalatest.flatspec.AnyFlatSpec

import java.time.Instant

class QuerySpec extends AnyFlatSpec {
  "Query" should "build a v1.query.Query" in {
    val f1 = Filter().withPropertyFilter(
      PropertyFilter(op = Operator.EQUAL)
        .withProperty(PropertyReference(name = "done"))
        .withValue(Value().withBooleanValue(false))
    )
    val f2 = Filter().withPropertyFilter(
      PropertyFilter(op = Operator.GREATER_THAN_OR_EQUAL)
        .withProperty(PropertyReference(name = "priority"))
        .withValue(Value().withIntegerValue(4))
    )
    val ans = GQuery()
      .addKind(KindExpression(name = "Task"))
      .withFilter(
        Filter().withCompositeFilter(
          CompositeFilter(op = AND).addFilters(f1, f2)
        )
      )
      .addOrder(
        PropertyOrder(direction = Direction.DESCENDING)
          .withProperty(PropertyReference(name = "priority"))
      )
    case class Task(done: Boolean, priority: Int)
    val res = Query
      .from[Task]
      .filter(t => !t.done && t.priority >= 4)
      .sortBy(_.priority.desc)
      .q
    assert(res == ans)
  }

  it should "support multiple filters" in {
    val f1 = Filter().withPropertyFilter(
      PropertyFilter(op = Operator.EQUAL)
        .withProperty(PropertyReference(name = "done"))
        .withValue(Value().withBooleanValue(false))
    )
    val f2 = Filter().withPropertyFilter(
      PropertyFilter(op = Operator.GREATER_THAN_OR_EQUAL)
        .withProperty(PropertyReference(name = "priority"))
        .withValue(Value().withIntegerValue(4))
    )
    val ans = GQuery()
      .addKind(KindExpression(name = "Task"))
      .withFilter(
        Filter().withCompositeFilter(
          CompositeFilter(op = AND).addFilters(f1, f2)
        )
      )
    case class Task(done: Boolean, priority: Int)
    val res = Query
      .from[Task]
      .filter(_.done == false)
      .filter(_.priority >= 4)
      .q
    assert(res == ans)
  }

  it should "support multiple sort orders" in {
    val ans = GQuery()
      .addKind(KindExpression(name = "Task"))
      .addOrder(
        PropertyOrder(direction = Direction.DESCENDING)
          .withProperty(PropertyReference(name = "priority")),
        PropertyOrder(direction = Direction.ASCENDING)
          .withProperty(PropertyReference(name = "created"))
      )
    case class Task(priority: Int, created: Instant)
    val res = Query.from[Task].sortBy(_.priority.desc, _.created.asc).q
    assert(res == ans)
  }

  it should "support limit" in {
    val ans = GQuery().addKind(KindExpression(name = "Task")).withLimit(5)
    case class Task(done: Boolean)
    val res = Query.from[Task].take(5).q
    assert(res == ans)
  }

  it should "support array exists" in {
    val f1 = Filter().withPropertyFilter(
      PropertyFilter(op = Operator.EQUAL)
        .withProperty(PropertyReference(name = "tag"))
        .withValue(Value().withStringValue("fun"))
    )
    val f2 = Filter().withPropertyFilter(
      PropertyFilter(op = Operator.EQUAL)
        .withProperty(PropertyReference(name = "tag"))
        .withValue(Value().withStringValue("programming"))
    )
    val ans = GQuery()
      .addKind(KindExpression(name = "Task"))
      .withFilter(
        Filter().withCompositeFilter(
          CompositeFilter(op = AND).addFilters(f1, f2)
        )
      )
    case class Task(tag: Seq[String])
    val res = Query
      .from[Task]
      .filter(_.tag.exists(_ == "fun"))
      .filter(_.tag.exists(_ == "programming"))
      .q
    assert(res == ans)
  }

  it should "support nested entity" in {
    val ans = GQuery()
      .addKind(KindExpression(name = "Zombie"))
      .withFilter(
        Filter().withPropertyFilter(
          PropertyFilter(op = Operator.EQUAL)
            .withProperty(PropertyReference(name = "hometown.city"))
            .withValue(Value().withStringValue("Saga"))
        )
      )
    case class Hometown(country: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val res = Query.from[Zombie].filter(_.hometown.city == "Saga").q
    assert(res == ans)
  }

  it should "support exists for entity array" in {
    val ans = GQuery()
      .addKind(KindExpression(name = "Group"))
      .withFilter(
        Filter().withPropertyFilter(
          PropertyFilter(op = Operator.EQUAL)
            .withProperty(PropertyReference(name = "members.name"))
            .withValue(Value().withStringValue("Sakura Minamoto"))
        )
      )
    case class Member(name: String)
    case class Group(members: Seq[Member])
    val res = Query
      .from[Group]
      .filter(_.members.exists(_.name == "Sakura Minamoto"))
      .q
    assert(res == ans)
  }

  it should "support nullable value" in {
    val ansNull = GQuery()
      .addKind(KindExpression(name = "User"))
      .withFilter(
        Filter().withPropertyFilter(
          PropertyFilter(op = Operator.EQUAL)
            .withProperty(PropertyReference(name = "name"))
            .withValue(Value().withNullValue(NullValue.NULL_VALUE))
        )
      )
    val ansSome = GQuery()
      .addKind(KindExpression(name = "User"))
      .withFilter(
        Filter().withPropertyFilter(
          PropertyFilter(op = Operator.EQUAL)
            .withProperty(PropertyReference(name = "name"))
            .withValue(Value().withStringValue("Sakura Minamoto"))
        )
      )
    case class User(name: Option[String])
    val resNull = Query.from[User].filter(_.name == None).q
    val resSome = Query.from[User].filter(_.name == Some("Sakura Minamoto")).q
    assert(resNull == ansNull)
    assert(resSome == ansSome)
  }

  it should "decode QueryResult" in {
    case class User(id: Int)
    val ans = Seq(1, 2, 3).map(id => User(id))
    val entityResults = Seq(1, 2, 3).map { id =>
      EntityResult().withEntity(
        Entity().addProperties("id" -> Value().withIntegerValue(id))
      )
    }
    val queryResult = QueryResultBatch(entityResults = entityResults)
    val res = Query.Result[User](queryResult).toSeq
    assert(res == ans)
  }
}
