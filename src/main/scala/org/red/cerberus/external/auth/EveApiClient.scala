package org.red.cerberus.external.auth

import cats.data.NonEmptyList
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.util._

import scala.concurrent.{ExecutionContext, Future}


class EveApiClient(config: Config)(implicit ec: ExecutionContext) extends LazyLogging {
  private val publicDataClient = new PublicDataClient
  private val legacyClient = new LegacyClient(config, publicDataClient)
  private val ssoClient = new SSOClient(config, publicDataClient)


  def fetchUserByCharacterId(characterId: Long): Future[EveUserData] = {
    publicDataClient.fetchUserByCharacterId(characterId)
  }

  def fetchUser(credentials: Credentials): Future[NonEmptyList[EveUserData]] = {
    credentials match {
      case legacyCredentials: LegacyCredentials => legacyClient.fetchUser(legacyCredentials)
      case ssoCredentials: SSOCredentials => ssoClient.fetchUser(ssoCredentials)
        .map(x => NonEmptyList(x, List()))
    }
  }

  def fetchCredentials(ssoAuthCode: SSOAuthCode): Future[SSOCredentials] = {
    ssoClient.fetchSSOCredential(ssoAuthCode)
  }

  def fetchCredentials(refreshToken: String): Future[SSOCredentials] = {
    ssoClient.createSSOCredential(refreshToken)
  }

  def fetchUserAndCredentials(ssoAuthCode: SSOAuthCode): Future[(SSOCredentials, EveUserData)] = {
    for {
      creds <- this.fetchCredentials(ssoAuthCode)
      data <- ssoClient.fetchUser(creds)
    } yield (creds, data)
  }

  def fetchUserAndCredentials(refreshToken: String): Future[(SSOCredentials, EveUserData)] = {
    for {
      creds <- this.fetchCredentials(refreshToken)
      data <- ssoClient.fetchUser(creds)
    } yield (creds, data)
  }
}
