package store4s.async

import store4s.async.model.CompositeFilter
import store4s.async.model.Filter
import store4s.async.model.KindExpression
import store4s.async.model.PropertyFilter
import store4s.async.model.PropertyOrder
import store4s.async.model.PropertyReference

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

case class Query[S, T](
    selector: S,
    query: model.Query
) {
  def filter(f: S => Filter) = {
    val newFilter = query.filter
      .map(oldFilter =>
        Filter(compositeFilter =
          Some(CompositeFilter("AND", Seq(oldFilter, f(selector))))
        )
      )
      .orElse(Some(f(selector)))
    this.copy(query = query.copy(filter = newFilter))
  }
  def sortBy(fs: S => PropertyOrder*) =
    this.copy(query = query.copy(order = Some(fs.map(f => f(selector)))))
  def startCursor(cursor: String) =
    this.copy(query = query.copy(startCursor = Some(cursor)))
  def endCursor(cursor: String) =
    this.copy(query = query.copy(endCursor = Some(cursor)))
  def drop(n: Int) = {
    require(query.limit.isEmpty, "drop should be applied before take")
    this.copy(query = query.copy(offset = Some(n)))
  }
  def take(n: Int) = this.copy(query = query.copy(limit = Some(n)))
}

object Query {
  case class Property[T](name: String)(implicit enc: ValueEncoder[T]) {
    def propertyFilter(op: String, t: T) = Filter(propertyFilter =
      Some(PropertyFilter(PropertyReference(name), op, enc.encode(t, false)))
    )
    def ==(t: T): Filter = propertyFilter("EQUAL", t)
    def >(t: T): Filter = propertyFilter("GREATER_THAN", t)
    def <(t: T): Filter = propertyFilter("LESS_THAN", t)
    def >=(t: T): Filter = propertyFilter("GREATER_THAN_OR_EQUAL", t)
    def <=(t: T): Filter = propertyFilter("LESS_THAN_OR_EQUAL", t)
    def asc = PropertyOrder(PropertyReference(name), "ASCENDING")
    def desc = PropertyOrder(PropertyReference(name), "DESCENDING")
  }

  case class ArrayProperty[P](p: P) {
    def exists(f: P => Filter): Filter = f(p)
  }

  def apply[S, T](selector: S, kind: String): Query[S, T] =
    Query[S, T](selector, model.Query(Seq(KindExpression(kind))))

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
      trait Selector {
        ..$defs
      }
      Query[Selector, ${rootType}](new Selector {}, $kind)
    """
  }
}
