package store4s

import cats.Id
import cats.implicits._
import com.google.cloud.datastore.{Query => GQuery}
import com.google.cloud.datastore.Cursor
import com.google.cloud.datastore.QueryResults
import com.google.cloud.datastore.ReadOption
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
    orders: Seq[OrderBy] = Seq.empty,
    limit: Option[Int] = None,
    start: Option[Cursor] = None,
    end: Option[Cursor] = None
) {
  def builder() = {
    GQuery
      .newEntityQueryBuilder()
      .setKind(kind)
      .pure[Id]
      .map(b =>
        if (filters.nonEmpty) b.setFilter(filters.reduce(_ && _)) else b
      )
      .map(b =>
        if (orders.nonEmpty) b.setOrderBy(orders.head, orders.tail: _*) else b
      )
      .map(b => limit.fold(b)(i => b.setLimit(i)))
      .map(b => start.fold(b)(c => b.setStartCursor(c)))
      .map(b => end.fold(b)(c => b.setEndCursor(c)))
  }
  def filter(f: S => Filter) = this.copy(filters = filters :+ f(selector))
  def sortBy(fs: S => OrderBy*) = this.copy(orders = fs.map(f => f(selector)))
  def take(n: Int) = this.copy(limit = Some(n))
  def startFrom(cursor: Cursor) = this.copy(start = Some(cursor))
  def endAt(cursor: Cursor) = this.copy(end = Some(cursor))
  def run(implicit datastore: Datastore) = {
    datastore.underlying.run(builder().build(), Seq.empty[ReadOption]: _*)
  }
}

object Query {
  case class Property[T](name: String)(implicit enc: ValueEncoder[T]) {
    def ==(t: T): Filter = PropertyFilter.eq(name, enc.encode(t))
    def >(t: T): Filter = PropertyFilter.gt(name, enc.encode(t))
    def <(t: T): Filter = PropertyFilter.lt(name, enc.encode(t))
    def >=(t: T): Filter = PropertyFilter.ge(name, enc.encode(t))
    def <=(t: T): Filter = PropertyFilter.le(name, enc.encode(t))
    def asc: OrderBy = OrderBy.asc(name)
    def desc: OrderBy = OrderBy.desc(name)
  }

  def apply[T]: Any = macro impl[T]

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
