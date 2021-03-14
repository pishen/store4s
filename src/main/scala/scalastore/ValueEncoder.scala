package scalastore

import com.google.cloud.datastore.Blob
import com.google.cloud.datastore.BlobValue
import com.google.cloud.datastore.BooleanValue
import com.google.cloud.datastore.DoubleValue
import com.google.cloud.datastore.LongValue
import com.google.cloud.datastore.StringValue
import com.google.cloud.datastore.Value

trait ValueEncoder[V] {
  type Base
  def encode(v: V): Value[Base]
}

object ValueEncoder {
  def create[V, O](f: V => Value[O]) = new ValueEncoder[V] {
    type Base = O
    def encode(v: V) = f(v)
  }

  implicit val blobEncoder = create(BlobValue.of)
  implicit val booleanEncoder = create(BooleanValue.of)
  implicit val doubleEncoder = create(DoubleValue.of)

  implicit val longEncoder = create(LongValue.of)

  implicit val stringEncoder = create(StringValue.of)
}
