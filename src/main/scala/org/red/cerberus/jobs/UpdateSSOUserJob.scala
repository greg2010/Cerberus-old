package org.red.cerberus.jobs

import scala.concurrent.ExecutionContext.Implicits.global
import org.quartz.{Job, JobExecutionContext}
import org.red.cerberus.controllers.{ScheduleController, UserController}
import org.red.cerberus.exceptions.ExceptionHandlers
import org.red.cerberus.external.auth.EveApiClient
import slick.jdbc.JdbcBackend


class UpdateSSOUserJob extends AbstractUserJob {
  override def execute(context: JobExecutionContext): Unit = {
    try {
      val dbAgent = context.getScheduler.getContext.get("dbAgent").asInstanceOf[JdbcBackend.Database]
      val scheduleController = context.getScheduler.getContext.get("scheduleController").asInstanceOf[ScheduleController]
      val userController = context.getScheduler.getContext.get("userController").asInstanceOf[UserController]
      val userId = context.getMergedJobDataMap.getLong("userId")
      val characterId = context.getMergedJobDataMap.getLong("characterId")
      val characterName = context.getMergedJobDataMap.getString("characterName")
      val eveApiClient = context.getScheduler.getContext.get("eveApiClient").asInstanceOf[EveApiClient]
      val ssoToken = context.getMergedJobDataMap.getString("ssoToken")
      eveApiClient.fetchUserAndCredentials(ssoToken)
        .flatMap(r => userController.updateUserData(r._2))
        .onComplete(updateUserCallback(userId, characterId, characterName, CredentialsType.SSO))
    } catch { ExceptionHandlers.jobExceptionHandler }
  }
}
