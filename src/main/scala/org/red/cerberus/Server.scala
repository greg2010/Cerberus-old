package org.red.cerberus

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.Implicits._
import org.red.cerberus.http.endpoints.Base
import org.red.iris.finagle.clients.UserClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn


object Server extends App with LazyLogging with Base {

  val userClient = new UserClient(config)

  val route: Route = this.baseRoute(userClient)

  val server = Http().bindAndHandle(route, cerberusConfig.getString("host"), cerberusConfig.getInt("port"))
  logger.debug(s"Server online at http://${cerberusConfig.getString("host")}:${cerberusConfig.getInt("port")}/\n" +
    s"Press RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  server
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
