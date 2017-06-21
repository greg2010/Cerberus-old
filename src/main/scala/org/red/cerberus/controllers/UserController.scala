package org.red.cerberus.controllers

import java.sql.Timestamp
import java.util.UUID

import com.roundeights.hasher.Implicits._
import com.typesafe.scalalogging.LazyLogging
import org.matthicks.mailgun.MessageResponse
import org.red.cerberus.UserData
import org.red.cerberus.exceptions._
import org.red.cerberus.external.auth._
import org.red.db.models.Coalition
import org.red.db.models.Coalition.{PasswordResetRequestsRow, UsersRow}
import slick.dbio.Effect
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._
import slick.sql.FixedSqlAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}


class UserController(permissionController: PermissionController, emailController: EmailController, eveApiClient: EveApiClient)(implicit dbAgent: JdbcBackend.Database) extends LazyLogging {

  private case class DBUserData(eveUserData: EveUserData,
                                userId: Int,
                                password: Option[String],
                                salt: Option[String],
                                isBanned: Boolean)


  private val rsg: Stream[Char] = Random.alphanumeric
  def generateSalt: String = rsg.take(4).mkString

  private def generatePwdHash(password: String, salt: String): String = {
    (password + salt).sha512.hex
  }

  private def verifyPassword(pwd: String, salt: String, dbHash: String): Boolean = generatePwdHash(pwd, salt) == dbHash

  private def usersQuery(email: String,
                         eveUserData: EveUserData,
                         password: Option[String],
                         timestamp: Timestamp
                        ): FixedSqlAction[Int, NoStream, Effect.Write] = {

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
      .filter(_.id === userId)
      .map(_.lastLoggedIn).update(currentTimestamp)
    val f = dbAgent.run(query).flatMap {
      case 0 => Future.failed(ResourceNotFoundException("No user was updated!"))
      case 1 => Future.successful {}
      case n => Future.failed(new RuntimeException("More than 1 user was updated!"))
    }.recoverWith(ExceptionHandlers.dbExceptionHandler)
    f.onComplete {
      case Success(_) =>
        logger.info(s"Successfully checked in user asynchronously " +
          s"userId=$userId " +
          s"event=users.checkinAsync.success")
      case Failure(ex) =>
        logger.error(s"Failed to check in user asynchronously " +
          s"userId=$userId " +
          s"event=users.checkinAsync.failure", ex)
    }
    f
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
    val f = dbAgent.run(query.result)
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
            permissionController.calculateAclPermission(res.eveUserData).flatMap { perm =>
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
    f.onComplete {
      case Success(res) =>
        logger.info(s"Logged in user using legacy flow " +
          s"userId=${res.id} " +
          s"characterId=${res.characterId} " +
          s"event=users.login.legacy.success")
      case Failure(ex: AuthenticationException) =>
        logger.error(s"Bad login or password for nameOrLogin=$nameOrEmail event=users.login.legacy.failure")
      case Failure(ex) =>
        logger.error(s"Failed to log in user using legacy flow " +
          s"event=users.login.legacy.failure", ex)
    }
    f
  }

  private def updateUserDataQuery(eveUserData: EveUserData): DBIOAction[Unit, NoStream, Effect.Write with Effect.Write with Effect.Write with Effect.Transactional] = {
    val currentTimestamp = new Timestamp(System.currentTimeMillis())
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
      case _ => throw CCPException("Alliance ID or name is present, but not both")
    }

    (for {
      _ <- allianceQuery
      _ <- corpQuery
      _ <- charQuery
    } yield ()).transactionally
  }

  def createUser(email: String,
                 password: Option[String],
                 credentials: Credentials): Future[Unit] = {
    eveApiClient.fetchUser(credentials).flatMap { eveUserData =>
      val currentTimestamp = new Timestamp(System.currentTimeMillis())
      
      def credsQuery(userId: Int) = credentials match {
        case legacy: LegacyCredentials =>
          Coalition.EveApi.map(c => (c.userId, c.characterId, c.keyId, c.verificationCode)) +=
            (userId, eveUserData.characterId, Some(legacy.apiKey.keyId), Some(legacy.apiKey.vCode))
        case sso: SSOCredentials => Coalition.EveApi.map(_.evessoRefreshToken) += Some(sso.refreshToken)
      }


      val action = (for {
        _ <- updateUserDataQuery(eveUserData)
        userId <- usersQuery(email, eveUserData, password, currentTimestamp)
        _ <- credsQuery(userId)
      } yield userId).transactionally
      val f = dbAgent.run(action).recoverWith(ExceptionHandlers.dbExceptionHandler)
      f.onComplete {
        case Success(res) =>
          logger.info(s"Created new user using legacy flow " +
            s"userId=$res " +
            s"event=users.create.legacy.success")
        case Failure(ex) =>
          logger.error(s"Failed to create new user using legacy flow " +
            s"event=users.create.legacy.failure", ex)
      }
      f.map(_ => ())
    }
  }



  def updateUserData(eveUserData: EveUserData): Future[Unit] = {
    val res = dbAgent.run(updateUserDataQuery(eveUserData))
    res.onComplete {
      case Success(_) =>
        logger.info(s"Updated eve user info for user " +
          s"characterId=${eveUserData.characterId} " +
          s"characterName=${eveUserData.characterName} " +
          s"event=user.eveData.update.success")
      case Failure(ex) =>
        logger.info(s"Failed to update eve user info " +
          s"characterId=${eveUserData.characterId} " +
          s"characterName=${eveUserData.characterName} " +
          s"event=user.eveData.update.failure")
    }
    res
  }

  private def deleteObsoleteQuery(id: Int): FixedSqlAction[Int, NoStream, Effect.Write] =
    Coalition.PasswordResetRequests.filter(_.id === id).delete

  def initializePasswordReset(email: String): Future[MessageResponse] = {
    def insertAndSendToken(usersRow: UsersRow, obsoleteRequestId: Option[Int]): Future[(UsersRow, MessageResponse)] = {
      val deleteObsolete = obsoleteRequestId match {
        case Some(id) => dbAgent.run(deleteObsoleteQuery(id))
        case None => Future {0}
      }
      val token = generatePwdHash(UUID.randomUUID().toString, usersRow.id.toString)
      val q = Coalition.PasswordResetRequests.map(r => (r.email, r.token, r.timeCreated)) +=
        (email, token, new Timestamp(System.currentTimeMillis()))

      for {
        _ <- deleteObsolete
        sendEmail <- emailController.sendPasswordResetEmail(usersRow.id, token)
        insertToken <- dbAgent.run(q)
      } yield (usersRow, sendEmail)
    }

    val q = Coalition.Users.filter(_.email === email).take(1)
      .joinLeft(Coalition.PasswordResetRequests).on((u, p) => u.email === p.email)
    val f = dbAgent.run(q.result).flatMap { r =>
      val rOpt = r.headOption
      (rOpt.map(_._1), rOpt.flatMap(_._2)) match {
        case (Some(usersRow), None) =>
          logger.info(s"Password reset request for " +
            s"email=$email " +
            s"userId=${usersRow.id} " +
            s"characterId=${usersRow.characterId} " +
            s"event=user.passwordReset.create")
          insertAndSendToken(usersRow, None)
        case (Some(usersRow), Some(passwordResetRequestsRow)) =>
          val difference = (System.currentTimeMillis() - passwordResetRequestsRow.timeCreated.getTime).millis
          if (difference < 15.minutes)
            Future.failed(new IllegalStateException("This user has already requested password reset"))
          else insertAndSendToken(usersRow, Some(passwordResetRequestsRow.id))
        case (None, _) => Future.failed(ResourceNotFoundException(s"No user with email $email exists"))
      }
    }

    f.onComplete {
      case Success(res) =>
        logger.info(s"Successfully created password reset request " +
          s"userId=${res._1.id} " +
          s"characterId=${res._1.characterId} " +
          s"name=${res._1.name} " +
          s"email=${res._1.email} " +
          s"mailgunMessageId=${res._2.id} " +
          s"event=user.passwordReset.create.success")
      case Failure(ex) =>
        logger.info(s"Failed create password reset request " +
          s"email=$email " +
          s"event=user.passwordReset.create.failure", ex)
    }

    f.map(r => r._2)
  }

  def updatePassword(userId: Int, newPassword: String): Future[Unit] = {
    val salt = generateSalt
    val hashedPwd = generatePwdHash(newPassword, salt)
    val q = Coalition.Users.filter(_.id === userId)
      .map(r => (r.password, r.salt))
      .update((Some(hashedPwd), Some(salt)))
    val f = dbAgent.run(q).map {
      case 0 => throw ResourceNotFoundException(s"No user with userId $userId exists")
      case 1 =>
        emailController.sendPasswordChangeEmail(userId)
        () // Executing email send async
      case n if n > 1 => throw new RuntimeException(s"$n users were updated!")
    }

    f.onComplete {
      case Success(_) =>
        logger.info(s"Password for user was updated userId=$userId event=user.password.update.success")
      case Failure(ex) =>
        logger.error(s"Failed to update password for user userId=$userId event=user.password.update.failure", ex)
    }
    f
  }

  def resetPasswordWithToken(email: String, token: String, newPassword: String): Future[Unit] = {
    def testToken(passwordResetRequestsRow: PasswordResetRequestsRow, userId: Int): Boolean = {
      val dbHashedToken = generatePwdHash(passwordResetRequestsRow.token, userId.toString)
      val difference = (System.currentTimeMillis() - passwordResetRequestsRow.timeCreated.getTime).millis
      (dbHashedToken == token) &&  difference < 15.minutes
    }

    val q = Coalition.Users.filter(_.email === email)
      .joinLeft(Coalition.PasswordResetRequests).on((u, p) => u.email === p.email)
    val f = dbAgent.run(q.result).flatMap { r =>
      val rOpt = r.headOption
      (rOpt.map(_._1), rOpt.flatMap(_._2)) match {
        case (Some(usersRow), Some(passwordResetRequestsRow))
          if testToken(passwordResetRequestsRow, usersRow.id) =>
          for {
            upd <- this.updatePassword(usersRow.id, newPassword)
            del <- dbAgent.run(deleteObsoleteQuery(passwordResetRequestsRow.id))
          } yield usersRow
        case _ =>
          Future.failed(ResourceNotFoundException("No user found for this email, token expired or token doesn't match"))
      }
    }
    f.onComplete {
      case Success(res) =>
        logger.info(s"Completed password reset for user userId=${res.id} email=${res.email} event=user.passwordReset.complete.success")
      case Failure(ex) =>
        logger.error(s"Failed to update password for user email=$email event=user.passwordReset.complete.failure", ex)
    }
    f.map(_ => ())
  }
}
