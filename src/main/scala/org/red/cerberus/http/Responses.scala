package org.red.cerberus.http

import org.red.iris.EveUserData

case class DataResponse[T](data: T)

case class TokenResponse(access_token: String, refresh_token: String)

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