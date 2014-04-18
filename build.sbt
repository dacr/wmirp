import AssemblyKeys._

seq(assemblySettings: _*)

name := "wmirp"

version := "0.1"

scalaVersion := "2.10.4"

mainClass in assembly := Some("fr.janalyse.wmirp.Main")

jarName in assembly := "wmirp.jar"

libraryDependencies ++= Dependencies.akka

classpathTypes ++= Set("jnilib", "dll")
