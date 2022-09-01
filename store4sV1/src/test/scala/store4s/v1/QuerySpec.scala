package store4s.v1

import com.google.datastore.v1.CompositeFilter
import com.google.datastore.v1.EntityResult
import com.google.datastore.v1.Filter
import com.google.datastore.v1.KindExpression
import com.google.datastore.v1.PropertyFilter
import com.google.datastore.v1.PropertyFilter.Operator
import com.google.datastore.v1.PropertyOrder
import com.google.datastore.v1.PropertyOrder.Direction
import com.google.datastore.v1.PropertyReference
import com.google.datastore.v1.QueryResultBatch
import com.google.datastore.v1.RunQueryResponse
import com.google.datastore.v1.Value
import com.google.datastore.v1.client.DatastoreOptions
import com.google.datastore.v1.{Query => GQuery}
import com.google.protobuf.Int32Value
import com.google.protobuf.NullValue
import com.google.protobuf.Timestamp
import org.scalatest.flatspec.AnyFlatSpec

import scala.jdk.CollectionConverters._

class QuerySpec extends AnyFlatSpec {
  val filter1 = Filter
    .newBuilder()
    .setPropertyFilter(
      PropertyFilter
        .newBuilder()
        .setOp(Operator.EQUAL)
        .setProperty(PropertyReference.newBuilder().setName("done"))
        .setValue(Value.newBuilder().setBooleanValue(false).build())
    )
    .build()
  val filter2 = Filter
    .newBuilder()
    .setPropertyFilter(
      PropertyFilter
        .newBuilder()
        .setOp(Operator.GREATER_THAN_OR_EQUAL)
        .setProperty(PropertyReference.newBuilder().setName("priority"))
        .setValue(Value.newBuilder().setIntegerValue(4).build())
    )
    .build()
  val order1 = PropertyOrder
    .newBuilder()
    .setDirection(Direction.DESCENDING)
    .setProperty(PropertyReference.newBuilder().setName("priority"))
    .build()
  val order2 = PropertyOrder
    .newBuilder()
    .setDirection(Direction.ASCENDING)
    .setProperty(PropertyReference.newBuilder().setName("created"))
    .build()

  "A v1.Query" should "generate same Query as Google Cloud Java" in {
    val qG = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("Task"))
      .setFilter(
        Filter
          .newBuilder()
          .setCompositeFilter(
            CompositeFilter
              .newBuilder()
              .addFilters(filter1)
              .addFilters(filter2)
              .setOp(CompositeFilter.Operator.AND)
          )
      )
      .addOrder(order1)
      .build()

    case class Task(done: Boolean, priority: Int)
    val qS = Query[Task]
      .filter(t => !t.done && t.priority >= 4)
      .sortBy(_.priority.desc)
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "support multiple filters" in {
    val qG = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("Task"))
      .setFilter(
        Filter
          .newBuilder()
          .setCompositeFilter(
            CompositeFilter
              .newBuilder()
              .addFilters(filter1)
              .addFilters(filter2)
              .setOp(CompositeFilter.Operator.AND)
          )
      )
      .build()

    case class Task(done: Boolean, priority: Int)
    val qS = Query[Task]
      .filter(_.done == false)
      .filter(_.priority >= 4)
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "support multiple sort orders" in {
    val qG = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("Task"))
      .addOrder(order1)
      .addOrder(order2)
      .build()

    case class Task(priority: Int, created: Timestamp)
    val qS = Query[Task]
      .sortBy(_.priority.desc, _.created.asc)
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "support limit" in {
    val qG = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("Task"))
      .setLimit(Int32Value.of(5))
      .build()

    case class Task(done: Boolean)
    val qS = Query[Task].take(5).builder().build()

    assert(qG == qS)
  }

  it should "support array exists" in {
    val arrayFilter1 = Filter
      .newBuilder()
      .setPropertyFilter(
        PropertyFilter
          .newBuilder()
          .setOp(Operator.EQUAL)
          .setProperty(PropertyReference.newBuilder().setName("tag"))
          .setValue(Value.newBuilder().setStringValue("fun").build())
      )
      .build()
    val arrayFilter2 = Filter
      .newBuilder()
      .setPropertyFilter(
        PropertyFilter
          .newBuilder()
          .setOp(Operator.EQUAL)
          .setProperty(PropertyReference.newBuilder().setName("tag"))
          .setValue(Value.newBuilder().setStringValue("programming").build())
      )
      .build()
    val qG = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("Task"))
      .setFilter(
        Filter
          .newBuilder()
          .setCompositeFilter(
            CompositeFilter
              .newBuilder()
              .addFilters(arrayFilter1)
              .addFilters(arrayFilter2)
              .setOp(CompositeFilter.Operator.AND)
          )
      )
      .build()

    case class Task(tag: Seq[String])
    val qS = Query[Task]
      .filter(_.tag.exists(_ == "fun"))
      .filter(_.tag.exists(_ == "programming"))
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "support nested entity" in {
    val qG = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("Zombie"))
      .setFilter(
        Filter
          .newBuilder()
          .setPropertyFilter(
            PropertyFilter
              .newBuilder()
              .setOp(Operator.EQUAL)
              .setProperty(
                PropertyReference.newBuilder().setName("hometown.city")
              )
              .setValue(Value.newBuilder().setStringValue("Saga").build())
          )
      )
      .build()

    case class Hometown(country: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val qS = Query[Zombie]
      .filter(_.hometown.city == "Saga")
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "support exists for entity array" in {
    val qG = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("Group"))
      .setFilter(
        Filter
          .newBuilder()
          .setPropertyFilter(
            PropertyFilter
              .newBuilder()
              .setOp(Operator.EQUAL)
              .setProperty(
                PropertyReference.newBuilder().setName("members.name")
              )
              .setValue(
                Value.newBuilder().setStringValue("Sakura Minamoto").build()
              )
          )
      )
      .build()

    case class Member(name: String)
    case class Group(members: Seq[Member])
    val qS = Query[Group]
      .filter(_.members.exists(_.name == "Sakura Minamoto"))
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "support nullable value" in {
    def qG(v: Value) = GQuery
      .newBuilder()
      .addKind(KindExpression.newBuilder().setName("User"))
      .setFilter(
        Filter
          .newBuilder()
          .setPropertyFilter(
            PropertyFilter
              .newBuilder()
              .setOp(Operator.EQUAL)
              .setProperty(
                PropertyReference.newBuilder().setName("name")
              )
              .setValue(v)
          )
      )
      .build()
    val qGNull =
      qG(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
    val qGSome =
      qG(Value.newBuilder().setStringValue("Sakura Minamoto").build())

    case class User(name: Option[String])
    val qSNull = Query[User]
      .filter(_.name == None)
      .builder()
      .build()
    val qSSome = Query[User]
      .filter(_.name == Some("Sakura Minamoto"))
      .builder()
      .build()

    assert(qGNull == qSNull)
    assert(qGSome == qSSome)
  }

  it should "decode RunQueryResponse" in {
    val options = new DatastoreOptions.Builder().projectId("store4s").build()
    implicit val ds = Datastore(options)

    case class User(id: Int)
    val users = Seq(1, 2, 3).map(id => User(id))

    val resp = RunQueryResponse
      .newBuilder()
      .setBatch(
        QueryResultBatch
          .newBuilder()
          .addAllEntityResults(
            users.map { user =>
              EntityResult
                .newBuilder()
                .setEntity(user.asEntity(user.id.toLong))
                .build()
            }.asJava
          )
      )
      .build()

    assert(Query.Result[User](resp).getRights == users)
  }
}
