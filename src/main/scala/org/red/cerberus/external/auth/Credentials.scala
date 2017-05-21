package org.red.cerberus.external.auth

import moe.pizza.eveapi.{ApiKey, EVEAPI}
import moe.pizza.eveapi.generated.account.APIKeyInfo.Row
import org.red.cerberus.exceptions.{BadEveCredential, CCPException, ResourceNotFoundException}
import org.red.cerberus.cerberusConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


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

case class LegacyCredentials(apiKey: ApiKey, name: String) extends Credentials {
  private val minimumMask: Int = cerberusConfig.getInt("legacyAPI.minimumKeyMask")
  lazy val client = new EVEAPI()(Some(apiKey), global)
  override lazy val fetchUser: Future[EveUserData] = {
    client.account.APIKeyInfo().flatMap {
      case Success(res) if (res.result.key.accessMask & minimumMask) == minimumMask =>
        res.result.key.rowset.row.find(_.characterName == name) match {
          case Some(ch) => Future(EveUserData(ch))
          case None => throw ResourceNotFoundException(s"Character $name not found")
        }
      case Failure(ex) => throw BadEveCredential(this, "Invalid key", -2)
      case _ => throw BadEveCredential(this, "Invalid mask", -1)
    }
  }
}

case class SSOCredentials(refreshToken: String, accessToken: Option[String] = None) extends Credentials {
  override lazy val fetchUser: Future[EveUserData] = {
    Future(EveUserData(1, "", 1, "", Some(1), Some("")))
  }
}