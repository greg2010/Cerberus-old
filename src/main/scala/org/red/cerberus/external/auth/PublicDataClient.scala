package org.red.cerberus.external.auth

import org.red.cerberus.exceptions.CCPException
import org.red.eveapi.esi.api.{AllianceApi, CharacterApi, CorporationApi}

import scala.concurrent.Future

private[this] class PublicDataClient {
  private val defaultDatasource = Some("tranquility")
  private lazy val esiCharClient = new CharacterApi
  private lazy val esiCorpClient = new CorporationApi
  private lazy val esiAllianceClient = new AllianceApi

  def fetchUserByCharacterId(characterId: Long): Future[EveUserData] = {
    for {
      extraCharInfo <- Future {
        esiCharClient
          .getCharactersCharacterId(
            characterId.toInt,
            datasource = defaultDatasource
          ) match {
          case Some(resp) => resp
          case None => throw CCPException("Failed to obtain character info from ESI API")
        }
      }
      corp <- Future {
        esiCorpClient
          .getCorporationsCorporationId(
            extraCharInfo.corporationId.toInt,
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
          characterId,
          extraCharInfo.name,
          extraCharInfo.corporationId.toLong,
          corp.corporationName,
          corp.ticker,
          corp.allianceId.map(_.toLong),
          alliance.map(_.allianceName),
          alliance.map(_.ticker)
        )
      }
    } yield res
  }
}
