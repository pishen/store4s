package store4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.OrderBy
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore.{Query => GQuery}
import org.scalatest.flatspec.AnyFlatSpec

import scala.language.reflectiveCalls
import com.google.cloud.datastore.Cursor

class QuerySpec extends AnyFlatSpec {
  "A Query" should "generate same Query as Google Cloud Java" in {
    val qG = GQuery
      .newEntityQueryBuilder()
      .setKind("Task")
      .setFilter(
        CompositeFilter.and(
          PropertyFilter.eq("done", false),
          PropertyFilter.ge("priority", 4)
        )
      )
      .setOrderBy(OrderBy.desc("priority"))
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
      .newEntityQueryBuilder()
      .setKind("Task")
      .setFilter(
        CompositeFilter.and(
          PropertyFilter.eq("done", false),
          PropertyFilter.ge("priority", 4)
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
      .newEntityQueryBuilder()
      .setKind("Task")
      .setOrderBy(OrderBy.desc("priority"), OrderBy.asc("created"))
      .build()

    case class Task(priority: Int, created: Timestamp)
    val qS = Query[Task]
      .sortBy(_.priority.desc, _.created.asc)
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "support limit" in {
    val qG = GQuery.newEntityQueryBuilder().setKind("Task").setLimit(5).build()

    case class Task(done: Boolean)
    val qS = Query[Task].take(5).builder().build()

    assert(qG == qS)
  }
}
