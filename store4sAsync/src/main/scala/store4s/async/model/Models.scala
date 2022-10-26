package store4s.async.model

case class PartitionId(projectId: String, namespaceId: Option[String])
case class PathElement(kind: String, id: Option[String], name: Option[String])
case class Key(partitionId: PartitionId, path: Seq[PathElement])
case class LatLng(latitude: Double, longitude: Double)
case class ArrayValue(values: Seq[Value])
case class Value(
    excludeFromIndexes: Boolean,
    nullValue: Option[String],
    booleanValue: Option[Boolean],
    integerValue: Option[String],
    doubleValue: Option[Double],
    timestampValue: Option[String],
    keyValue: Option[Key],
    stringValue: Option[String],
    blobValue: Option[String],
    geoPointValue: Option[LatLng],
    entityValue: Option[Entity],
    arrayValue: Option[ArrayValue]
)
case class Entity(key: Key, properties: Map[String, Value])
case class Mutation(
    insert: Option[Entity],
    update: Option[Entity],
    upsert: Option[Entity],
    delete: Option[Key]
)
case class AllocateIdBody(keys: Seq[Key])
case class CommitBody(
    mode: String,
    mutations: Seq[Mutation],
    transaction: Option[String]
)
