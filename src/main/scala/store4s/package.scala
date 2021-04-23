import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter

package object store4s {
  implicit class EncoderOps[T](t: T)(implicit
      enc: EntityEncoder[T],
      encCtx: EncoderContext
  ) {
    def asEntity = enc.encodeEntity(t)
    def asEntity(keyName: String) = enc.encodeEntity(t, keyName)
  }

  implicit class EntityOps(entity: FullEntity[_]) {
    def toV1 = EntityEncoder.toV1Entity(entity)
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = CompositeFilter.and(left, right)
  }
}
