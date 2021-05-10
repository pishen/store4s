import com.google.cloud.datastore.EntityQuery
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter
import scala.language.implicitConversions
import scala.reflect._

package object store4s {
  case class KeyContext(project: String, namespace: Option[String]) {
    def newKeyFactory(kind: String) = {
      namespace
        .map(new KeyFactory(project, _))
        .getOrElse(new KeyFactory(project))
        .setKind(kind)
    }
    // use EntityDecoder to force user filling T
    def newKey[T: ClassTag: EntityDecoder](name: String) = {
      newKeyFactory(classTag[T].runtimeClass.getSimpleName).newKey(name)
    }
    def newKey[T: ClassTag: EntityDecoder](id: Long) = {
      newKeyFactory(classTag[T].runtimeClass.getSimpleName).newKey(id)
    }
  }

  implicit class QueryBuilderWrapper(eb: EntityQuery.Builder) {
    def applyIf(
        cond: Boolean
    )(f: EntityQuery.Builder => EntityQuery.Builder) = {
      if (cond) f(eb) else eb
    }
  }

  implicit class EncoderOps[T](t: T)(implicit
      enc: EntityEncoder[T],
      keyCtx: KeyContext
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

  implicit class BooleanProperty(p: Query.Property[Boolean]) {
    def unary_! = p == false
  }

  implicit def booleanProperty2Filter(p: Query.Property[Boolean]): Filter = {
    p == true
  }
}
