package org.red.cerberus

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpChallenges, HttpCredentials}
import akka.http.scaladsl.server.Directives.AuthenticationResult
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._
import org.red.cerberus.exceptions.AuthenticationException
import pdi.jwt._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


case class UserData(name: String, id: Int, characterId: Long, permissions: Long) {
  def toPrivateClaim: PrivateClaim = {
    PrivateClaim(
      nme = name,
      id = id,
      cid = characterId,
      prm = permissions
    )
  }
}

case class PrivateClaim(nme: String, id: Int, cid: Long, prm: Long) {
  def toUserData: UserData = {
    UserData(
      name = nme,
      id = id,
      characterId = cid,
      permissions = prm
    )
  }
}

trait AuthenticationHandler extends LazyLogging {


  protected def authWithCustomJwt(credentials: Option[HttpCredentials]):
  Future[AuthenticationResult[UserData]] =
    Future {
      credentials match {
        case Some(creds) =>
          try {
            Right(extractPayload(decodeJwt(creds.token)))
          }
          catch {
            case ex: Exception if NonFatal(ex) =>
              logger.error("Failed to decode JWT", ex)
              Left(challenge)
          }
        case _ =>
          logger.error("Missing auth credentials")
          Left(challenge)
      }
    }

  protected def generateAccessJwt(userData: UserData): String = {
    logger.info(s"generating access token for userId=${userData.id}")
    encodeJwt(generatePayload(userData, accessExpiration))
  }

  protected def generateRefreshJwt(userData: UserData): String = {
    logger.info(s"generating refresh token for userId=${userData.id}")
    encodeJwt(generatePayload(userData, refreshExpiration))
  }

  protected def validateJwt(token: String): Boolean = {
    JwtCirce.isValid(token, key, Seq(algorithm))
  }

  protected def extractPayloadFromToken(token: String): UserData = {
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

  private def generatePayload(userData: UserData, expiration: Long): JwtClaim = {
    JwtClaim()
      .by(issuer)
      .to(audience)
      .about(userData.toPrivateClaim.asJson.noSpaces)
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

  private def extractPayload(jwtClaim: JwtClaim): UserData = {
    jwtClaim.subject match {
      case Some(sub) =>
        parser.decode[PrivateClaim](sub) match {
          case Right(privateClaim) => privateClaim.toUserData
          case Left(ex) =>
            logger.error(s"Failed to decode payload ${jwtClaim.subject}", ex)
            throw ex
        }
      case None =>
        logger.error("Empty JWT payload")
        throw AuthenticationException("Empty JWT payload", "")
    }
  }
}
