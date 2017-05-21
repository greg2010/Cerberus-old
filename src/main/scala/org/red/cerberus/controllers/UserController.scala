package org.red.cerberus.controllers

import java.sql.Timestamp

import com.roundeights.hasher.Implicits._
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.Implicits.dbAgent
import org.red.cerberus.UserData
import org.red.cerberus.exceptions.{AccessRestrictedException, AuthenticationException, ExceptionHandlers}
import org.red.cerberus.external.auth.{Credentials, EveUserData, LegacyCredentials, SSOCredentials}
import org.red.db.models.Coalition
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.sql.FixedSqlAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random


object UserController extends LazyLogging {

  private case class DBUserData(eveUserData: EveUserData,
                                userId: Int,
                                password: Option[String],
                                salt: Option[String],
                                isBanned: Boolean)


  // TODO: add bans
  private val rsg: Stream[Char] = Random.alphanumeric

  private def generatePwdHash(password: String, salt: String): String = {
    (password + salt).sha512.toString()
  }

  private def verifyPassword(pwd: String, salt: String, dbHash: String): Boolean = generatePwdHash(pwd, salt) == dbHash

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

    Coalition.Users
      .map(u => (u.characterId, u.name, u.email, u.password, u.salt))
      .returning(Coalition.Users.map(_.id)) +=
      (eveUserData.characterId, eveUserData.characterName, email, passwordWithSalt._1, passwordWithSalt._2)
  }

  def checkInUser(userId: Int): Future[Unit] = {
    val currentTimestamp = Some(new Timestamp(System.currentTimeMillis()))
    val query = Coalition.Users
      .filter(_.id === userId.toInt) // FIXME: change serial to bigserial
      .map(_.lastLoggedIn).update(currentTimestamp)
    dbAgent.run(query).flatMap {
      case 0 => Future.failed(new RuntimeException("No user was updated!")) // FIXME: exception type
      case 1 => Future.successful {}
      case n => Future.failed(new RuntimeException("More than 1 user was updated!"))
    }.recoverWith(ExceptionHandlers.dbExceptionHandler)
  }

  def legacyLogin(nameOrEmail: String, password: String): Future[UserData] = {

    val query = Coalition.Users
      .filter(u => u.email === nameOrEmail || u.name === nameOrEmail)
      .join(Coalition.Character)
      .on((u, ch) => u.characterId === ch.id)
      .join(Coalition.Corporation)
      .on((uch, corp) => uch._2.corporationId === corp.id)
      .joinLeft(Coalition.Alliance)
      .on((uchcorp, al) => uchcorp._2.allianceId === al.id)
      .map(data => (
        data._1._1._1.id,
        data._1._1._1.characterId,
        data._1._1._1.name,
        data._1._2.id,
        data._1._2.name,
        data._1._2.allianceId,
        data._2,
        data._1._1._1.password,
        data._1._1._1.salt,
        data._1._1._1.banned)
      ).take(1)
    dbAgent.run(query.result)
      .flatMap { res =>
        res.headOption match {
          case Some((
            userId, charId, charName,
            corpId, corpName, allianceId,
            allianceRow, pwd, salt, banned)) =>
            Future {
              DBUserData(
                EveUserData(
                  charId,
                  charName,
                  corpId,
                  corpName,
                  allianceId,
                  allianceRow.map(_.name)
                ),
                userId, pwd,
                salt, banned
              )
            }
          case _ => Future.failed(AuthenticationException("Bad login and/or password", ""))
        }
      }.flatMap { res =>
      (res.password, res.salt) match {
        case (Some(dbHash), Some(salt)) =>
          if (verifyPassword(password, salt, dbHash) && !res.isBanned) {
            checkInUser(res.userId)
            PermissionController.calculateAclPermission(res.eveUserData).flatMap { perm =>
              Future {
                UserData(
                  name = res.eveUserData.characterName,
                  id = res.userId,
                  characterId = res.eveUserData.characterId,
                  permissions = perm
                )
              }
            }
          } else if (res.isBanned) Future.failed(AccessRestrictedException("User is banned"))
          else Future.failed(AuthenticationException("Bad login and/or password", ""))
        case _ => Future.failed(AuthenticationException("Bad login and/or password", ""))
      }
    }.recoverWith(ExceptionHandlers.dbExceptionHandler)
  }

  def createUser(email: String,
                 password: Option[String],
                 credentials: Credentials): Future[Unit] = {
    credentials.fetchUser.flatMap { eveUserData =>
      val currentTimestamp = new Timestamp(System.currentTimeMillis())


      def credsQuery(userId: Int) = credentials match {
        case legacy: LegacyCredentials =>
          Coalition.EveApi.map(c => (c.userId, c.characterId, c.keyId, c.verificationCode)) +=
            (userId, eveUserData.characterId, Some(legacy.apiKey.keyId), Some(legacy.apiKey.vCode))
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

      val allianceQuery = (eveUserData.allianceId, eveUserData.allianceName) match {
        case (Some(aId), Some(aName)) => Coalition.Alliance.insertOrUpdate(Coalition.AllianceRow(aId, aName, currentTimestamp))
        case (None, None) => DBIO.successful {}
        case _ => throw new RuntimeException("Alliance ID or name is present, but not both") // FIXME: change exception type
      }

      val action = (for {
        _ <- allianceQuery
        _ <- corpQuery
        _ <- charQuery
        userId <- usersQuery(email, eveUserData, password, currentTimestamp)
        _ <- credsQuery(userId)
      } yield ()).transactionally
      dbAgent.run(action).recoverWith(ExceptionHandlers.dbExceptionHandler)
    }
  }
}
