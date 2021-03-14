name := "scalastore"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % "2.3.3",
  "com.google.cloud" % "google-cloud-datastore" % "1.105.9"
)
