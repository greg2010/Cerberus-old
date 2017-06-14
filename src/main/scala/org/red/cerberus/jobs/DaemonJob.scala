package org.red.cerberus.jobs

import com.typesafe.scalalogging.LazyLogging
import org.quartz._
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._
import org.red.cerberus.controllers.ScheduleController
import scala.concurrent.ExecutionContext.Implicits.global

class DaemonJob extends Job with LazyLogging {
  override def execute(context: JobExecutionContext): Unit = {
    try {
      val dbAgent = context.getScheduler.getContext.get("dbAgent").asInstanceOf[JdbcBackend.Database]
      val scheduleController = context.getScheduler.getContext.get("scheduleController").asInstanceOf[ScheduleController]
      val f = dbAgent.run(Coalition.EveApi.result).map(_.foreach(scheduleController.scheduleUserUpdate))
    } catch {
      case ex: ClassCastException =>
        logger.error("Failed to instantiate one or more classes event=user.schedule.failure", ex)
    }
  }
}
