package org.red.cerberus

import org.red.cerberus.Implicits._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.osinka.i18n.Lang
import com.typesafe.scalalogging.LazyLogging
import org.matthicks.mailgun.EmailAddress
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import org.red.cerberus.controllers._
import org.red.cerberus.endpoints.Base
import org.red.cerberus.external.auth.EveApiClient

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.{Source, StdIn}


object Server extends App with LazyLogging with Base {

  lazy val emailController = new EmailController(cerberusConfig)

  lazy val eveApiClient = new EveApiClient(cerberusConfig)
  lazy val permissionController = new PermissionController
  lazy val authorizationController = new AuthorizationController(permissionController)
  lazy val userController = new UserController(permissionController, emailController, eveApiClient)

  val quartzScheduler: Scheduler = new StdSchedulerFactory().getScheduler
  quartzScheduler.start()
  val scheduleController = new ScheduleController(quartzScheduler, cerberusConfig, userController)


  val route: Route = this.baseRoute(authorizationController, userController, eveApiClient)

  val server = Http().bindAndHandle(route, "0.0.0.0", 8080)
  logger.debug(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  server
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
