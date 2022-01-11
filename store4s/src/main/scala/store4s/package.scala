import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter

import scala.language.implicitConversions
import scala.reflect.runtime.universe._

package object store4s {
  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A)(implicit
      encoder: EntityEncoder[A],
      ds: Datastore
  ) {
    def asEntity: FullEntity[IncompleteKey] = encoder
      .encode(obj, Some(ds.keyFactory[A].newKey()), Set.empty[String])

    def asEntity(name: String): Entity = {
      val fullEntity = asEntity
      Entity
        .newBuilder(Key.newBuilder(fullEntity.getKey, name).build(), fullEntity)
        .build()
    }

    def asEntity(id: Long): Entity = {
      val fullEntity = asEntity
      Entity
        .newBuilder(Key.newBuilder(fullEntity.getKey, id).build(), fullEntity)
        .build()
    }
  }

  def decodeEntity[T](e: FullEntity[_])(implicit decoder: EntityDecoder[T]) = {
    decoder.decode(e)
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = CompositeFilter.and(left, right)
  }

  implicit class BooleanPropertyWrapper(p: Query.Property[Boolean]) {
    def unary_! = p == false
  }

  implicit def booleanProperty2Filter(p: Query.Property[Boolean]): Filter = {
    p == true
  }
}
