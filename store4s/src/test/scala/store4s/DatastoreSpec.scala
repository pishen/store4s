package store4s

import com.google.cloud.datastore.Key
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.{Datastore => GDatastore}
import com.google.cloud.datastore.{Transaction => GTransaction}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec

class DatastoreSpec extends AnyFlatSpec with MockFactory {

  "A Datastore" should "commit when a transaction finish" in {
    val mockGDatastore = mock[GDatastore]
    val mockGTransaction = mock[GTransaction]
    (mockGDatastore.newTransaction _: () => GTransaction)
      .expects()
      .returning(mockGTransaction)
    (mockGTransaction.delete(_: Key)).expects(*)
    (mockGTransaction.commit _).expects()

    val keyFactory = new KeyFactory("store4s")

    val ds = Datastore(mockGDatastore)

    ds.transaction { tx =>
      tx.delete(keyFactory.setKind("Zombie").newKey("heroine"))
    }
  }

  it should "rollback when a transaction fail" in {
    val mockGDatastore = mock[GDatastore]
    val mockGTransaction = mock[GTransaction]
    (mockGDatastore.newTransaction _: () => GTransaction)
      .expects()
      .returning(mockGTransaction)
    (mockGTransaction.isActive _).expects().returning(true)
    (mockGTransaction.rollback _).expects()

    val ds = Datastore(mockGDatastore)

    assertThrows[Exception] {
      ds.transaction { _ =>
        throw new Exception("error")
      }
    }
  }
}
