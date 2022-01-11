package store4s.v1

case class Datastore(
    projectId: String,
    namespace: Option[String] = None,
    typeIdentifier: String = "_type"
)
