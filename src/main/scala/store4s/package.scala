import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter
import com.google.datastore.v1.PartitionId

package object store4s {
  implicit class EncoderOps[T](t: T)(implicit
      enc: EntityEncoder[T],
      encCtx: EncoderContext
  ) {
    def asEntity = enc.encodeEntity(t)
    def asEntity(keyName: String) = enc.encodeEntity(t, keyName)
  }

  implicit class EncoderOpsV1[T](t: T)(implicit enc: EntityEncoderV1[T]) {
    def asV1Entity(implicit partitionId: PartitionId) = enc.encodeEntity(t)
    def asV1Entity(keyName: String)(implicit partitionId: PartitionId) =
      enc.encodeEntity(t, keyName)
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = CompositeFilter.and(left, right)
  }
}
