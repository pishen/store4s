package store4s

import com.google.datastore.v1.CompositeFilter
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Filter
import com.google.datastore.v1.Key
import com.google.datastore.v1.PartitionId
import com.google.datastore.v1.PropertyFilter.Operator

import scala.language.implicitConversions
import scala.reflect.runtime.universe._

package object v1 {
  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A) {
    val typeName = weakTypeOf[A].typeSymbol.name.toString()

    def entityBuilder(
        f: Key.PathElement.Builder => Key.PathElement.Builder
    )(implicit ds: Datastore) = {
      val partitionId = Some(PartitionId.newBuilder())
        .map(_.setProjectId(ds.projectId))
        .map(eb => ds.namespace.map(eb.setNamespaceId).getOrElse(eb))
        .get
        .build()
      val pathBuilder = Key.PathElement.newBuilder().setKind(typeName)
      val key = Key
        .newBuilder()
        .setPartitionId(partitionId)
        .addPath(f(pathBuilder))
        .build()
      Entity.newBuilder().setKey(key)
    }

    // not providing incomplete key builder since dataflow doesn't support it
    def asEntity(name: String)(implicit
        encoder: EntityEncoder[A],
        ds: Datastore
    ) = encoder
      .encodeEntity(obj, entityBuilder(_.setName(name)))
      .build()

    def asEntity(id: Long)(implicit
        encoder: EntityEncoder[A],
        ds: Datastore
    ) = encoder
      .encodeEntity(obj, entityBuilder(_.setId(id)))
      .build()
  }

  def decodeEntity[T](e: Entity)(implicit decoder: EntityDecoder[T]) = {
    decoder.decodeEntity(e)
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = Filter
      .newBuilder()
      .setCompositeFilter(
        CompositeFilter
          .newBuilder()
          .addFilters(left)
          .addFilters(right)
      )
      .build()
  }

  implicit class BooleanPropertyWrapper(p: Query.Property[Boolean]) {
    def unary_! = p == false
  }

  implicit def booleanProperty2Filter(p: Query.Property[Boolean]): Filter = {
    p == true
  }

  implicit class PropertyWrapper[T](arr: Query.Property[Seq[T]]) {
    def contains(t: T)(implicit enc: ValueEncoder[T]): Filter = {
      Query.Property[T](arr.name).createFilter(Operator.EQUAL, t)
    }
  }
}
