package store4s

import com.google.datastore.v1.entity.Entity

package object rpc {
  implicit class EncoderOps[T](t: T)(implicit enc: Encoder[T]) {
    def asEntity(id: Long): Entity = enc.withId(_ => id).encodeEntity(t)
    def asEntity(name: String): Entity = enc.withName(_ => name).encodeEntity(t)
    def asEntity: Entity = enc.encodeEntity(t)
  }
}
