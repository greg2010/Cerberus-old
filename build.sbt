name := "Cerberus"

organization := "org.red"

version := "1.0"

scalaVersion := "2.12.2"

assemblyJarName in assembly := "cerberus.jar"
mainClass in assembly := Some("org.red.cerberus.Server")

val meta = """.*(RSA|DSA)$""".r

// Hax to get .jar to execute
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) =>
    xs.map(_.toLowerCase) match {
      case ("manifest.mf" :: Nil) |
           ("index.list" :: Nil) |
           ("dependencies" :: Nil) |
           ("bckey.dsa" :: Nil) => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  case PathList("reference.conf") | PathList("application.conf") => MergeStrategy.concat
  case PathList(_*) => MergeStrategy.first
}

scalacOptions ++= Seq("-deprecation", "-feature")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++=
  Seq(Resolver.bintrayRepo("andimiller", "maven"),
    "Artifactory Realm" at "http://maven.red.greg2010.me/artifactory/sbt-local/")

val circeVersion = "0.8.0"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.6",
  "ch.megard" %% "akka-http-cors" % "0.2.1",
  "de.heikoseeberger" %% "akka-http-circe" % "1.16.0",
  "com.typesafe" % "config" % "1.3.1",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.github.pukkaone" % "logback-gelf" % "1.1.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "com.pauldijou" %% "jwt-circe" % "0.12.1",
  "io.lemonlabs" %% "scala-uri" % "0.4.16",
  "org.red" %% "iris" % "0.0.11-SNAPSHOT",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion)
