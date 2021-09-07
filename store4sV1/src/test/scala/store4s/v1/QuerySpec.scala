package store4s.v1

import com.google.datastore.v1.CompositeFilter
import com.google.datastore.v1.Filter
import com.google.datastore.v1.KindExpression
import com.google.datastore.v1.PropertyFilter
import com.google.datastore.v1.PropertyFilter.Operator
import com.google.datastore.v1.PropertyOrder
import com.google.datastore.v1.PropertyOrder.Direction
import com.google.datastore.v1.PropertyReference
import com.google.datastore.v1.{Query => GQuery}
import com.google.datastore.v1.Value
import com.google.protobuf.Int32Value
import com.google.protobuf.Timestamp
import org.scalatest.flatspec.AnyFlatSpec

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

  "A Query" should "generate same Query as Google Cloud Java" in {
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
}
