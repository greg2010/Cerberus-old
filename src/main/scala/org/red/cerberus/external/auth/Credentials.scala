package org.red.cerberus.external.auth

import moe.pizza.eveapi.{ApiKey, EVEAPI}
import moe.pizza.eveapi.generated.account.APIKeyInfo.Row
import org.red.cerberus.exceptions.ResourceNotFoundException

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
        throw ResourceNotFoundException(s"One of mandatory XML API fields returned null ${ex.getMessage}")
    }
  }
}

sealed trait Credentials {
  def fetchUser: Future[EveUserData]
}

case class LegacyCredentials(apiKey: ApiKey, name: String) extends Credentials {
  override lazy val fetchUser: Future[EveUserData] = {
    val xmlapi = new EVEAPI()(Some(apiKey), global)
    xmlapi.account.APIKeyInfo().flatMap {
      case Success(res) if res.result.key.accessMask.toString > "DEFAULT_ACCESS_MASK" =>
        res.result.key.rowset.row.find(_.characterName == name) match {
          case Some(ch) => Future(EveUserData(ch))
          case None => throw ResourceNotFoundException(s"Character $name not found")
        }
      case Failure(ex) => throw new RuntimeException("Bad key") // FIXME: change exception type
      case _ => throw new RuntimeException("Bad mask")
    }
  }
}

case class SSOCredentials(refreshToken: String) extends Credentials {
  override lazy val fetchUser: Future[EveUserData] = {
    // do things
    Future(EveUserData(1, "", 1, "", Some(1), Some("")))
  }
}