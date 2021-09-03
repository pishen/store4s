package store4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.QueryResults
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.OrderBy
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore.{Query => GQuery}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec

class QuerySpec extends AnyFlatSpec with MockFactory {
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

  it should "support array contains" in {
    val qG = GQuery
      .newEntityQueryBuilder()
      .setKind("Task")
      .setFilter(
        CompositeFilter.and(
          PropertyFilter.eq("tag", "fun"),
          PropertyFilter.eq("tag", "programming")
        )
      )
      .build()

    case class Task(tag: Seq[String])
    val qS = Query[Task]
      .filter(_.tag.contains("fun"))
      .filter(_.tag.contains("programming"))
      .builder()
      .build()

    assert(qG == qS)
  }

  it should "decode QueryResults" in {
    val results = new QueryResults[Entity] {
      val keyFactory = new KeyFactory("store4s").setKind("User")
      def userEntity(id: Int) = {
        Entity
          .newBuilder(keyFactory.newKey(id))
          .set("id", id)
          .build()
      }
      val iter = Iterator(1, 2, 3).map(userEntity)
      override def hasNext() = iter.hasNext
      override def next() = iter.next()
      override def getResultClass() = ???
      override def getCursorAfter() = ???
      override def getSkippedResults() = ???
      override def getMoreResults() = ???
    }

    implicit val mockDatastore = mock[Datastore]
    (mockDatastore.run _).expects(*).returning(results)

    case class User(id: Int)
    val res: Seq[User] = Query[User]
      .filter(_.id > 0)
      .run
      .getRights

    assert(res == Seq(1, 2, 3).map(id => User(id)))
  }
}
