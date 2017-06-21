package org.red.cerberus.jobs

import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging
import moe.pizza.eveapi.ApiKey
import org.quartz.{Job, JobExecutionContext}
import org.red.cerberus.controllers.{ScheduleController, UserController}
import org.red.cerberus.exceptions.ExceptionHandlers
import org.red.cerberus.external.auth.{EveApiClient, LegacyCredentials}
import slick.jdbc.JdbcBackend


class UpdateLegacyUserJob extends AbstractUserJob {
  override def execute(context: JobExecutionContext): Unit = {
    try {
      val dbAgent = context.getScheduler.getContext.get("dbAgent").asInstanceOf[JdbcBackend.Database]
      val scheduleController = context.getScheduler.getContext.get("scheduleController").asInstanceOf[ScheduleController]
      val userController = context.getScheduler.getContext.get("userController").asInstanceOf[UserController]
      val eveApiClient = context.getScheduler.getContext.get("eveApiClient").asInstanceOf[EveApiClient]
      val userId = context.getMergedJobDataMap.getLong("userId")
      val characterId = context.getMergedJobDataMap.getLong("characterId")
      val characterName = context.getMergedJobDataMap.getString("characterName")
      val keyId = context.getMergedJobDataMap.getLong("keyId")
      val vCode = context.getMergedJobDataMap.getString("vCode")
      val legacyCredentials = LegacyCredentials(ApiKey(keyId.toInt, vCode), characterName)
      eveApiClient.fetchUser(legacyCredentials)
        .flatMap(userController.updateUserData)
        .onComplete(updateUserCallback(userId, characterId, characterName, CredentialsType.Legacy))
    } catch { ExceptionHandlers.jobExceptionHandler }
  }
}
