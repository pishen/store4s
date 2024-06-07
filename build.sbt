import org.typelevel.sbt.tpolecat.DevMode
import org.typelevel.scalacoptions.ScalacOptions
import scalapb.compiler.Version._

name := "store4s"

ThisBuild / scalaVersion := "2.13.14"
ThisBuild / tpolecatExcludeOptions ++= Set(
  ScalacOptions.warnNonUnitStatement,
  ScalacOptions.warnNumericWiden
)
ThisBuild / tpolecatDefaultOptionsMode := DevMode

lazy val store4sRpc = project
  .settings(
    name := "store4s-rpc",
    libraryDependencies ++= Seq(
      "com.google.auth" % "google-auth-library-oauth2-http" % "1.19.0",
      "com.google.api.grpc" % "proto-google-cloud-datastore-v1" % "0.111.0" % "protobuf-src" intransitive (),
      "com.google.api.grpc" % "proto-google-cloud-datastore-v1" % "0.111.0",
      "com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10",
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0",
      "io.grpc" % "grpc-netty" % grpcJavaVersion,
      "io.grpc" % "grpc-auth" % grpcJavaVersion,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scalatest" %% "scalatest" % "3.2.18" % "test"
    ),
    //https://stackoverflow.com/questions/22722449
    tpolecatScalacOptions += ScalacOptions.warnOption(
      "conf:src=src_managed/.*:silent"
    ),
    //https://scalapb.github.io/docs/generated-code#java-conversions
    Compile / PB.targets := Seq(
      scalapb.gen(javaConversions = true) -> (Compile / sourceManaged).value
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
