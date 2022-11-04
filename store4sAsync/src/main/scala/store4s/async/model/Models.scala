package store4s.async.model

case class PartitionId(projectId: String, namespaceId: Option[String])
case class PathElement(kind: String, id: Option[String], name: Option[String])
case class Key(partitionId: PartitionId, path: Seq[PathElement])
case class ReadWrite(previousTransaction: Option[String])
case class ReadOnly()
case class TransactionOptions(
    readWrite: Option[ReadWrite],
    readOnly: Option[ReadOnly]
)
case class LatLng(latitude: Double, longitude: Double)
case class ArrayValue(values: Seq[Value])
case class Value(
    excludeFromIndexes: Boolean,
    nullValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[String] = None,
    doubleValue: Option[Double] = None,
    timestampValue: Option[String] = None,
    keyValue: Option[Key] = None,
    stringValue: Option[String] = None,
    blobValue: Option[String] = None,
    geoPointValue: Option[LatLng] = None,
    entityValue: Option[Entity] = None,
    arrayValue: Option[ArrayValue] = None
)
case class Entity(key: Option[Key], properties: Map[String, Value])
case class Mutation(
    insert: Option[Entity] = None,
    update: Option[Entity] = None,
    upsert: Option[Entity] = None,
    delete: Option[Key] = None
)
case class MutationResult(key: Option[Key], version: String)
case class ReadOptions(
    readConsistency: Option[String],
    transaction: Option[String]
)
case class EntityResult(entity: Entity, cursor: Option[String])
case class KindExpression(name: String)
case class CompositeFilter(op: String, filters: Seq[Filter])
case class PropertyReference(name: String)
case class PropertyFilter(
    property: PropertyReference,
    op: String,
    value: Value
)
case class Filter(
    compositeFilter: Option[CompositeFilter] = None,
    propertyFilter: Option[PropertyFilter] = None
)
case class PropertyOrder(property: PropertyReference, direction: String)
case class Query(
    kind: Seq[KindExpression],
    filter: Option[Filter] = None,
    order: Option[Seq[PropertyOrder]] = None,
    distinctOn: Option[Seq[PropertyReference]] = None,
    startCursor: Option[String] = None,
    endCursor: Option[String] = None,
    offset: Option[Int] = None,
    limit: Option[Int] = None
)
case class QueryResultBatch(
    skippedResults: Option[Int],
    skippedCursor: Option[String],
    entityResultType: String,
    entityResults: Option[Seq[EntityResult]],
    endCursor: String,
    moreResults: String
)
case class AllocateIdBody(keys: Seq[Key])
case class BeginTransactionRequest(transactionOptions: TransactionOptions)
case class BeginTransactionResponse(transaction: String)
case class CommitRequest(
    mode: String,
    mutations: Seq[Mutation],
    transaction: Option[String]
)
case class CommitResponse(
    mutationResults: Seq[MutationResult],
    indexUpdates: Int
)
case class LookupRequest(
    readOptions: ReadOptions,
    keys: Seq[Key]
)
case class LookupResponse(
    found: Option[Seq[EntityResult]],
    missing: Option[Seq[EntityResult]],
    deferred: Option[Seq[Key]]
)
case class RollbackRequest(transaction: String)
case class RunQueryRequest(
    partitionId: PartitionId,
    readOptions: ReadOptions,
    query: Query
)
case class RunQueryResponse(batch: QueryResultBatch)
