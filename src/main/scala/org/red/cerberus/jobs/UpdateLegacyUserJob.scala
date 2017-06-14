package org.red.cerberus.jobs

import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging
import moe.pizza.eveapi.ApiKey
import org.quartz.{Job, JobExecutionContext}
import org.red.cerberus.controllers.{ScheduleController, UserController}
import org.red.cerberus.external.auth.LegacyCredentials
import slick.jdbc.JdbcBackend


class UpdateLegacyUserJob extends Job with LazyLogging {
  override def execute(context: JobExecutionContext): Unit = {
    try {
      val dbAgent = context.getScheduler.getContext.get("dbAgent").asInstanceOf[JdbcBackend.Database]
      val scheduleController = context.getScheduler.getContext.get("scheduleController").asInstanceOf[ScheduleController]
      val userController = context.getScheduler.getContext.get("userController").asInstanceOf[UserController]
      val userId = context.getMergedJobDataMap.getLong("userId")
      val characterId = context.getMergedJobDataMap.getLong("characterId")
      val characterName = context.getMergedJobDataMap.getString("characterName")
      val keyId = context.getMergedJobDataMap.getLong("keyId")
      val vCode = context.getMergedJobDataMap.getString("vCode")
      val legacyCredentials = LegacyCredentials(ApiKey(keyId.toInt, vCode), characterName)
      legacyCredentials.fetchUser.flatMap(userController.updateUserData)
    } catch {
      case ex: ClassCastException =>
        logger.error("Failed to instantiate one or more classes event=user.schedule.failure", ex)
    }
  }
}
