name := "store4s"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.propensive" %% "magnolia" % "0.16.0",
  "com.google.cloud" % "google-cloud-datastore" % "1.105.9",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
)

organization := "net.pishen"
licenses += "Apache-2.0" -> url(
  "https://www.apache.org/licenses/LICENSE-2.0.html"
)
homepage := Some(url("https://github.com/pishen/store4s"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/pishen/store4s"),
    "scm:git@github.com:pishen/store4s.git"
  )
)
developers := List(
  Developer(
    id = "pishen",
    name = "Pishen Tsai",
    email = "pishen02@gmail.com",
    url = url("https://github.com/pishen")
  )
)
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}
