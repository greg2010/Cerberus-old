package org.red.cerberus

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpChallenges, HttpCredentials}
import akka.http.scaladsl.server.Directives.AuthenticationResult
import org.red.cerberus.exceptions.AuthenticationException

import scala.concurrent.ExecutionContext.Implicits.global
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim, JwtHeader}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}



trait AuthenticationHandler {

  val challenge: HttpChallenge = HttpChallenges.oAuth2("Cerberus")

  def authWithCustomJwt(credentials: Option[HttpCredentials]): Future[AuthenticationResult[Long]] =
    Future {
      credentials match {
        case Some(creds) if validateJwt(creds.token()) =>
          decodeJwt(creds.token()) match {
            case Success(res) => res.subject match {
              case Some(sub) =>
                try {
                  Right(sub.toLong)
                } catch {
                  case ex: Exception if NonFatal(ex) => throw AuthenticationException("Bad sub field in JWT", sub)
                }
              case None => throw AuthenticationException("Sub field missing in JWT", "")
            }
            case Failure(_) => Left(challenge)
          }
        case _ => Left(challenge)
      }
    }

  val issuer = "RED Coalition Resources Auth"
  val audience = "org.red.cerberus"
  val algorithm = JwtAlgorithm.HS512
  val accessExpiration: Long = 60 * 60 * 24
  val refreshExpiration: Long = accessExpiration * 7 * 4
  val key = "VERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEYVERYRANDOMKEY"

  def encodeJwt(payload: JwtClaim): String = {
    val header = JwtHeader(algorithm = algorithm)
    JwtCirce.encode(header, payload, key)
  }

  def validateJwt(token: String): Boolean = {
    JwtCirce.isValid(token, key, Seq(algorithm))
  }

  private def generatePayload(sub: Long): JwtClaim = {
    JwtClaim()
      .by(issuer)
      .to(audience)
      .about(sub.toString)
      .withId(java.util.UUID.randomUUID().toString)
      .issuedNow
      .startsNow
  }
  def generateAccessPayload(sub: Long): JwtClaim = {
    generatePayload(sub).expiresIn(accessExpiration)
  }
  def generateRefreshPayload(sub: Long): JwtClaim = {
    generatePayload(sub).expiresIn(refreshExpiration)
  }

  def decodeJwt(token: String): Try[JwtClaim] = {
    JwtCirce.decode(token, key, Seq(algorithm))
  }
}
