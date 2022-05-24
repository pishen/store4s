package store4s.v1

import com.google.datastore.v1.CommitRequest
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Key
import com.google.datastore.v1.LookupRequest
import com.google.datastore.v1.Mutation
import com.google.datastore.v1.PartitionId
import com.google.datastore.v1.RunQueryRequest
import com.google.datastore.v1.client.DatastoreFactory
import com.google.datastore.v1.client.DatastoreHelper
import com.google.datastore.v1.client.DatastoreOptions
import com.google.datastore.v1.{Query => GQuery}

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe._

case class Datastore(
    options: DatastoreOptions,
    namespace: Option[String] = None,
    typeIdentifier: String = "_type"
) {
  def projectId = options.getProjectId()
  lazy val underlying = DatastoreFactory.get().create(options)

  def partitionId = {
    Some(PartitionId.newBuilder())
      .map(_.setProjectId(projectId))
      .map(eb => namespace.fold(eb)(eb.setNamespaceId))
      .get
      .build()
  }
  def buildKey[A: WeakTypeTag](pathBuilder: Key.PathElement.Builder) = {
    val typeName = weakTypeOf[A].typeSymbol.name.toString()
    Key
      .newBuilder()
      .setPartitionId(partitionId)
      .addPath(pathBuilder.setKind(typeName))
      .build()
  }
  def buildKey[A: WeakTypeTag](name: String): Key = {
    buildKey[A](Key.PathElement.newBuilder().setName(name))
  }
  def buildKey[A: WeakTypeTag](id: Long): Key = {
    buildKey[A](Key.PathElement.newBuilder().setId(id))
  }

  def commit(mutations: Mutation.Builder*) = {
    underlying.commit(
      CommitRequest
        .newBuilder()
        .addAllMutations(mutations.map(_.build()).asJava)
        // need beginTransaction() if set to TRANSACTIONAL
        .setMode(CommitRequest.Mode.NON_TRANSACTIONAL)
        .build()
    )
  }

  def lookup(keys: Key*) = {
    underlying.lookup(
      LookupRequest
        .newBuilder()
        .addAllKeys(keys.asJava)
        .build()
    )
  }

  def add(entities: Entity*) =
    commit(entities.map(e => Mutation.newBuilder().setInsert(e)): _*)
  def put(entities: Entity*) =
    commit(entities.map(e => Mutation.newBuilder().setUpsert(e)): _*)
  def get(key: Key) =
    lookup(key).getFoundList().asScala.headOption.map(_.getEntity())
  def get(keys: Seq[Key]) =
    lookup(keys: _*).getFoundList().asScala.map(_.getEntity()).toSeq
  def getEither[A: WeakTypeTag: EntityDecoder](name: String) = {
    get(buildKey[A](name)).map(decodeEntity[A])
  }
  def getEither[A: WeakTypeTag: EntityDecoder](id: Long) = {
    get(buildKey[A](id)).map(decodeEntity[A])
  }
  def getEithersByName[A: WeakTypeTag: EntityDecoder](names: Seq[String]) = {
    get(names.map(name => buildKey[A](name))).map(decodeEntity[A])
  }
  def getEithersById[A: WeakTypeTag: EntityDecoder](ids: Seq[Long]) = {
    get(ids.map(id => buildKey[A](id))).map(decodeEntity[A])
  }
  def getRight[A: WeakTypeTag: EntityDecoder](name: String) = {
    getEither[A](name).map(_.toTry.get)
  }
  def getRight[A: WeakTypeTag: EntityDecoder](id: Long) = {
    getEither[A](id).map(_.toTry.get)
  }
  def getRightsByName[A: WeakTypeTag: EntityDecoder](names: Seq[String]) = {
    getEithersByName[A](names).map(_.toTry.get)
  }
  def getRightsById[A: WeakTypeTag: EntityDecoder](ids: Seq[Long]) = {
    getEithersById[A](ids).map(_.toTry.get)
  }
  def delete(keys: Key*) = {
    commit(keys.map(k => Mutation.newBuilder().setDelete(k)): _*)
  }
  def update(entities: Entity*) = {
    commit(entities.map(e => Mutation.newBuilder().setUpdate(e)): _*)
  }
  def run(query: GQuery) = {
    underlying.runQuery(
      RunQueryRequest
        .newBuilder()
        .setProjectId(projectId)
        .setPartitionId(partitionId)
        .setQuery(query)
        .build()
    )
  }
}

object Datastore {
  // need to set DATASTORE_PROJECT_ID environment variable if not running on Compute Engine
  def defaultInstance = Datastore(DatastoreHelper.getOptionsFromEnv().build())
}
