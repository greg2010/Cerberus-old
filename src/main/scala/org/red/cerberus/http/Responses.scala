package org.red.cerberus.http

import org.red.iris.EveUserData

case class DataResponse[T](data: T)

case class TokenResponse(accessToken: String, refreshToken: String)

case class AccessTokenResponse(accessToken: String)

case class SSOLoginResponse(accessToken: String,
                            refreshToken: String,
                            currentCharacterId: Long)

object SSOLoginResponse {
  def fromTokenResponse(tokenResponse: TokenResponse, currentCharacterId: Long): SSOLoginResponse = {
    SSOLoginResponse(tokenResponse.accessToken, tokenResponse.refreshToken, currentCharacterId)
  }
}

case class TeamspeakRegistrationResponse(expectedNickname: String)

case class ErrorResponse(reason: String, code: Int = 1)

case class EveUserDataResponse(characterId: Long,
                               characterName: String,
                               corporationId: Long,
                               corporationName: String,
                               corporationTicker: String,
                               allianceId: Option[Long],
                               allianceName: Option[String],
                               allianceTicker: Option[String]) {
  def fromIrisEveUserData(eveUserData: EveUserData): EveUserDataResponse = {
    EveUserDataResponse(
      characterId,
      characterName,
      corporationId,
      corporationName,
      corporationTicker,
      allianceId,
      allianceName,
      allianceTicker
    )
  }
}