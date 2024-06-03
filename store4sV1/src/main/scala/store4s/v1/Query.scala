package store4s.v1

import cats.Id
import cats.implicits._
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Filter
import com.google.datastore.v1.KindExpression
import com.google.datastore.v1.PropertyFilter
import com.google.datastore.v1.PropertyFilter.Operator
import com.google.datastore.v1.PropertyOrder
import com.google.datastore.v1.PropertyOrder.Direction
import com.google.datastore.v1.PropertyReference
import com.google.datastore.v1.RunQueryResponse
import com.google.datastore.v1.{Query => GQuery}
import com.google.protobuf.ByteString
import com.google.protobuf.Int32Value

import scala.jdk.CollectionConverters._
import scala.reflect.macros.whitebox.Context

case class Query[S, T: EntityDecoder](
    kind: String,
    selector: S,
    filters: Seq[Filter] = Seq.empty,
    orders: Seq[PropertyOrder] = Seq.empty,
    limit: Option[Int] = None,
    start: Option[ByteString] = None,
    end: Option[ByteString] = None
) {
  def builder() = {
    GQuery
      .newBuilder()
      .pure[Id]
      .map(_.addKind(KindExpression.newBuilder().setName(kind)))
      .map(b =>
        if (filters.nonEmpty) b.setFilter(filters.reduce(_ && _)) else b
      )
      .map(b => if (orders.nonEmpty) b.addAllOrder(orders.asJava) else b)
      .map(b => limit.fold(b)(i => b.setLimit(Int32Value.of(i))))
      .map(b => start.fold(b)(c => b.setStartCursor(c)))
      .map(b => end.fold(b)(c => b.setEndCursor(c)))
  }
  def build() = builder().build()
  def filter(f: S => Filter) = this.copy(filters = filters :+ f(selector))
  def sortBy(fs: S => PropertyOrder*) =
    this.copy(orders = fs.map(f => f(selector)))
  def take(n: Int) = this.copy(limit = Some(n))
  def startFrom(cursor: ByteString) = this.copy(start = Some(cursor))
  def endAt(cursor: ByteString) = this.copy(end = Some(cursor))
  def run(implicit ds: Datastore) = Query.Result[T](ds.run(build()))
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

    def createOrder(direction: Direction) = PropertyOrder
      .newBuilder()
      .setDirection(direction)
      .setProperty(PropertyReference.newBuilder().setName(name))
      .build()
    def asc: PropertyOrder = createOrder(Direction.ASCENDING)
    def desc: PropertyOrder = createOrder(Direction.DESCENDING)
  }

  case class ArrayProperty[P](p: P) {
    def exists(f: P => Filter): Filter = f(p)
  }

  case class Result[T: EntityDecoder](resp: RunQueryResponse) {
    def getEntities: Seq[Entity] =
      resp.getBatch().getEntityResultsList().asScala.map(_.getEntity()).toSeq
    def getEithers = getEntities.map(decodeEntity[T])
    def getRights = getEithers.map(_.toTry.get)
    def getCursorAfter: ByteString = resp.getBatch().getEndCursor()
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
      Query[Selector, ${rootType}](
        $kind,
        new Selector {}
      )
    """
  }
}
