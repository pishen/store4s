package store4s.async

object ReadConsistency extends Enumeration {
  implicit val STRONG = Value
  val EVENTUAL = Value
}
