import org.typelevel.scalacoptions.ScalacOptions

name := "store4s"

ThisBuild / scalaVersion := "2.13.14"
ThisBuild / tpolecatExcludeOptions ++= Set(
  ScalacOptions.warnNonUnitStatement,
  ScalacOptions.warnNumericWiden
)

val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.3.7",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.typelevel" %% "cats-core" % "2.6.1",
    "org.scalamock" %% "scalamock" % "5.1.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.7" % Test
  )
)

lazy val store4s = project
  .settings(
    commonSettings,
    name := "store4s",
    libraryDependencies ++= Seq(
      "com.google.cloud" % "google-cloud-datastore" % "1.105.9"
    )
  )

lazy val store4sV1 = project
  .settings(
    commonSettings,
    name := "store4s-v1",
    libraryDependencies ++= Seq(
      "com.google.cloud.datastore" % "datastore-v1-proto-client" % "2.0.1"
    )
  )

lazy val store4sSttp = project
  .settings(
    commonSettings,
    name := "store4s-sttp",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core" % "3.8.15",
      "com.google.auth" % "google-auth-library-oauth2-http" % "1.19.0",
      "io.circe" %% "circe-generic" % "0.14.1" % Test,
      "com.softwaremill.sttp.client3" %% "circe" % "3.8.15" % Test
    )
  )

lazy val store4sSttpCirce = project
  .settings(
    commonSettings,
    name := "store4s-sttp-circe",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % "0.14.1",
      "com.softwaremill.sttp.client3" %% "circe" % "3.5.2"
    )
  )
  .dependsOn(store4sSttp)

//prevent root project from running these tasks
publish / skip := true

ThisBuild / organization := "net.pishen"
ThisBuild / licenses += "Apache-2.0" -> url(
  "https://www.apache.org/licenses/LICENSE-2.0.html"
)
ThisBuild / homepage := Some(url("https://github.com/pishen/store4s"))
ThisBuild / developers := List(
  Developer(
    id = "pishen",
    name = "Pishen Tsai",
    email = "",
    url = url("https://github.com/pishen")
  )
)
// will cause warning when publishing if not set
ThisBuild / versionScheme := Some("early-semver")
