name := "scalastore"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.propensive" %% "magnolia" % "0.16.0",
  "com.google.cloud" % "google-cloud-datastore" % "1.105.9",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
)
