package org.red.cerberus.controllers

import com.gilt.gfc.concurrent.ScalaFutures.FutureOps
import com.github.theholywaffle.teamspeak3.api.CommandFuture
import com.github.theholywaffle.teamspeak3.api.event._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import monix.execution.Scheduler.{global => scheduler}
import org.red.cerberus.daemons.teamspeak.TeamspeakDaemon
import org.red.cerberus.exceptions.{ConflictingEntityException, ExceptionHandlers, ResourceNotFoundException}
import org.red.cerberus.jobs.teamspeak.RegistrationJoinListener
import org.red.cerberus.util.FutureConverters._
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}


class TeamspeakController(config: Config, userController: => UserController)
                         (implicit dbAgent: JdbcBackend.Database, ec: ExecutionContext)
  extends LazyLogging {
  private val daemon = new TeamspeakDaemon(config)
  private val client = daemon.client


  def obtainExpectedTeamspeakName(userId: Int): Future[String] = {
    userController.getUser(userId).map { u =>
      (u.eveUserData.allianceTicker match {
        case Some(n) => s"$n | "
        case None => ""
      }) + s"${u.eveUserData.corporationTicker} | ${u.eveUserData.characterName}"
    }
  }

  def registerUserOnTeamspeak(userId: Int): Future[Unit] = {

    def registerJoinedUser(expectedNickname: String): Future[Unit] = {
      val f = safeTeamspeakQuery(client.getClients)()
        .map { r =>
          r.asScala.find(_.getNickname == expectedNickname) match {
            case Some(user) => user.getUniqueIdentifier
            case None => throw ResourceNotFoundException(s"No user with name $expectedNickname is on teamspeak")
          }
        }
      f.onComplete {
        case Success(res) =>
          logger.info(s"Successfully obtained joined user uniqueId " +
            s"userId=$userId " +
            s"expectedNickname=$expectedNickname " +
            s"event=teamspeak.joined.get.success")
        case Failure(ex: ResourceNotFoundException) =>
          logger.warn("Failed to obtain joined user uniqueId, user isn't joined " +
            s"userId=$userId " +
            s"expectedNickname=$expectedNickname " +
            s"event=teamspeak.joined.get.failure")
        case Failure(ex) =>
          logger.error("Failed to obtain joined user uniqueId " +
            s"userId=$userId " +
            s"expectedNickname=$expectedNickname " +
            s"event=teamspeak.joined.get.failure")
      }
      f.flatMap(uniqueId => registerTeamspeakUser(userId, uniqueId))
    }

    def registerUsingEventListener(expectedNickname: String): Future[Unit] = {
      val p = Promise[String]()
      safeAddListener(new RegistrationJoinListener(client, config, expectedNickname, p))(10.minutes)
        .onComplete {
          case Success(_) =>
            logger.info(s"Successfully registered listener for " +
              s"userId=$userId " +
              s"expectedNickname=$expectedNickname " +
              s"event=teamspeak.listener.create.success")
          case Failure(ex) =>
            logger.error("Failed to register listener for " +
              s"userId=$userId " +
              s"expectedNickname=$expectedNickname " +
              s"event=teamspeak.listener.create.failure")
        }
      p.future.flatMap(uniqueId => registerTeamspeakUser(userId, uniqueId))
    }

    val r = this.obtainExpectedTeamspeakName(userId).flatMap { n =>
      registerJoinedUser(n).fallbackTo(registerUsingEventListener(n))
    }

    r.onComplete {
      case Success(_) =>
        logger.info(s"Register user in teamspeak " +
          s"userId=$userId " +
          s"event=teamspeak.register.success")
      case Failure(ex) =>
        logger.error("Failed to create registration request for " +
          s"userId=$userId " +
          s"event=teamspeak.register.failure", ex)
    }
    r
  }

  def registerTeamspeakUser(userId: Int, uniqueId: String): Future[Unit] = {
    val f = dbAgent.run(Coalition.TeamspeakUsers.filter(_.uniqueId === uniqueId).take(1).result).flatMap { r =>
      r.headOption match {
        case Some(res) => Future.failed(ConflictingEntityException("User with such teamspeak uniqueId already exists", None))
        case None =>
          val q = Coalition.TeamspeakUsers.map(r => (r.userId, r.uniqueId)) += (userId, uniqueId)
          dbAgent.run(q)
      }
    }.recoverWith(ExceptionHandlers.dbExceptionHandler)
    f.onComplete {
      case Success(_) =>
        logger.info(s"Successfully registered user in teamspeak userId=$userId event=teamspeak.register.success")
      case Failure(ex) =>
        logger.error(s"Failed to register user in teamspeak userId=$userId event=teamspeak.register.failure", ex)
    }
    f.map(_ => ())
  }

  private def safeQuery[T](f: => T, timeout: FiniteDuration): Future[T] = {
    val p = Promise[T]()
    val q = scheduler.scheduleWithFixedDelay(0.seconds, 5.seconds) {
      if (daemon.isConnected)
        p.success(f)
    }
    val r = p.future.withTimeout(timeout)
    r.onComplete {
      case Success(res) =>
        q.cancel()
        logger.info(s"Executed teamspeak command commandResultType=${res.getClass.getName} event=teamspeak.execute.success")
      case Failure(ex) =>
        q.cancel()
        logger.error(s"Failed to execute teamspeak command command ${timeout.toString()} event=teamspeak.execute.failure", ex)
    }
    r
  }

  private def safeAddListener(listeners: TS3Listener*)(timeout: FiniteDuration = 1.minute): Future[Unit] = {
    safeQuery(() => client.addTS3Listeners(listeners: _*), timeout)
  }

  private def safeTeamspeakQuery[T](f: => CommandFuture[T])(timeout: FiniteDuration = 1.minute): Future[T] = {
    safeQuery(f, timeout).flatMap(x => commandToScalaFuture(x))
  }
}
