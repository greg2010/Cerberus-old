package org.red.cerberus.daemons

import com.github.theholywaffle.teamspeak3.api.event._
import scala.concurrent.duration._
import com.github.theholywaffle.teamspeak3.api.reconnect.{ConnectionHandler, ReconnectStrategy}
import com.github.theholywaffle.teamspeak3.{TS3Config, TS3Query}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.controllers.UserController
import org.red.cerberus.exceptions.ConflictingEntityException
import org.red.cerberus.util.FutureConverters
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._
import com.gilt.gfc.concurrent.ScalaFutures.FutureOps

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class TeamspeakDaemon(config: Config, userController: => UserController)
                     (implicit dbAgent: JdbcBackend.Database, ec: ExecutionContext)
  extends LazyLogging {

  private class CustomConnectionHandler extends ConnectionHandler {

    override def onDisconnect(ts3Query: TS3Query): Unit = {
      logger.warn("Disconnected from teamspeak event=teamspeak.disconnect")
    }

    override def onConnect(ts3Query: TS3Query): Unit = {
      val client = ts3Query.getAsyncApi
      logger.info("Connected to teamspeak event=teamspeak.connect")
      client.login(config.getString("ts3.serverQueryLogin"), config.getString("ts3.serverQueryPassword"))
      client.selectVirtualServerById(config.getInt("ts3.virtualServerId"))
      client.setNickname(config.getString("ts3.botName"))
      client.registerAllEvents()

      FutureConverters.commandToScalaFuture(client.getServerInfo)
        .onComplete {
          case Success(r) =>
            logger.info(s"Successfully connected to TS3 server " +
              s"teamspeakServerName=${r.getName} " +
              s"teamspeakVersion=${r.getVersion} " +
              s"ip=${r.getIp} " +
              s"ping=${r.getPing} " +
              s"event=teamspeak.connect.success")
          case Failure(ex) =>
            logger.error("Failed to connect to TS3 server event=teamspeak.connect.failure", ex)
        }
    }
  }

  private class RegistrationJoinListener(expectedNickname: String, p: Promise[String]) extends TS3EventAdapter {
    logger.info(s"Created new teamspeak event listener " +
      s"teamspeakEvent=join " +
      s"expectedNickname=$expectedNickname " +
      s"event=teamspeak.listener.create")
    override def onClientJoin(e: ClientJoinEvent): Unit = {
      logger.debug(s"Teamspeak join event fired " +
        s"teamspeakClientId=${e.getClientId} " +
        s"teamspeakNickname=${e.getClientNickname} " +
        s"event=teamspeak.listener.fire")
      if (e.getClientNickname == expectedNickname) {
        logger.info(s"Successfully obtained teamspeak uniqueId for user " +
          s"expectedNickname=$expectedNickname " +
          s"event=teamspeak.listener.success")
        p.success(e.getUniqueClientIdentifier)
        client.removeTS3Listeners(this)
      }
    }
  }

  private val ts3Conf = new TS3Config
  ts3Conf.setHost(config.getString("ts3.host"))
  private val connectionHandler = new CustomConnectionHandler
  ts3Conf.setConnectionHandler(new CustomConnectionHandler)
  ts3Conf.setReconnectStrategy(ReconnectStrategy.exponentialBackoff())

  private val ts3Query = new TS3Query(ts3Conf)
  private val client = ts3Query.getAsyncApi
  private val initialConnectionFuture: Future[Unit] = Future {
    ts3Query.connect()
  }.withTimeout(30.seconds)


  initialConnectionFuture.onComplete {
    case Success(r) =>
      logger.info(s"Connected to teamspeak teamspeakHost=${config.getString("ts3.host")} " +
        s"event=teamspeak.connect.success")
    case Failure(ex) =>
      logger.error("Failed to connect to teamspeak event=teamspeak.connect.failure", ex)
  }


  def createRegistrationAttempt(userId: Int): Future[Unit] = {
    val r = userController.getUser(userId).flatMap { u =>
      val p = Promise[String]()
      val expectedNickname = s"${u.eveUserData.characterName} | ${u.eveUserData.corporationTicker}" +
        (u.eveUserData.allianceTicker match {
        case Some(n) => s" | $n"
        case None => ""
      })
      val f =
        initialConnectionFuture.map { _ =>
        client.addTS3Listeners(new RegistrationJoinListener(expectedNickname, p))
        logger.info(s"Successfully registered listener for " +
          s"userId=$userId " +
          s"expectedNickname=$expectedNickname " +
          s"event=teamspeak.listener.create.success")
      }
      p.future.flatMap(uniqueId => registerTeamspeakUser(userId, uniqueId))
      f
    }
    r.onComplete {
      case Success(_) =>
        logger.info(s"Created registration request for " +
          s"userId=$userId " +
          s"event=teamspeak.listener.create.success")
      case Failure(ex) =>
        logger.error("Failed to create registration request for " +
          s"userId=$userId " +
          s"event=teamspeak.listener.create.failure", ex)
    }
    r
  }

  def registerTeamspeakUser(userId: Int, uniqueId: String): Future[Int] = {
    val f = dbAgent.run(Coalition.TeamspeakUsers.filter(_.uniqueId === uniqueId).take(1).result).flatMap { r =>
      r.headOption match {
        case Some(res) => Future.failed(ConflictingEntityException("User with such teamspeak uniqueId already exists" , None))
        case None =>
          val q = Coalition.TeamspeakUsers.map(r => (r.userId, r.uniqueId)) += (userId, uniqueId)
          dbAgent.run(q)
      }
    }
    f.onComplete {
      case Success(_) =>
        logger.info(s"Successfully registered user in teamspeak userId=$userId event=teamspeak.register.success")
      case Failure(ex) =>
        logger.error(s"Failed to register user in teamspeak userId=$userId event=teamspeak.register.failure")
    }
    f
  }
}
