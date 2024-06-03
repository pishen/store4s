package store4s.sttp

case class TypeIdentifier(fieldName: String)

object TypeIdentifier {
  implicit val defaultTypeIdentifier: TypeIdentifier = TypeIdentifier("_type")
}
