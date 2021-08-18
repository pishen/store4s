package store4s.v1

import com.google.datastore.v1.{Query => GQuery}
import com.google.datastore.v1.Filter
import com.google.datastore.v1.PropertyFilter
import com.google.datastore.v1.PropertyFilter.Operator
import com.google.datastore.v1.PropertyOrder
import com.google.datastore.v1.PropertyOrder.Direction
import com.google.datastore.v1.PropertyReference
import com.google.protobuf.ByteString
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import com.google.datastore.v1.KindExpression

trait Selector

case class Query[S <: Selector](
    kind: String,
    selector: S,
    filters: Seq[Filter] = Seq.empty,
    orders: Seq[PropertyOrder] = Seq.empty,
    limit: Option[Int] = None,
    start: Option[ByteString] = None,
    end: Option[ByteString] = None
) {
  def build() = Some(GQuery.newBuilder())
    .map(_.addKind(KindExpression.newBuilder().setName(kind)))
    .get
    .build()
}

object Query {
  case class Property[T](name: String)(implicit enc: ValueEncoder[T]) {
    def createFilter(op: Operator, t: T) = Filter
      .newBuilder()
      .setPropertyFilter(
        PropertyFilter
          .newBuilder()
          .setOp(op)
          .setProperty(PropertyReference.newBuilder().setName(name))
          .setValue(enc.encode(t))
      )
      .build()
    def ==(t: T): Filter = createFilter(Operator.EQUAL, t)
    def >(t: T): Filter = createFilter(Operator.GREATER_THAN, t)
    def <(t: T): Filter = createFilter(Operator.LESS_THAN, t)
    def >=(t: T): Filter = createFilter(Operator.GREATER_THAN_OR_EQUAL, t)
    def <=(t: T): Filter = createFilter(Operator.LESS_THAN_OR_EQUAL, t)

    def createOrder(direction: Direction) = PropertyOrder.newBuilder()
      .setDirection(direction)
      .setProperty(PropertyReference.newBuilder().setName(name))
      .build()
    def asc: PropertyOrder = createOrder(Direction.ASCENDING)
    def desc: PropertyOrder = createOrder(Direction.DESCENDING)
  }

  def apply[T]: Any = macro impl[T]

  def impl[T: c.WeakTypeTag](c: Context) = {
    import c.universe._
    val typeName = weakTypeOf[T].typeSymbol.name.toString()
    val defs = weakTypeOf[T].members.toSeq.filterNot(_.isMethod).map { s =>
      val name = s.name.toString().trim()
      q"val ${TermName(name)} = store4s.v1.Query.Property[${s.info}]($name)"
    }

    q"""
      store4s.v1.Query(
        $typeName,
        new store4s.v1.Selector {
          ..$defs
        }
      )
    """
  }
}
