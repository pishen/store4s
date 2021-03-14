package scalastore

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import shapeless._
import shapeless.labelled._
import scala.reflect._

trait Encoder[A] {
  def builder(obj: A): FullEntity.Builder[IncompleteKey]

  def encode(obj: A): FullEntity[IncompleteKey] = builder(obj).build()

  def encode(obj: A, name: String)(implicit
      tag: ClassTag[A],
      datastore: Datastore
  ) = {
    val className = classTag[A].runtimeClass.getSimpleName()
    val key = datastore.keyFactory.setKind(className).newKey(name)
    builder(obj).setKey(key).build()
  }

  def encode(obj: A, id: Long)(implicit
      tag: ClassTag[A],
      datastore: Datastore
  ) = {
    val className = classTag[A].runtimeClass.getSimpleName()
    val key = datastore.keyFactory.setKind(className).newKey(id)
    builder(obj).setKey(key).build()
  }
}

object Encoder {
  def apply[A](implicit enc: Encoder[A]): Encoder[A] = enc

  def create[A](f: A => FullEntity.Builder[IncompleteKey]) = new Encoder[A] {
    def builder(obj: A) = f(obj)
  }

  implicit val hnilEncoder: Encoder[HNil] = create(_ => FullEntity.newBuilder())

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: Encoder[T]
  ): Encoder[FieldType[K, H] :: T] = {
    val fieldName = witness.value.name
    create { hlist =>
      tEncoder.builder(hlist.tail).set(fieldName, hEncoder.encode(hlist.head))
    }
  }

  implicit def genericEncoder[A: ClassTag, H <: HList](implicit
      generic: LabelledGeneric.Aux[A, H],
      hEncoder: Encoder[H],
      datastore: Datastore
  ): Encoder[A] = {
    val className = classTag[A].runtimeClass.getSimpleName()
    println(className)
    val key = datastore.keyFactory.setKind(className).newKey()
    create { obj =>
      hEncoder.builder(generic.to(obj)).setKey(key)
    }
  }
}
