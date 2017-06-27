package org.red.cerberus.controllers

import com.gilt.gfc.concurrent.ScalaFutures.FutureOps
import com.github.theholywaffle.teamspeak3.api.CommandFuture
import com.github.theholywaffle.teamspeak3.api.event._
import com.github.theholywaffle.teamspeak3.api.wrapper.DatabaseClient
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import monix.execution.Scheduler.{global => scheduler}
import org.red.cerberus.daemons.teamspeak.TeamspeakDaemon
import org.red.cerberus.exceptions.{ConflictingEntityException, ExceptionHandlers, ResourceNotFoundException}
import org.red.cerberus.jobs.teamspeak.RegistrationJoinListener
import org.red.cerberus.util.FutureConverters._
import org.red.cerberus.util.{PermissionBitEntry, TeamspeakGroupMapEntry, YamlParser}
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.Source
import scala.util.{Failure, Success}


class TeamspeakController(config: Config, userController: => UserController,
                          permissionController: => PermissionController)
                         (implicit dbAgent: JdbcBackend.Database, ec: ExecutionContext)
  extends LazyLogging {
  private val daemon = new TeamspeakDaemon(config)
  private val client = daemon.client

  private case class TeamspeakGroupMap(teamspeak_group_map: Seq[TeamspeakGroupMapEntry])

  val teamspeakPermissionMap: Seq[TeamspeakGroupMapEntry] =
    YamlParser.parseResource[TeamspeakGroupMap](Source.fromResource("teamspeak_group_map.yml")).teamspeak_group_map


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
      val f = safeTeamspeakQuery(() => client.getClients)()
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
        logger.info(s"Registered user in teamspeak " +
          s"userId=$userId " +
          s"event=teamspeak.register.success")
      case Failure(ex) =>
        logger.error("Failed to create registration request for " +
          s"userId=$userId " +
          s"event=teamspeak.register.failure", ex)
    }
    r
  }

  def getTeamspeakUniqueId(userId: Int): Future[String] = {
    val f = dbAgent.run(Coalition.TeamspeakUsers.filter(_.userId === userId).result)
      .map { res =>
        res.headOption match {
          case Some(r) => r.uniqueId
          case None => throw ResourceNotFoundException(s"No teamspeak uniqueId found for $userId")
        }
      }
    f.onComplete {
      case Success(res) =>
        logger.info(s"Got teamspeak uniqueId for userId=$userId event=user.getTeamspeakUniqueId.success")
      case Failure(ex) =>
        logger.error(s"Failed to get teamspeak uniqueId for userId=$userId event=user.getTeamspeakUniqueId.failure")
    }
    f
  }

  def getUserIdByUniqueId(uniqueId: String): Future[Int] = {
    val f = dbAgent.run(Coalition.TeamspeakUsers.filter(_.uniqueId === uniqueId).result)
    .map { res =>
      res.headOption match {
        case Some(r) => r.userId
        case None => throw ResourceNotFoundException(s"No teamspeak uniqueId found for uniqueId8=${uniqueId.substring(8)}")
      }
    }
    f.onComplete {
      case Success(res) =>
        logger.info(s"Got teamspeak uniqueId for uniqueId8=${uniqueId.substring(8)} event=user.getTeamspeakUserId.success")
      case Failure(ex) =>
        logger.error(s"Failed to get teamspeak uniqueId for uniqueId8=${uniqueId.substring(8)} event=user.getTeamspeakUserId.failure")
    }
    f
  }

  def registerTeamspeakUser(userId: Int, uniqueId: String): Future[Unit] = {
    val f = dbAgent.run(Coalition.TeamspeakUsers.filter(_.uniqueId === uniqueId).take(1).result).flatMap { r =>
      r.headOption match {
        case Some(res) => Future.failed(ConflictingEntityException("User with such teamspeak uniqueId already exists", None))
        case None =>
          val q = Coalition.TeamspeakUsers.map(r => (r.userId, r.uniqueId)) += (userId, uniqueId)
          dbAgent.run(q).flatMap(r => syncTeamspeakUser(userId))
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

  def getAllClients: Future[List[DatabaseClient]] = {
    this.safeTeamspeakQuery(() => client.getDatabaseClients)().map(_.asScala.toList)
  }

  def getGroupsByUniqueId(uniqueId: String): Future[List[TeamspeakGroupMapEntry]] = {
    this.safeTeamspeakQuery(() => client.getClientByUId(uniqueId))().map { resp =>
      resp.getServerGroups.toList.flatMap(gId => teamspeakPermissionMap.find(_.teamspeak_group_id == gId))
    }
  }

  def syncTeamspeakUser(uniqueId: String, permissions: Seq[PermissionBitEntry]): Future[Unit] = {
    val shouldBeGroups = permissions.flatMap { p =>
      teamspeakPermissionMap.find(_.bit_name == p.name).map(_.teamspeak_group_id)
    }.toSet

    val f = (for {
      tsDbId <- this.safeTeamspeakQuery(() => client.getDatabaseClientByUId(uniqueId))().map(_.getDatabaseId)
      curGroups <- this.safeTeamspeakQuery(() => client.getClientByUId(uniqueId))().map(_.getServerGroups.toSet)
      addToGroups <- Future.sequence {
        (shouldBeGroups -- curGroups).toList.map { groupId =>
          this.safeTeamspeakQuery(() => client.addClientToServerGroup(groupId, tsDbId))().map(_.booleanValue())
        }
      }
      removeFromGroups <- Future.sequence {
        (curGroups -- shouldBeGroups).toList.map { groupId =>
          this.safeTeamspeakQuery(() => client.addClientToServerGroup(groupId, tsDbId))().map(_.booleanValue())
        }
      }
    } yield addToGroups ++ removeFromGroups).map(_.forall(identity))

    f.onComplete {
      case Success(true) =>
        logger.info(s"Successfully updated teamspeak permissions for uniqueId8=${uniqueId.substring(8)} event=teamspeak.updateUser.success")
      case Success(false) =>
        logger.error(s"Updated user, but one or more requests failed uniqueId8=${uniqueId.substring(8)} event=teamspeak.updateUser.partial")
      case Failure(ex) =>
        logger.error(s"Failed to update user uniqueId8=${uniqueId.substring(8)} event=teamspeak.updateUser.failure", ex)
    }
    f.map { r =>
      if (r) ()
      else throw new RuntimeException(s"Not all requests returned true for userId ${uniqueId.substring(8)}")
    }
  }

  def syncTeamspeakUser(userId: Int): Future[Unit] = {
    for {
      uniqueId <- this.getTeamspeakUniqueId(userId)
      permissions <- permissionController.calculateAclPermissionsByUserId(userId)
      res <- syncTeamspeakUser(uniqueId, permissions)
    } yield res
  }

  private def safeQuery[T](f: () => T, timeout: FiniteDuration): Future[T] = {
    val p = Promise[T]()
    val q = scheduler.scheduleWithFixedDelay(0.seconds, 5.seconds) {
      if (daemon.isConnected)
        p.success(f.apply())
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

  private def safeTeamspeakQuery[T](f: () => CommandFuture[T])(timeout: FiniteDuration = 1.minute): Future[T] = {
    safeQuery(f, timeout).flatMap(x => commandToScalaFuture(x))
  }
}
