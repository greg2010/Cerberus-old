package org.red.cerberus

import org.red.cerberus.Implicits._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.osinka.i18n.Lang
import com.typesafe.scalalogging.LazyLogging
import moe.pizza.eveapi.ApiKey
import org.matthicks.mailgun.EmailAddress
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import org.red.cerberus.controllers._
import org.red.cerberus.daemons.{ScheduleDaemon, TeamspeakDaemon}
import org.red.cerberus.endpoints.Base
import org.red.cerberus.external.auth.EveApiClient
import org.red.cerberus.util.{CredentialsType, LegacyCredentials}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.{Source, StdIn}


object Server extends App with LazyLogging with Base {

  lazy val emailController: EmailController = new EmailController(cerberusConfig, userController)

  lazy val eveApiClient: EveApiClient = new EveApiClient(cerberusConfig)
  lazy val permissionController: PermissionController = new PermissionController
  lazy val authorizationController: AuthorizationController = new AuthorizationController(permissionController)
  lazy val userController: UserController = new UserController(permissionController, emailController, eveApiClient)

  lazy val teamspeakController = new TeamspeakDaemon(cerberusConfig, userController)
  lazy val scheduleController = new ScheduleDaemon(cerberusConfig, userController, eveApiClient)

  teamspeakController.initialize()
  scheduleController.initialize()
  teamspeakController.createRegistrationAttempt(1)

  val route: Route = this.baseRoute(authorizationController, userController, eveApiClient)

  val server = Http().bindAndHandle(route, "0.0.0.0", 8080)
  logger.debug(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  server
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
