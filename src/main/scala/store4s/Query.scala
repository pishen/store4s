package store4s

import com.google.cloud.datastore
import com.google.cloud.datastore.StructuredQuery.Filter
import com.google.cloud.datastore.StructuredQuery.OrderBy
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

trait Selector

case class Query[S <: Selector](
    kind: String,
    selector: S,
    filters: Seq[Filter] = Seq.empty,
    orders: Seq[OrderBy] = Seq.empty
) {
  def build() = {
    Some(datastore.Query.newEntityQueryBuilder().setKind(kind))
      .map { eb =>
        if (filters.isEmpty) eb
        else eb.setFilter(filters.reduce(_ && _))
      }
      .map { eb =>
        if (orders.isEmpty) eb
        else if (orders.size == 1) eb.setOrderBy(orders.head)
        else eb.setOrderBy(orders.head, orders.tail: _*)
      }
      .get
      .build()
  }
  def filter(f: S => Filter) = this.copy(filters = filters :+ f(selector))
  def sortBy(fs: S => Query.Property[_]*) = this.copy(
    orders = fs.map(f => OrderBy.asc(f(selector).name))
  )
}

object Query {
  case class Property[T](name: String)(implicit enc: ValueEncoder[T]) {
    def ==(t: T): Filter = PropertyFilter.eq(name, enc.encode(t))
    def >(t: T): Filter = PropertyFilter.gt(name, enc.encode(t))
    def <(t: T): Filter = PropertyFilter.lt(name, enc.encode(t))
    def >=(t: T): Filter = PropertyFilter.ge(name, enc.encode(t))
    def <=(t: T): Filter = PropertyFilter.le(name, enc.encode(t))
  }

  def from[T]: Any = macro impl[T]

  def impl[T: c.WeakTypeTag](c: Context) = {
    import c.universe._
    val typeName = weakTypeOf[T].typeSymbol.name.toString()
    val defs = weakTypeOf[T].members.toSeq.filterNot(_.isMethod).map { s =>
      val name = s.name.toString().trim()
      q"val ${TermName(name)} = store4s.Query.Property[${s.info}]($name)"
    }

    q"""
      store4s.Query(
        $typeName,
        new store4s.Selector {
          ..$defs
        }
      )
    """
  }
}
