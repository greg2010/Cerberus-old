package org.red.cerberus.controllers

import java.sql.Timestamp

import com.roundeights.hasher.Implicits._
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.Implicits.dbAgent
import org.red.cerberus.UserData
import org.red.cerberus.external.auth.{Credentials, EveUserData, LegacyCredentials, SSOCredentials}
import org.red.db.models.Coalition
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.sql.FixedSqlAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random


object UserController extends LazyLogging {

  // TODO: add bans
  private val rsg: Stream[Char] = Random.alphanumeric

  private def generatePwdHash(password: String, salt: String): String = {
    (password + salt).sha512.toString()
  }

  private def usersQuery(email: String,
                         eveUserData: EveUserData,
                         password: Option[String],
                         timestamp: Timestamp
                        ): FixedSqlAction[Int, NoStream, Effect.Write] = {
    def generateSalt: String = rsg.take(4).mkString
    val passwordWithSalt = password match {
      case Some(pwd) =>
        val s = generateSalt
        (Some(generatePwdHash(pwd, s)), Some(s))
      case None => (None, None)
    }
    Coalition.Users.map(u => (u.characterId, u.name, u.email, u.password, u.salt)) +=
      (eveUserData.characterId, eveUserData.characterName, email, passwordWithSalt._1, passwordWithSalt._2)
  }

  def checkInUser(userId: Long): Future[Unit] = {
    val currentTimestamp = Some(new Timestamp(System.currentTimeMillis()))
    val query = Coalition.Users
      .filter(_.id === userId.toInt) // FIXME: change serial to bigserial
      .map(_.lastLoggedIn).update(currentTimestamp)
    dbAgent.run(query).flatMap {
      case 0 => Future.failed(new RuntimeException("No user was updated!")) // FIXME: exception type
      case 1 => Future.successful {}
      case n => Future.failed(new RuntimeException("More than 1 user was updated!"))
    }
  }

  def legacyLogin(nameOrEmail: String, password: String): Future[UserData] = {
    val query = Coalition.Users
      .filter(u => u.email === nameOrEmail || u.name === nameOrEmail)
      .take(1)
    dbAgent.run(query.result).flatMap { res =>
      val dbPassword = res.headOption.flatMap(u => u.password)
      val dbSalt = res.headOption.flatMap(u => u.salt)
      (res.headOption, dbPassword, dbSalt) match {
        case (Some(u), Some(p), Some(s)) =>
          if (generatePwdHash(password, s) == p) {
            checkInUser(u.id)
            Future {
              UserData(
                name = u.name,
                id = u.id,
                characterId = u.characterId
              )
            }
          } else Future.failed(new RuntimeException("Bad login and/or password"))
        case _ => Future.failed(new RuntimeException("Bad login and/or password")) //FIXME: change exception type
      }
    }
  }

  def createUser(email: String,
                   password: Option[String],
                   credentials: Credentials): Future[Unit] = {
    credentials.fetchUser.flatMap { eveUserData =>
      val currentTimestamp = new Timestamp(System.currentTimeMillis())
      val credsQuery = credentials match {
        case legacy: LegacyCredentials =>
          Coalition.EveApi.map(c => (c.keyId, c.verificationCode)) +=
            (Some(legacy.apiKey.keyId), Some(legacy.apiKey.vCode))
        case sso: SSOCredentials => Coalition.EveApi.map(_.evessoRefreshToken) += Some(sso.refreshToken)
      }
      val charQuery = Coalition.Character
        .insertOrUpdate(
          Coalition.CharacterRow(
            eveUserData.characterId,
            eveUserData.characterName,
            eveUserData.corporationId,
            currentTimestamp))
      val corpQuery =
        Coalition.Corporation
          .insertOrUpdate(
            Coalition.CorporationRow(
              eveUserData.corporationId,
              eveUserData.corporationName,
              eveUserData.allianceId,
              currentTimestamp))

      lazy val allianceQuery = (eveUserData.allianceId, eveUserData.allianceName) match {
        case (Some(aId), Some(aName)) =>
          dbAgent.run(Coalition.Alliance.insertOrUpdate(Coalition.AllianceRow(aId, aName, currentTimestamp)))
        case (None, None) => Future.successful {}
        case _ => throw new RuntimeException("Alliance ID or name is present, but not both") // FIXME: change exception type
      }
      Future.sequence {
        Seq(
          dbAgent.run(usersQuery(email, eveUserData, password, currentTimestamp)),
          dbAgent.run(credsQuery),
          dbAgent.run(charQuery),
          dbAgent.run(corpQuery),
          allianceQuery
        )
      }.map(_ => ())
    }
  }
}
