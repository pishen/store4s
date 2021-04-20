import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter

package object scalastore {
  implicit class EncoderOps[T](t: T)(implicit
      enc: EntityEncoder[T],
      datastore: Datastore
  ) {
    def asEntity = enc.encodeEntity(t)
    def asEntity(keyName: String) = enc.encodeEntity(t, keyName)
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = CompositeFilter.and(left, right)
  }
}
