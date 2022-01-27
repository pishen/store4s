package store4s

import com.google.datastore.v1.CompositeFilter
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Filter
import com.google.datastore.v1.Key
import com.google.datastore.v1.PartitionId

import scala.language.implicitConversions
import scala.reflect.runtime.universe._

package object v1 {
  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A)(implicit
      encoder: EntityEncoder[A],
      ds: Datastore
  ) {
    val typeName = weakTypeOf[A].typeSymbol.name.toString()

    def buildKey(f: Key.PathElement.Builder => Key.PathElement.Builder) = {
      val partitionId = Some(PartitionId.newBuilder())
        .map(_.setProjectId(ds.projectId))
        .map(eb => ds.namespace.map(eb.setNamespaceId).getOrElse(eb))
        .get
        .build()
      val pathBuilder = Key.PathElement.newBuilder().setKind(typeName)
      Key
        .newBuilder()
        .setPartitionId(partitionId)
        .addPath(f(pathBuilder))
        .build()
    }

    // not providing incomplete key builder since dataflow doesn't support it
    def asEntity(name: String) =
      encoder.encode(obj, Some(buildKey(_.setName(name))), Set.empty[String])

    def asEntity(id: Long) =
      encoder.encode(obj, Some(buildKey(_.setId(id))), Set.empty[String])

    def asEntity[B](f: A => B): Entity = f(obj) match {
      case id: Int   => asEntity(id.toLong)
      case id: Long  => asEntity(id)
      case name: Any => asEntity(name.toString())
    }
  }

  def decodeEntity[T](e: Entity)(implicit decoder: EntityDecoder[T]) = {
    decoder.decode(e)
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = Filter
      .newBuilder()
      .setCompositeFilter(
        CompositeFilter
          .newBuilder()
          .setOp(CompositeFilter.Operator.AND)
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
}
