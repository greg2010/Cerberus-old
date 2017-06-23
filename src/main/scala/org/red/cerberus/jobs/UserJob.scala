package org.red.cerberus.jobs

import com.typesafe.scalalogging.LazyLogging
import moe.pizza.eveapi.ApiKey
import org.quartz.{Job, JobExecutionContext}
import org.red.cerberus.controllers.UserController
import org.red.cerberus.daemons.ScheduleDaemon
import org.red.cerberus.exceptions.ExceptionHandlers
import org.red.cerberus.external.auth.EveApiClient
import org.red.cerberus.util.{Credentials, CredentialsType, LegacyCredentials, SSOCredentials}
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class UserJob extends Job with LazyLogging {

  def updateUserCallback
  (userId: Long, characterId: Long, characterName: String, credsType: CredentialsType.CredentialsType)
  (t: Try[Unit]): Unit = t match {
    case Success(_) =>
      logger.info(s"Updated user " +
        s"userId=$userId " +
        s"characterId=$characterId " +
        s"characterName=$characterName " +
        s"type=${credsType.toString} " +
        s"event=user.update.success")
    case Failure(ex) =>
      logger.error("Failed to update user " +
        s"userId=$userId " +
        s"characterId=$characterId " +
        s"characterName=$characterName " +
        s"type=${credsType.toString} " +
        s"event=user.update.failure", ex)
  }

  override def execute(context: JobExecutionContext): Unit = {
    try {
      implicit val ec = context.getScheduler.getContext.get("ec").asInstanceOf[ExecutionContext]
      val dbAgent = context.getScheduler.getContext.get("dbAgent").asInstanceOf[JdbcBackend.Database]
      val scheduleController = context.getScheduler.getContext.get("scheduleController").asInstanceOf[ScheduleDaemon]
      val userController = context.getScheduler.getContext.get("userController").asInstanceOf[UserController]
      val eveApiClient = context.getScheduler.getContext.get("eveApiClient").asInstanceOf[EveApiClient]
      val userId = context.getMergedJobDataMap.getLong("userId")
      val characterId = context.getMergedJobDataMap.getLong("characterId")
      val characterName = context.getMergedJobDataMap.getString("characterName")
      val credentialsType = CredentialsType.withName(context.getMergedJobDataMap.getString("credentialsType"))

      (credentialsType match {
        case CredentialsType.Legacy =>
          val keyId = context.getMergedJobDataMap.getLong("keyId")
          val vCode = context.getMergedJobDataMap.getString("vCode")
          Future(LegacyCredentials(ApiKey(keyId.toInt, vCode), characterName))
        case CredentialsType.SSO =>
          eveApiClient.fetchCredentials(context.getMergedJobDataMap.getString("ssoToken"))
        case t => Future.failed(new IllegalStateException(s"CredentialsType $t doesn't exist"))
      }).flatMap(eveApiClient.fetchUser)
        .flatMap(userController.updateUserData)
        .onComplete(updateUserCallback(userId, characterId, characterName, credentialsType))
    } catch { ExceptionHandlers.jobExceptionHandler }
  }
}
