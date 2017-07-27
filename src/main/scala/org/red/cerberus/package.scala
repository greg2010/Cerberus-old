package org.red

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._
import scala.language.postfixOps


package object cerberus {
  val config: Config = ConfigFactory.load()
  val cerberusConfig: Config = config.getConfig("cerberus")

  object Implicits {
    implicit val system: ActorSystem = ActorSystem("cerberus", config.getConfig("akka"))
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = Timeout(2 seconds)
  }

}
