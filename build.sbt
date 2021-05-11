name := "store4s"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.5"

crossScalaVersions := Seq("2.13.5", "2.12.13")

libraryDependencies ++= Seq(
  "com.propensive" %% "magnolia" % "0.16.0",
  "com.google.cloud" % "google-cloud-datastore" % "1.105.9",
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.3",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
  "org.scalatest" %% "scalatest" % "3.2.7" % Test
)

scalacOptions ++= Seq("-feature", "-deprecation", "-language:higherKinds")

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
