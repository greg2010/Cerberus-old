package org.red.cerberus.external.auth


import moe.pizza.eveapi.{ApiKey, EVEAPI}
import moe.pizza.eveapi.generated.account.APIKeyInfo.Row
import org.red.cerberus.exceptions.{BadEveCredential, CCPException, ResourceNotFoundException}



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

sealed trait Credentials
case class LegacyCredentials(apiKey: ApiKey, name: String) extends Credentials

sealed trait SSO
case class SSOAuthCode(code: String) extends SSO
case class SSOCredentials(refreshToken: String, accessToken: String) extends Credentials with SSO