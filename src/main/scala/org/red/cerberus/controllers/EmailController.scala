package org.red.cerberus.controllers

import com.osinka.i18n.{Lang, Messages}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.matthicks.mailgun.{EmailAddress, Mailgun, Message, MessageResponse}
import org.red.cerberus.exceptions.ResourceNotFoundException
import slick.jdbc.PostgresProfile.api._
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import scala.concurrent.duration._
import com.gilt.gfc.concurrent.ScalaFutures._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


class EmailController(config: Config)(implicit dbAgent: JdbcBackend.Database) extends LazyLogging {
  private val mailer = new Mailgun(config.getString("mailer.domain"), config.getString("mailer.apiKey"))
  private val defaultEmailSender = EmailAddress(
    config.getString("mailer.defaultSenderEmail"),
    config.getString("mailer.defaultSenderAlias")
  )

  def send(msgType: String)(to: EmailAddress, subject: String, body: String, from: EmailAddress = defaultEmailSender): Future[MessageResponse] = {
    logger.debug(s"Sending email from=${from.toString} to=${to.toString} subject=$subject body=$body event=email.send")

    val f = retryWithExponentialDelay(
      maxRetryTimes = 3,
      initialDelay = 100.millis,
      maxDelay = 1.second,
      exponentFactor = 2) {
      mailer.send(Message.simple(from, to, subject, text = body))
    }

    f.onComplete {emailSendCallback(from, to, subject, msgType)}
    f
  }

  def emailSendCallback(from: EmailAddress,
                        to: EmailAddress,
                        subject: String,
                        msgType: String)
                       (maybeResult: Try[MessageResponse]): Unit = {
    maybeResult match {
      case Success(response) =>
        logger.info(s"Successfully sent email " +
          s"from=${from.toString} " +
          s"to=${to.toString} " +
          s"subject=$subject " +
          s"messageType=$msgType " +
          s"mailgunMessageId=${response.id} " +
          s"event=email.send.success")
      case Failure(ex) =>
        logger.error("Failed to send email " +
          s"from=${from.toString} " +
          s"to=${to.toString} " +
          s"subject=$subject " +
          s"messageType=$msgType " +
          s"event=email.send.failure", ex)
    }
  }

  def sendPasswordResetEmail(userId: Int, token: String): Future[MessageResponse] = {
    val f = dbAgent.run(Coalition.Users.filter(_.id === userId).result)
      .flatMap { _.headOption match {
        case Some(res) =>
          implicit val lang: Lang = Lang(res.languageCode)
          val dest = EmailAddress (res.email, res.name)
          val subject = Messages ("email.reset.subject", config.getString ("mailer.defaultSenderAlias"))

          val link = "https://" + config.getString ("mailer.defaultResetDomain") + "/" + token
          val body = Messages ("email.reset.body", res.name, config.getString ("mailer.defaultSenderAlias"), link)
          this.send("reset")(dest, subject, body)
        case None => Future.failed(ResourceNotFoundException(s"No user with id $userId exists"))
      }}
    f.onComplete {
      case Success(r) =>
        logger.info(s"Successfully sent password reset email for userId=$userId event=email.reset.sent")
      case Failure(ex) =>
      logger.info(s"Failed to send password reset email for userId=$userId event=email.reset.failure", ex)
    }
    f
  }

  def sendPasswordChangeEmail(userId: Int): Future[MessageResponse] = {
    val f = dbAgent.run(Coalition.Users.filter(_.id === userId).result)
      .flatMap {_.headOption match {
        case Some(res) =>
          implicit val lang: Lang = Lang(res.languageCode)
          val dest = EmailAddress(res.email, res.name)
          val subject = Messages("email.passwordChange.subject", config.getString("mailer.defaultSenderAlias"))
          val body = Messages("email.passwordChange.body", res.name, config.getString("mailer.defaultSenderAlias"))
          this.send("change")(dest, subject, body)
        case None => Future.failed(ResourceNotFoundException(s"No user with id $userId exists"))
      }}

    f.onComplete {
      case Success(r) =>
        logger.info(s"Successfully sent password change email for userId=$userId event=email.change.sent")
      case Failure(ex) =>
        logger.info(s"Failed to send password change email for userId=$userId event=email.change.failure", ex)
    }
    f
  }
}
