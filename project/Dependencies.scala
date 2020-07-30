import sbt._

object Dependencies {
  private object Version {
    val akka = "2.6.7"
    val akkaHttp = "10.1.12"
    val flyway = "6.2.4"
    val scalaTest = "3.1.0"
    val logbackVersion = "1.2.3"
    val slick = "3.3.2"
    val postgres = "42.2.10"
    val h2 = "1.4.192"
    val slick_pg = "0.18.1"
  }
  private val utils: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % Version.logbackVersion,
    "org.flywaydb" % "flyway-core" % Version.flyway,
    "org.scalatest" %% "scalatest" % Version.scalaTest
  )

  private val akka: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % Version.akka,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % Version.akka % Test,
    "com.typesafe.akka" %% "akka-http" % Version.akkaHttp,
    "com.typesafe.akka" %% "akka-stream" % Version.akka,
    "com.typesafe.akka" %% "akka-http-spray-json" % Version.akkaHttp
  )

  private val slick: Seq[ModuleID] = Seq(
    "com.typesafe.slick" %% "slick" % Version.slick,
    "com.typesafe.slick" %% "slick-hikaricp" % Version.slick,
    "com.github.tminglei" %% "slick-pg" % Version.slick_pg,
    "com.github.tminglei" %% "slick-pg_play-json" % Version.slick_pg,
    "org.postgresql" % "postgresql" % Version.postgres,
    "com.h2database" % "h2" % Version.h2 % "test"
  )

  val dependencies = Dependencies.akka ++ Dependencies.slick ++ Dependencies.utils
}
