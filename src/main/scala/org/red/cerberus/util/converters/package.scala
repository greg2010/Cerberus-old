package org.red.cerberus.util

import org.red.cerberus.http.EveUserDataResponse
import org.red.iris.{EveUserData, EveUserDataList}

import scala.language.implicitConversions


package object converters {
  implicit class RichEveUserDataList(val eveUserDataList: EveUserDataList) extends AnyVal {
    implicit def toSeq: Seq[EveUserData] = {
      eveUserDataList.head +: eveUserDataList.tail
    }
  }

  implicit class RichEveUserData(val eveUserData: EveUserData) extends AnyVal {
    implicit def toResponse: EveUserDataResponse = {
      EveUserDataResponse(
        eveUserData.characterId,
        eveUserData.characterName,
        eveUserData.corporationId,
        eveUserData.corporationName,
        eveUserData.corporationTicker,
        eveUserData.allianceId,
        eveUserData.allianceName,
        eveUserData.allianceTicker
      )
    }
  }
}
