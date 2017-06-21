package org.red.cerberus.external.auth

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class EveApiClient(config: Config) extends LazyLogging {
  private val legacyClient = new LegacyClient(config)
  private val ssoClient = new SSOClient(config)

  def fetchUser(credentials: Credentials): Future[EveUserData] = {
    credentials match {
      case legacyCredentials: LegacyCredentials => legacyClient.fetchUser(legacyCredentials)
      case ssoCredentials: SSOCredentials => ssoClient.fetchUser(ssoCredentials)
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
