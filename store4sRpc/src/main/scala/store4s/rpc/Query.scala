package store4s.rpc

import com.google.datastore.v1.query.CompositeFilter
import com.google.datastore.v1.query.CompositeFilter.Operator.AND
import com.google.datastore.v1.query.Filter
import com.google.datastore.v1.query.KindExpression
import com.google.datastore.v1.query.PropertyFilter
import com.google.datastore.v1.query.PropertyFilter.Operator
import com.google.datastore.v1.query.PropertyOrder
import com.google.datastore.v1.query.PropertyOrder.Direction
import com.google.datastore.v1.query.PropertyReference
import com.google.datastore.v1.query.QueryResultBatch
import com.google.datastore.v1.query.{Query => GQuery}
import com.google.protobuf.ByteString

import scala.concurrent.ExecutionContext
import scala.reflect.macros.whitebox.Context

trait Selector { type R }

case class Query[S <: Selector](selector: S, q: GQuery) {
  def filter(f: S => Filter) = {
    val newFilter = q.filter
      .map(oldFilter =>
        Filter().withCompositeFilter(
          CompositeFilter(op = AND).addFilters(oldFilter, f(selector))
        )
      )
      .getOrElse(f(selector))
    this.copy(q = q.withFilter(newFilter))
  }
  def sortBy(fs: S => PropertyOrder*) =
    this.copy(q = q.withOrder(fs.map(f => f(selector))))
  def startCursor(cursor: ByteString) = this.copy(q = q.withStartCursor(cursor))
  def endCursor(cursor: ByteString) = this.copy(q = q.withEndCursor(cursor))
  def drop(n: Int) = {
    require(q.limit.isEmpty, "drop should be applied before take")
    this.copy(q = q.withOffset(n))
  }
  def take(n: Int) = this.copy(q = q.withLimit(n))

  def run(ds: Datastore)(implicit ec: ExecutionContext) = ds.runQuery(this)
}

object Query {
  case class Property[T](name: String)(implicit enc: Encoder[T]) {
    def propertyFilter(op: Operator, t: T) = Filter().withPropertyFilter(
      PropertyFilter(op = op)
        .withProperty(PropertyReference(name = name))
        .withValue(enc.encode(t))
    )
    def ==(t: T): Filter = propertyFilter(Operator.EQUAL, t)
    def >(t: T): Filter = propertyFilter(Operator.GREATER_THAN, t)
    def <(t: T): Filter = propertyFilter(Operator.LESS_THAN, t)
    def >=(t: T): Filter = propertyFilter(Operator.GREATER_THAN_OR_EQUAL, t)
    def <=(t: T): Filter = propertyFilter(Operator.LESS_THAN_OR_EQUAL, t)
    def asc = PropertyOrder(direction = Direction.ASCENDING)
      .withProperty(PropertyReference(name = name))
    def desc = PropertyOrder(direction = Direction.DESCENDING)
      .withProperty(PropertyReference(name = name))
  }

  case class ArrayProperty[P](p: P) {
    def exists(f: P => Filter): Filter = f(p)
  }

  case class Result[T](underlying: QueryResultBatch) {
    def toSeq(implicit dec: Decoder[T]): Seq[T] =
      underlying.entityResults.map(er => dec.decodeEntity(er.getEntity))
    def endCursor: ByteString = underlying.endCursor
  }

  def apply[S <: Selector](selector: S, kind: String): Query[S] =
    Query[S](selector, GQuery(kind = Seq(KindExpression(name = kind))))

  def from[T]: Any = macro impl[T]

  def impl[T: c.WeakTypeTag](c: Context) = {
    import c.universe._
    val rootType = weakTypeOf[T]
    val kind = rootType.typeSymbol.name.toString()

    def getCaseMethods(t: Type) = t.members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList

    def isCaseClass(t: Type) = {
      // isCaseClass may not work correctly if not initialized first,
      // which may cause error in nested Entity or array of Entities
      // https://stackoverflow.com/questions/12377046
      { t.typeSymbol.asClass.typeSignature }
      t.typeSymbol.asClass.isCaseClass
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
      trait SelectorImpl extends Selector {
        type R = ${rootType}
        ..$defs
      }
      Query[SelectorImpl](new SelectorImpl {}, $kind)
    """
  }
}
