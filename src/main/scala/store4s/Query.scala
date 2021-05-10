package store4s

import com.google.cloud.datastore.{Query => GQuery}
import com.google.cloud.datastore.Cursor
import com.google.cloud.datastore.QueryResults
import com.google.cloud.datastore.ReadOption
import com.google.cloud.datastore.StructuredQuery.Filter
import com.google.cloud.datastore.StructuredQuery.OrderBy
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import scala.jdk.CollectionConverters._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

trait Selector

case class Query[S <: Selector](
    kind: String,
    selector: S,
    filters: Seq[Filter] = Seq.empty,
    orders: Seq[OrderBy] = Seq.empty,
    limit: Option[Int] = None
) {
  def builder = {
    GQuery
      .newEntityQueryBuilder()
      .setKind(kind)
      .applyIf(filters.nonEmpty)(_.setFilter(filters.reduce(_ && _)))
      .applyIf(orders.nonEmpty)(_.setOrderBy(orders.head, orders.tail: _*))
      .applyIf(limit.nonEmpty)(_.setLimit(limit.get))
  }
  def filter(f: S => Filter) = this.copy(filters = filters :+ f(selector))
  def sortBy(fs: S => OrderBy*) = this.copy(orders = fs.map(f => f(selector)))
  def take(n: Int) = this.copy(limit = Some(n))
  def run(implicit datastore: Datastore) = {
    val res = datastore.underlying.run(
      builder.build(),
      Seq.empty[ReadOption]: _*
    )
    Query.Result(res.asScala.toList, res.getCursorAfter())
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
  case class Result[V](values: Seq[V], cursor: Cursor)

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
