package org.red.cerberus.external.auth


import moe.pizza.eveapi.{ApiKey, EVEAPI}
import moe.pizza.eveapi.generated.account.APIKeyInfo.Row
import org.red.cerberus.exceptions.{BadEveCredential, CCPException, ResourceNotFoundException}



case class EveUserData(characterId: Long,
                       characterName: String,
                       corporationId: Long,
                       corporationName: String,
                       corporationTicker: String,
                       allianceId: Option[Long],
                       allianceName: Option[String],
                       allianceTicker: Option[String])

sealed trait Credentials
case class LegacyCredentials(apiKey: ApiKey, name: String) extends Credentials

sealed trait SSO
case class SSOAuthCode(code: String) extends SSO
case class SSOCredentials(refreshToken: String, accessToken: String) extends Credentials with SSO