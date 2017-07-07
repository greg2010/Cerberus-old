package org.red

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration._
import scala.language.postfixOps


package object cerberus {
  private val conf: Config = ConfigFactory.load()
  val cerberusConfig: Config = conf.getConfig("cerberus")

  object Implicits {
    implicit val system: ActorSystem = ActorSystem("cerberus", conf.getConfig("akka"))
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = Timeout(2 seconds)
  }

}
