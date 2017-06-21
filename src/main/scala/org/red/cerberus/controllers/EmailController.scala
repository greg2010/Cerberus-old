package org.red.cerberus.controllers

import com.osinka.i18n.{Lang, Messages}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.matthicks.mailgun.{EmailAddress, Mailgun, Message, MessageResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


class EmailController(config: Config) extends LazyLogging {
  private val mailer = new Mailgun(config.getString("mailer.domain"), config.getString("mailer.apiKey"))
  private val defaultEmailSender = EmailAddress(
    config.getString("mailer.defaultSenderEmail"),
    config.getString("mailer.defaultSenderAlias")
  )

  def send(to: EmailAddress, subject: String, body: String, from: EmailAddress = defaultEmailSender): Future[MessageResponse] = {
    // TODO: add retry logic
    logger.debug(s"Sending email from=${from.toString} to=${to.toString} subject=$subject body=$body event=email.send")
    val f = mailer.send(Message.simple(from, to, subject, text = body))
    f.onComplete {
      case Success(response) =>
        logger.info(s"Successfully sent email " +
          s"from=${from.toString} " +
          s"to=${to.toString} " +
          s"subject=$subject " +
          s"mailgunMessageId=${response.id} " +
          s"event=email.send.success")
      case Failure(ex) =>
        logger.error("Failed to send email " +
          s"from=${from.toString} " +
          s"to=${to.toString} " +
          s"subject=$subject " +
          s"event=email.send.failure", ex)
    }
    f
  }

  def sendPasswordResetEmail(name: String, email: String, token: String)
                            (implicit lang: Lang = Lang("en")): Future[MessageResponse] = {
    val dest = EmailAddress(email, name)
    val subject = Messages("email.reset.subject", config.getString("mailer.defaultSenderAlias"))

    val link = "https://" + config.getString("mailer.defaultResetDomain") +  "/" + token
    val body = Messages("email.reset.body", name, config.getString("mailer.defaultSenderAlias"), link)
    this.send(dest, subject, body)
  }
}
