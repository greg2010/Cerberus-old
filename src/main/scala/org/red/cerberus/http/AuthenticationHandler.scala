package org.red.cerberus.http

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpChallenges, HttpCredentials}
import akka.http.scaladsl.server.Directives.AuthenticationResult
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._
import org.red.cerberus.cerberusConfig
import org.red.cerberus.util.PrivateClaim
import org.red.iris.finagle.clients.PermissionClient
import org.red.iris.{AuthenticationException, PermissionBit, UserMini}
import pdi.jwt._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


class AuthenticationHandler(permissionList: Future[Seq[PermissionBit]])(implicit ec: ExecutionContext) extends LazyLogging {


  def dataResponseFromUserMini(userMini: UserMini): DataResponse[TokenResponse] = {

    DataResponse(
      TokenResponse(
        accessToken = this.generateAccessJwt(userMini),
        refreshToken = this.generateRefreshJwt(userMini)
      )
    )
  }

  def authWithCustomJwt(credentials: Option[HttpCredentials]):
  Future[AuthenticationResult[UserMini]] =
    credentials match {
      case Some(creds) =>
        try {
          extractPayload(decodeJwt(creds.token)).map(Right.apply)
        }
        catch {
          case ex: Exception if NonFatal(ex) =>
            logger.error("Failed to decode JWT", ex)
            Future(Left(challenge))
        }
      case _ =>
        logger.error("Missing auth credentials")
        Future(Left(challenge))
    }

  def generateAccessJwt(userData: UserMini): String = {
    logger.info(s"generating access token for userId=${userData.id}")
    encodeJwt(generatePayload(userData, accessExpiration))
  }

  def generateRefreshJwt(userData: UserMini): String = {
    logger.info(s"generating refresh token for userId=${userData.id}")
    encodeJwt(generatePayload(userData, refreshExpiration))
  }

  protected def validateJwt(token: String): Boolean = {
    JwtCirce.isValid(token, key, Seq(algorithm))
  }

  def extractPayloadFromToken(token: String): Future[UserMini] = {
    extractPayload(decodeJwt(token))
  }

  private val challenge: HttpChallenge = HttpChallenges.oAuth2("Cerberus")
  private val issuer = "RED Coalition Resources Auth"
  private val audience = "org.red.cerberus"
  private val algorithm = JwtAlgorithm.HS512
  private val accessExpiration: Long = 60 * 60 * 24
  private val refreshExpiration: Long = accessExpiration * 7 * 4
  private val key = cerberusConfig.getString("JWTSecretKey")

  private def encodeJwt(payload: JwtClaim): String = {
    val header = JwtHeader(algorithm = algorithm)
    JwtCirce.encode(header, payload, key)
  }

  private def generatePayload(userMini: UserMini, expiration: Long): JwtClaim = {
    JwtClaim()
      .by(issuer)
      .to(audience)
      .about(PrivateClaim.fromUserData(userMini).asJson.noSpaces)
      .withId(java.util.UUID.randomUUID().toString)
      .issuedNow
      .startsNow
      .expiresIn(expiration)
  }

  private def decodeJwt(token: String): JwtClaim = {
    JwtCirce.decode(token, key, Seq(algorithm)) match {
      case Success(jwtClaim) => jwtClaim
      case Failure(ex) =>
        logger.error("Failed to extract JWT", ex)
        throw ex
    }
  }

  private def extractPayload(jwtClaim: JwtClaim): Future[UserMini] = {
    jwtClaim.subject match {
      case Some(sub) =>
        parser.decode[PrivateClaim](sub) match {
          case Right(privateClaim) => permissionList.map(privateClaim.toUserData)
          case Left(ex) =>
            logger.error(s"Failed to decode payload ${jwtClaim.subject}", ex)
            Future.failed(ex)
        }
      case None =>
        logger.error("Empty JWT payload")
        Future.failed(AuthenticationException("Empty JWT payload", ""))
    }
  }
}
