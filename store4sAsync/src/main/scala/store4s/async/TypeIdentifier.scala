package store4s.async

case class TypeIdentifier(fieldName: String)

object TypeIdentifier {
  implicit val defaultTypeIdentifier = TypeIdentifier("_type")
}
