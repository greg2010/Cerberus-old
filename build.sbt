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
  case PathList("reference.conf") => MergeStrategy.concat
  case PathList(_*) => MergeStrategy.first
}

scalacOptions ++= Seq("-deprecation", "-feature")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++=
  Seq(Resolver.bintrayRepo("andimiller", "maven"),
    "Artifactory Realm" at "http://maven.red.greg2010.me/artifactory/sbt-local/")

val circeVersion = "0.8.0"
libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-async" % "0.9.6",
  "org.typelevel" %% "cats" % "0.9.0",
  "com.typesafe.akka" %% "akka-http" % "10.0.6",
  "ch.megard" %% "akka-http-cors" % "0.2.1",
  "de.heikoseeberger" %% "akka-http-circe" % "1.16.0",
  "com.typesafe" % "config" % "1.3.1",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "com.pauldijou" %% "jwt-circe" % "0.12.1",
  "com.roundeights" %% "hasher" % "1.2.0",
  "io.lemonlabs" %% "scala-uri" % "0.4.16",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "org.quartz-scheduler" % "quartz" % "2.3.0",
  "net.joelinn" % "quartz-redis-jobstore" % "1.1.8",
  "moe.pizza" %% "eveapi" % "0.58-SNAPSHOT",
  "org.red" %% "reddb" % "1.0.7-SNAPSHOT",
  //"org.red" %% "eveapi-esi" % "1.0.0-SNAPSHOT",
  "net.troja.eve" % "eve-esi" % "1.0.0",
  "org.glassfish.jersey.core" % "jersey-common" % "2.25.1",
  "io.monix" %% "monix" % "2.3.0",
  "org.matthicks" %% "mailgun4s" % "1.0.4",
  "com.osinka.i18n" %% "scala-i18n" % "1.0.2",
  "com.github.theholywaffle" % "teamspeak3-api" % "1.0.14",
  "com.gilt" %% "gfc-concurrent" % "0.3.5",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-yaml" % "0.5.0").map(_.exclude("org.slf4j", "slf4j-simple"))
