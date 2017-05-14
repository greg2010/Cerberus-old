package org.red.cerberus

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpChallenges, HttpCredentials}
import akka.http.scaladsl.server.Directives.AuthenticationResult
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.syntax._
import org.red.cerberus.exceptions.AuthenticationException
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim, JwtHeader}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.{Failure, Success}



trait AuthenticationHandler extends LazyLogging {

  case class UserData(name: String, id: Long, characterId: Long) {
    def toPrivateClaim: PrivateClaim = {
      PrivateClaim(
        nme = name,
        id = id,
        cid = characterId
      )
    }
  }

  protected case class PrivateClaim(nme: String, id: Long, cid: Long) {
    def toUserData: UserData = {
      UserData(
        name = nme,
        id = id,
        characterId = cid
      )
    }
  }

  protected def authWithCustomJwt(credentials: Option[HttpCredentials]):
  Future[AuthenticationResult[UserData]] =
    Future {
      credentials match {
        case Some(creds) =>
          try {
            Right(extractPayload(decodeJwt(creds.token)))
          }
          catch {
            case ex: Exception if NonFatal(ex) => Left(challenge)
          }
        case _ => Left(challenge)
      }
    }

  protected def generateAccessJwt(userData: UserData): String = {
    encodeJwt(generatePayload(userData, accessExpiration))
  }

  protected def generateRefreshJwt(userData: UserData): String = {
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
  private val key = "VERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEY"

  private def encodeJwt(payload: JwtClaim): String = {
    val header = JwtHeader(algorithm = algorithm)
    JwtCirce.encode(header, payload, key)
  }

  private def generatePayload(userData: UserData, expiration: Long): JwtClaim = {
    JwtClaim()
      .by(issuer)
      .to(audience)
      .about(userData.toPrivateClaim.asJson.toString)
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
      case Some(sub) => sub.asJson.as[PrivateClaim] match {
        case Right(privateClaim) =>privateClaim.toUserData
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
