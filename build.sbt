name := "BatchService"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "io.reactivex" %% "rxscala" % "0.25.1",
  "commons-io" % "commons-io" % "2.4",
  "com.github.tototoshi" %% "scala-csv" % "1.2.2",
  "com.h2database" % "h2" % "1.4.190",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.slf4j" % "slf4j-log4j12" % "1.7.13"
)