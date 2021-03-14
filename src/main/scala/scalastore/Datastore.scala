package scalastore

import com.google.cloud.datastore
import com.google.cloud.datastore.DatastoreOptions

case class Datastore(underlying: datastore.Datastore) {
  def keyFactory = underlying.newKeyFactory()
  def add() = {}
  def put() = {}
  def get() = {}
  def delete() = {}
  def run() = {}
}

object Datastore {
  def defaultInstance() = {
    Datastore(DatastoreOptions.getDefaultInstance().getService())
  }

  def apply(options: DatastoreOptions): Datastore = {
    Datastore(options.getService())
  }
}
