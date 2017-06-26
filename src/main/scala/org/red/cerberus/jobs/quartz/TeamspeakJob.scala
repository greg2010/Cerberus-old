package org.red.cerberus.jobs.quartz

import com.typesafe.scalalogging.LazyLogging
import org.quartz.{Job, JobExecutionContext}
import org.red.cerberus.controllers.{TeamspeakController, UserController}
import org.red.cerberus.exceptions.{ExceptionHandlers, ResourceNotFoundException}
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class TeamspeakJob extends Job with LazyLogging {
  override def execute(context: JobExecutionContext): Unit = {
    try {
      // Warning: this job requires O(n) queries to teamspeak server, where n is number of users ever joined the server
      val dbAgent = context.getScheduler.getContext.get("dbAgent").asInstanceOf[JdbcBackend.Database]
      val teamspeakController = context.getScheduler.getContext.get("teamspeakController").asInstanceOf[TeamspeakController]
      implicit val ec = context.getScheduler.getContext.get("ec").asInstanceOf[ExecutionContext]

      teamspeakController.getAllClients.flatMap { clients =>
        Future.sequence {
          clients.map { c =>
            teamspeakController.getUserIdByUniqueId(c.getUniqueIdentifier)
              .transformWith {
                case Success(userId) =>
                  teamspeakController.syncTeamspeakUser(userId)
                case Failure(ex: ResourceNotFoundException) =>
                  teamspeakController.syncTeamspeakUser(c.getUniqueIdentifier, Seq())
                case Failure(ex) =>
                  Future.failed(ex)
              }
          }
        }
      }.onComplete {
        case Success(r) =>
          logger.info(s"Synced permissions of affected=${r.length} event=teamspeak.sync.success")
        case Failure(ex) =>
          logger.error("Failed to sync permissions event=teamspeak.sync.failure", ex)
      }

    } catch {
      ExceptionHandlers.jobExceptionHandler
    }
  }
}
