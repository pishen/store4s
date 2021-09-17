package store4s

import cats.Id
import cats.implicits._
import com.google.cloud.datastore.Cursor
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.QueryResults
import com.google.cloud.datastore.StructuredQuery.Filter
import com.google.cloud.datastore.StructuredQuery.OrderBy
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore.{Query => GQuery}

import scala.jdk.CollectionConverters._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

case class Query[S, T: EntityDecoder](
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
  def run(implicit ds: Datastore) = {
    Query.Result[T](ds.run(builder().build()))
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

  case class ArrayProperty[P](p: P) {
    def exists(f: P => Filter): Filter = f(p)
  }

  case class Result[T: EntityDecoder](underlying: QueryResults[Entity]) {
    def getEntities: Seq[Entity] = underlying.asScala.toList
    def getEithers = getEntities.map(decodeEntity[T])
    def getRights = getEithers.map(_.toTry.get)
    def getCursorAfter = underlying.getCursorAfter()
  }

  def apply[T]: Any = macro impl[T]

  def impl[T: c.WeakTypeTag](c: Context) = {
    import c.universe._
    val rootType = weakTypeOf[T]
    val kind = rootType.typeSymbol.name.toString()

    def getCaseMethods(t: Type) = t.members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList

    def isCaseClass(t: Type) = {
      // The isCaseClass method on ClassSymbol still has some problems, use a workaround here
      // https://stackoverflow.com/questions/12377046
      t.baseClasses.exists(_.name.toString() == "Product")
    }

    def makeTrait(t: Type, outerName: String) = {
      val defs = getCaseMethods(t).map { p =>
        val fullName = outerName + "." + p.name.toString()
        q"val ${p.name} = Query.Property[${p.returnType}](${fullName})"
      }
      val traitName = TypeName(outerName.capitalize)
      (traitName, q"trait ${traitName} { ..$defs }")
    }

    val defs = getCaseMethods(rootType).flatMap { p =>
      if (isCaseClass(p.returnType)) {
        val (traitName, traitDef) = makeTrait(p.returnType, p.name.toString())
        Seq(traitDef, q"val ${p.name} = new ${traitName} {}")
      } else if (p.returnType.typeConstructor.toString() == "Seq") {
        val elemType = p.returnType.typeArgs.head
        if (isCaseClass(elemType)) {
          val (traitName, traitDef) = makeTrait(elemType, p.name.toString())
          Seq(
            traitDef,
            q"val ${p.name} = Query.ArrayProperty(new ${traitName} {})"
          )
        } else {
          Seq(
            q"val ${p.name} = Query.ArrayProperty(Query.Property[${elemType}](${p.name.toString()}))"
          )
        }
      } else {
        Seq(
          q"val ${p.name} = Query.Property[${p.returnType}](${p.name.toString()})"
        )
      }
    }

    q"""
      trait Selector {
        ..$defs
      }
      Query[Selector, ${rootType}](
        $kind,
        new Selector {}
      )
    """
  }
}
