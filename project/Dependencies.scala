import sbt._

object Dependencies {
  val akkaREL         = "2.3.1"
  val logbackREL      = "1.1.2"
  val sprayREL        = "1.3.1"
  val scalatestREL    = "1.9.1"
  val scalaloggingREL = "1.0.1"

  val akka=List(
    "com.typesafe.akka" %% "akka-actor"         % akkaREL,
    "com.typesafe.akka" %% "akka-slf4j"         % akkaREL,
    "ch.qos.logback"     % "logback-classic"    % logbackREL,
    "io.spray"           % "spray-can"          % sprayREL,
    "com.typesafe"      %% "scalalogging-slf4j" % scalaloggingREL,
    "org.scalatest"     %% "scalatest"          % scalatestREL % "test"
  )
}
