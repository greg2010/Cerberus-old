package org.red.cerberus

import org.red.cerberus.Implicits._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import org.red.cerberus.controllers.{AuthorizationController, PermissionController, ScheduleController, UserController}
import org.red.cerberus.endpoints.Base

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.{Source, StdIn}


object Server extends App with LazyLogging with Base {

  val permissionController = new PermissionController
  val authorizationController = new AuthorizationController(permissionController)
  val userController = new UserController(permissionController)

  val quartzScheduler: Scheduler = new StdSchedulerFactory().getScheduler
  quartzScheduler.start()
  val scheduleController = new ScheduleController(quartzScheduler, cerberusConfig, userController)


  val route: Route = this.baseRoute(authorizationController, userController)

  val server = Http().bindAndHandle(route, "0.0.0.0", 8080)
  logger.debug(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  server
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
