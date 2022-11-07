package store4s

import store4s.async.model._

import scala.reflect.runtime.universe._

package object async {
  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A) {
    val kind = weakTypeOf[A].typeSymbol.name.toString()

    def asEntity(implicit partitionId: PartitionId, enc: EntityEncoder[A]) =
      enc.encode(
        obj,
        Some(Key(partitionId, Seq(PathElement(kind, None, None)))),
        Set.empty[String]
      )

    def asEntity(id: Long)(implicit
        partitionId: PartitionId,
        enc: EntityEncoder[A]
    ) = enc.encode(
      obj,
      Some(Key(partitionId, Seq(PathElement(kind, Some(id.toString), None)))),
      Set.empty[String]
    )

    def asEntity(name: String)(implicit
        partitionId: PartitionId,
        enc: EntityEncoder[A]
    ) = enc.encode(
      obj,
      Some(Key(partitionId, Seq(PathElement(kind, None, Some(name))))),
      Set.empty[String]
    )
  }
}
