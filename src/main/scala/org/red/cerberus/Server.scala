package org.red.cerberus

import org.red.cerberus.Implicits._
import akka.http.scaladsl.Http
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.controllers.PermissionController
import org.red.cerberus.endpoints.Base

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.{Source, StdIn}


object Server extends App with LazyLogging with Base {
  val server = Http().bindAndHandle(baseRoute, "0.0.0.0", 8080)
  logger.debug(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  server
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
