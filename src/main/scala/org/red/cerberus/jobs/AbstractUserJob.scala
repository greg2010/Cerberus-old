package org.red.cerberus.jobs

import com.typesafe.scalalogging.LazyLogging
import org.quartz.Job

import scala.util.{Failure, Success, Try}


abstract class AbstractUserJob extends Job with LazyLogging {

  protected object CredentialsType extends Enumeration {
    type CredentialsType = Value
    val Legacy = Value("Legacy")
    val SSO = Value("SSO")
  }

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
}
