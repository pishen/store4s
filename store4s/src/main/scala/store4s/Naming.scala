package store4s

sealed trait Naming {
  def convert(typeName: String): String
}
case object CamelCase extends Naming {
  def convert(typeName: String) = typeName
}
case object SnakeCase extends Naming {
  def convert(typeName: String) = typeName.zipWithIndex.flatMap {
    case (char, i) =>
      if (i == 0) Seq(char.toLower)
      else if (char.isUpper) Seq('_', char.toLower)
      else Seq(char)
  }.mkString
}
