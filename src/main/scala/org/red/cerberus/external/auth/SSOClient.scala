package org.red.cerberus.external.auth

import java.io.InputStream

import com.gilt.gfc.concurrent.ScalaFutures.retryWithExponentialDelay
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, parser}
import org.red.cerberus.exceptions.CCPException
import org.red.eveapi.esi.api.{AllianceApi, CharacterApi, CorporationApi}
import io.circe.generic.auto._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.concurrent.duration._
import scalaj.http.{Http, HttpRequest}


private [this] class SSOClient(config: Config) extends LazyLogging {
  private val defaultDatasource = Some("tranquility")
  private val baseUrl = "https://login.eveonline.com/oauth"
  private val userAgent = "red-cerberus/1.0"
  private def tokenRequest: HttpRequest =
    Http(baseUrl + "/token")
      .method("POST")
      .header("User-Agent", userAgent)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .auth(config.getString("SSOClientId"), config.getString("SSOClientSecret"))

  private lazy val esiCharClient = new CharacterApi()
  private lazy val esiCorpClient = new CorporationApi()
  private lazy val esiAllianceClient = new AllianceApi()
  private case class VerifyResponse(CharacterID: Long,
                                    CharacterName: String,
                                    ExpiresOn: String,
                                    Scopes: String,
                                    TokenType: String,
                                    CharacterOwnerHash: String)


  private case class TokenResponse(access_token: String,
                                     token_type: String,
                                     expires_in: String,
                                     refresh_token: String)

  private def parseResponse[T](respCode: Int,
                                 headers: Map[String, IndexedSeq[String]],
                                 is: InputStream)(implicit evidence: Decoder[T]): T = {
    respCode match {
      case 200 =>
        parser.decode[T](scala.io.Source.fromInputStream(is).mkString) match {
          case Right(res) => res
          case Left(ex) => throw CCPException("Failed to parse SSO response", Some(ex))
        }
      case x =>
        throw CCPException(s"Got $x status code on attempt to access SSO API")
    }
  }


  private def executeQueryAsync[T](httpRequest: HttpRequest,
                                   parser: (Int, Map[String, IndexedSeq[String]], InputStream) => T) = {
    retryWithExponentialDelay(
      maxRetryTimes = 3,
      initialDelay = 100.millis,
      maxDelay = 100.millis,
      exponentFactor = 1) {
      Future(httpRequest.exec[T](parser).body)
    }
  }

  def fetchSSOCredential(SSOAuthCode: SSOAuthCode): Future[SSOCredentials] = {
    val q =
      tokenRequest.postForm(
        Seq {
          ("grant_type", "authorization_code")
          ("code", SSOAuthCode.code)
        }
      )
    this.executeQueryAsync[TokenResponse](
      q,
      parseResponse[TokenResponse]
    ).map { res =>
      SSOCredentials(res.refresh_token, res.access_token)
    }
  }

  def createSSOCredential(refreshToken: String): Future[SSOCredentials] = {
    val q =
      tokenRequest.postForm(
        Seq {
          ("grant_type", "refresh_token")
          ("refresh_token", refreshToken)
        }
      )
    this.executeQueryAsync[TokenResponse](q, parseResponse[TokenResponse]).map { res =>
      SSOCredentials(refreshToken, res.access_token)
    }
  }

  def fetchUser(credential: SSOCredentials): Future[EveUserData] = {
    val q =
      Http(baseUrl + "/verify")
        .method("GET")
        .header("User-Agent", userAgent)
        .header("Authorization", s"Bearer ${credential.accessToken}")
    val tokenInfo =
      this.executeQueryAsync[VerifyResponse](q, parseResponse[VerifyResponse])
    for {
      char <- tokenInfo
      exCharInfo <- Future {
        esiCharClient
          .getCharactersCharacterId(
            char.CharacterID.toInt,
            datasource = defaultDatasource
          ) match {
          case Some(resp) => resp
          case None => throw CCPException("Failed to obtain character info from ESI API")
        }
      }
      corp <- Future {
        esiCorpClient
          .getCorporationsCorporationId(
            exCharInfo.corporationId.toInt,
            datasource = defaultDatasource
          ) match {
          case Some(corpResp) => corpResp
          case None => throw CCPException("Failed to obtain corporation info from ESI API")
        }
      }
      alliance <- Future {
        corp.allianceId.flatMap { id =>
          esiAllianceClient
            .getAlliancesAllianceId(
              id.toInt,
              datasource = defaultDatasource
            )
        }
      }
      res <- Future {
        EveUserData(
          char.CharacterID,
          char.CharacterName,
          exCharInfo.corporationId.toLong,
          corp.corporationName,
          corp.allianceId.map(_.toLong),
          alliance.map(_.allianceName)
        )
      }
    } yield res
  }
}
