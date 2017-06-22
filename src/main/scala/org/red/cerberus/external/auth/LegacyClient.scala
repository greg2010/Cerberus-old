package org.red.cerberus.external.auth

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import moe.pizza.eveapi.EVEAPI
import org.red.cerberus.exceptions.{BadEveCredential, ResourceNotFoundException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


private[this] class LegacyClient(config: Config, publicDataClient: PublicDataClient) extends LazyLogging {
  private val minimumMask: Int = config.getInt("legacyAPI.minimumKeyMask")

  def fetchUser(legacyCredentials: LegacyCredentials): Future[EveUserData] = {
    lazy val client = new EVEAPI()(Some(legacyCredentials.apiKey), global)
    val f = client.account.APIKeyInfo().flatMap {
      case Success(res) if (res.result.key.accessMask & minimumMask) == minimumMask =>
        res.result.key.rowset.row.find(_.characterName == legacyCredentials.name) match {
          case Some(ch) => publicDataClient.fetchUserByCharacterId(ch.characterID.toLong)
          case None => throw ResourceNotFoundException(s"Character ${legacyCredentials.name} not found")
        }
      case Failure(ex) => throw BadEveCredential(legacyCredentials, "Invalid key", -2)
      case _ => throw BadEveCredential(legacyCredentials, "Invalid mask", -1)
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
