package org.red.cerberus.controllers

import com.github.theholywaffle.teamspeak3.api.event._
import com.github.theholywaffle.teamspeak3.{TS3ApiAsync, TS3Config, TS3Query}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.Try


class TeamspeakController(config: Config, userController: UserController)(implicit dbAgent: JdbcBackend.Database) extends LazyLogging {

  private class RegistrationJoinListener(expectedNickname: String, p: Promise[String]) extends TS3EventAdapter {
    override def onClientJoin(e: ClientJoinEvent): Unit = {
      if (e.getClientNickname == expectedNickname) {
        p.complete(Try(e.getUniqueClientIdentifier))
        client.removeTS3Listeners(this)
      }
    }
  }

  val ts3Conf = new TS3Config
  ts3Conf.setHost(config.getString("ts3.host"))

  val ts3Query = new TS3Query(ts3Conf)
  ts3Query.connect()

  val client = new TS3ApiAsync(ts3Query)
  client.login(config.getString("ts3.serverQueryLogin"), config.getString("ts3.serverQueryPassword"))
  client.selectVirtualServerById(config.getInt("ts3.virtualServerId"))
  client.setNickname(config.getString("ts3.botName"))
  client.registerAllEvents()
  
  
  def createRegistrationAttempt(userId: Int): Unit = {
    val p = Promise[String]()
    userController.getUser(userId).flatMap { u =>
      val expectedNickname = s"${u.eveUserData.characterName} | ${u.eveUserData.corporationName}" +
        (u.eveUserData.allianceName match {
        case Some(n) => s" | + $n"
        case None => ""
      })
      client.addTS3Listeners(new RegistrationJoinListener(expectedNickname, p))
      val f = (for {
        uniqueId <- p.future
        dbHasUniqueId <- dbAgent.run(Coalition.TeamspeakUsers.filter(_.uniqueId === uniqueId).result).map(_.nonEmpty)
        if dbHasUniqueId
      } yield dbAgent.run(Coalition.TeamspeakUsers.map(r => (r.userId, r.uniqueId)) += (userId, uniqueId)))
        .flatten
      f
    }
  }
}
