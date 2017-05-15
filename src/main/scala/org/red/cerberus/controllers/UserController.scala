package org.red.cerberus.controllers

import com.typesafe.scalalogging.LazyLogging
import moe.pizza.eveapi.{ApiKey, EVEAPI}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


object UserController extends LazyLogging {

  def createLegacyUser(name: String,
                       email: String,
                       keyId: Int,
                       verificationCode: String): Future[Boolean] = {
    val xmlapi = new EVEAPI()(Some(ApiKey(keyId, verificationCode)), global)
    xmlapi.account.APIKeyInfo().flatMap {
      case Success(res) if res.result.key.accessMask.toString > "DEFAULT_ACCESS_MASK" =>
        res.result.key.rowset.row.find(_.characterName == name) match {
          case Some(ch) =>
            createUser(
              ch.characterName,
              ch.characterID.toLong,
              ch.corporationName,
              ch.corporationID.toLong,
              Some(ch.allianceName), // FIXME: figure out what lib does when there's no ally name/id
              Some(ch.allianceID.toLong))
        }
      case Failure(ex) => throw new RuntimeException("Bad key") // FIXME: change exception type
      case _ => throw new RuntimeException("Bad mask")
    }
  }

  def createUser(characterName: String,
                 characterId: Long,
                 corporationName: String,
                 corporationId: Long,
                 allianceName: Option[String],
                 allianceId: Option[Long]): Future[Boolean] = {
    Future(true)
  }
}
