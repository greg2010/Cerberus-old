package org.red.cerberus.external.auth

import java.io.InputStream

import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, parser}
import io.circe.generic.auto._
import moe.pizza.eveapi.{ApiKey, EVEAPI}
import moe.pizza.eveapi.generated.account.APIKeyInfo.Row
import org.red.cerberus.exceptions.{BadEveCredential, CCPException, ResourceNotFoundException}
import org.red.cerberus.cerberusConfig
import org.red.eveapi.esi.api.{AllianceApi, CharacterApi, CorporationApi}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaj.http.{Http, HttpRequest}


case class EveUserData(characterId: Long,
                       characterName: String,
                       corporationId: Long,
                       corporationName: String,
                       allianceId: Option[Long],
                       allianceName: Option[String])

object EveUserData {
  def apply(eveApiRow: Row): EveUserData = {
    try {
      EveUserData(
        characterId = eveApiRow.characterID.toLong,
        characterName = eveApiRow.characterName,
        corporationId = eveApiRow.corporationID.toLong,
        corporationName = eveApiRow.corporationName,
        allianceId = Option(eveApiRow.allianceID.toLong),
        allianceName = Option(eveApiRow.allianceName)
      )
    } catch {
      case ex: NullPointerException =>
        throw CCPException(s"One of mandatory XML API fields returned null ${ex.getMessage}")
    }
  }
}

sealed trait Credentials {
  def fetchUser: Future[EveUserData]
}
sealed trait SSO {
  protected val defaultDatasource = Some("tranquility")
  protected val baseUrl = "https://login.eveonline.com/oauth"
  protected val userAgent = "red-cerberus/1.0"
  protected lazy val tokenRequest: HttpRequest =
    Http(baseUrl + "/token")
      .method("POST")
      .header("User-Agent", userAgent)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .auth(cerberusConfig.getString("SSOClientId"), cerberusConfig.getString("SSOClientSecret"))

  protected case class TokenResponse(access_token: String,
                                   token_type: String,
                                   expires_in: String,
                                   refresh_token: String)

  protected def parseResponse[T](respCode: Int,
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
}

case class LegacyCredentials(apiKey: ApiKey, name: String) extends Credentials with LazyLogging {
  private val minimumMask: Int = cerberusConfig.getInt("legacyAPI.minimumKeyMask")
  lazy val client = new EVEAPI()(Some(apiKey), global)
  override lazy val fetchUser: Future[EveUserData] = {
    val f = client.account.APIKeyInfo().flatMap {
      case Success(res) if (res.result.key.accessMask & minimumMask) == minimumMask =>
        res.result.key.rowset.row.find(_.characterName == name) match {
          case Some(ch) => Future(EveUserData(ch))
          case None => throw ResourceNotFoundException(s"Character $name not found")
        }
      case Failure(ex) => throw BadEveCredential(this, "Invalid key", -2)
      case _ => throw BadEveCredential(this, "Invalid mask", -1)
    }
    f.onComplete {
      case Success(res) =>
        logger.info(s"Fetched user using legacy API " +
          s"characterId=${res.characterId} " +
          s"event=external.auth.legacy.fetch.success")
      case Failure(ex) =>
        logger.error(s"Failed to fetch user using legacy PI " +
          s"event=external.auth.legacy.fetch.failure", ex)
    }
    f
  }
}

case class SSOAuthCode(code: String) extends SSO {

  lazy val exchangeCode: Future[SSOCredential] = {
    Future {
      tokenRequest.postForm(
        Seq {
          ("grant_type", "authorization_code")
          ("code", code)
        }
      ).exec[TokenResponse](parseResponse[TokenResponse]).body
    }.map { res =>
      SSOCredential(res.refresh_token, Some(res.access_token))
    }
  }
}

case class SSOCredential(refreshToken: String, accessToken: Option[String] = None) extends Credentials with SSO {
  private lazy val esiCharClient = new CharacterApi()
  private lazy val esiCorpClient = new CorporationApi()
  private lazy val esiAllianceClient = new AllianceApi()
  private case class VerifyResponse(CharacterID: Long,
                                    CharacterName: String,
                                    ExpiresOn: String,
                                    Scopes: String,
                                    TokenType: String,
                                    CharacterOwnerHash: String)

  lazy val obtainedAccessToken: String = {
    case class accessTokenResponse()
    tokenRequest
      .postForm(
        Seq {
          ("grant_type","refresh_token")
          ("refresh_token", refreshToken)
        }
      ).exec[TokenResponse](parseResponse[TokenResponse]).body.access_token
  }

  private lazy val accessTokenInternal: String = {
    accessToken match {
      case None => obtainedAccessToken
      case Some(token) => token
    }
  }

  override lazy val fetchUser: Future[EveUserData] = {
    val tokenInfo = Future {
      Http(baseUrl + "/verify")
        .method("GET")
        .header("User-Agent", userAgent)
        .header("Authorization", s"Bearer $accessTokenInternal")
        .exec[VerifyResponse](parseResponse[VerifyResponse]).body
    }
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