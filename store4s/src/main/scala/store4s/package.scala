import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter

import scala.reflect.runtime.universe._

package object store4s {
  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A)(implicit
      encoder: EntityEncoder[A],
      ds: Datastore
  ) {
    def asEntity: FullEntity[IncompleteKey] = encoder
      .encode(obj, Some(ds.keyFactory[A].newKey()), Set.empty[String])

    def asEntity(key: Key): Entity = {
      val entity = encoder.encode(obj, None, Set.empty[String])
      Entity.newBuilder(key, entity).build()
    }

    def asEntity(name: String): Entity = {
      asEntity(ds.keyFactory[A].newKey(name))
    }

    def asEntity(id: Long): Entity = {
      asEntity(ds.keyFactory[A].newKey(id))
    }

    def asEntity[B](f: A => B): Entity = f(obj) match {
      case id: Int  => asEntity(id.toLong)
      case id: Long => asEntity(id)
      case name     => asEntity(name.toString())
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
