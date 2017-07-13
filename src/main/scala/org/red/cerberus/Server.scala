package org.red.cerberus

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.Implicits._
import org.red.cerberus.http.AuthenticationHandler
import org.red.cerberus.http.endpoints.Base
import org.red.iris.finagle.clients.{PermissionClient, TeamspeakClient, UserClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn


object Server extends App with LazyLogging with Base {

  val userClient = new UserClient(config)
  val permissionClient = new PermissionClient(config)
  val teamspeakClient = new TeamspeakClient(config)

  val authenticationHandler = new AuthenticationHandler(permissionClient)

  val route = this.baseRoute(userClient, teamspeakClient, authenticationHandler) _
  val server = Http().bind(cerberusConfig.getString("host"), cerberusConfig.getInt("port"))
    .runWith(Sink foreach { conn =>
      val address = conn.remoteAddress

      conn.handleWith(route(address))
    })
  logger.debug(s"Server online at http://${cerberusConfig.getString("host")}:${cerberusConfig.getInt("port")}/\n" +
    s"Press RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  server
    .onComplete(_ => system.terminate()) // and shutdown when done
}
