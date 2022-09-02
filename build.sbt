name := "store4s"

ThisBuild / scalaVersion := "2.13.5"
ThisBuild / crossScalaVersions := Seq("2.13.5", "2.12.13")

ThisBuild / scalacOptions ++= {
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
}

val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.3.7",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.3",
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
