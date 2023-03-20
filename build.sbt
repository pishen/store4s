name := "store4s"

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / crossScalaVersions := Seq("2.13.10", "2.12.13")

val commonSettings = Seq(
  scalacOptions ++= {
    val common = Seq(
      "-feature",
      "-deprecation",
      "-language:higherKinds",
      "-Ywarn-unused:implicits",
      "-Ywarn-unused:imports",
      "-Ywarn-unused:locals",
      "-Ywarn-unused:params",
      "-Ywarn-unused:patvars",
      "-Ywarn-unused:privates",
      //https://stackoverflow.com/questions/56351793
      "-Ywarn-macros:after"
    )
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => common :+ "-Ypartial-unification" // for cats
      case _             => common
    }
  },
  Compile / console / scalacOptions --= Seq("-Ywarn-unused:imports"),
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.3.7",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.9.0",
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
      "com.softwaremill.sttp.client3" %% "core" % "3.5.2",
      "com.google.auth" % "google-auth-library-oauth2-http" % "1.12.0",
      "io.circe" %% "circe-generic" % "0.14.1" % Test,
      "com.softwaremill.sttp.client3" %% "circe" % "3.5.2" % Test
    )
  )

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
