package org.red.cerberus.jobs.quartz

import com.typesafe.scalalogging.LazyLogging
import org.quartz.{Job, JobExecutionContext}
import org.red.cerberus.controllers.TeamspeakController
import org.red.cerberus.exceptions.{ExceptionHandlers, ResourceNotFoundException}
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class TeamspeakJob extends Job with LazyLogging {
  override def execute(context: JobExecutionContext): Unit = {
    try {
      val dbAgent = context.getScheduler.getContext.get("dbAgent").asInstanceOf[JdbcBackend.Database]
      val teamspeakController = context.getScheduler.getContext.get("teamspeakController").asInstanceOf[TeamspeakController]
      implicit val ec = context.getScheduler.getContext.get("ec").asInstanceOf[ExecutionContext]
      val uniqueId = context.getMergedJobDataMap.getString("uniqueId")

      teamspeakController.getUserIdByUniqueId(uniqueId)
        .transformWith {
          case Success(userId) =>
            teamspeakController.syncTeamspeakUser(userId)
          case Failure(ex: ResourceNotFoundException) =>
            teamspeakController.syncTeamspeakUser(uniqueId, Seq())
          case Failure(ex) =>
            Future.failed(ex)
        }.recover {
        case ex: RuntimeException =>
          logger.warn(s"Teamspeak user with uniqueId8=${uniqueId.substring(8)} doesn't exist, removing from the schedule " +
            s"event=teamspeak.sync.remove")
          context.getScheduler.deleteJob(context.getJobDetail.getKey)
      }.onComplete {
        case Success(_) =>
          logger.info(s"Synced permissions for uniqueId8=${uniqueId.substring(8)} event=teamspeak.sync.success")
        case Failure(ex) =>
          logger.error("Failed to sync permissions event=teamspeak.sync.failure", ex)
      }
    } catch {
      ExceptionHandlers.jobExceptionHandler
    }
  }
}
