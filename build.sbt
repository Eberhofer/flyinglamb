name := "flyinglamb"

libraryDependencies ++= Dependencies.dependencies

enablePlugins(FlywayPlugin)

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.eberhofer"
ThisBuild / organizationName := "eberhofer"

flywayUrl := "jdbc:postgresql://localhost:5432/flyinglamb"
flywayUser := "postgres"
flywayPassword := "postgres"
flywayLocations += "filesystem:src/main/resources/db/migration"
flywayUrl in Test := "jdbc:hsqldb:file:target/flyinglamb;shutdown=true"
flywayUser in Test := "SA"
flywayPassword in Test := ""

lazy val root = (project in file("."))
  .settings(
    name := "flyingLamb"
)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.

