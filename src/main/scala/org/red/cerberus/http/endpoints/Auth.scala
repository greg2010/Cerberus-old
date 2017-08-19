package org.red.cerberus.http.endpoints

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.red.cerberus.util.converters._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import org.red.cerberus.http._
import org.red.iris.finagle.clients.UserClient
import org.red.iris.LegacyCredentials

import scala.concurrent.ExecutionContext

trait Auth
  extends LazyLogging
    with FailFastCirceSupport {
  def authEndpoints(userClient: UserClient, authenticationHandler: AuthenticationHandler)
                   (implicit ec: ExecutionContext): Route = pathPrefix("auth") {
    pathPrefix("api") {
      pathPrefix("legacy") {
        (get & parameters("keyId", "verificationCode", "name".?)) { (keyId, verificationCode, name) =>
          complete {
            val credentials = LegacyCredentials(keyId.toInt, verificationCode, None, name)
            userClient.getEveUser(credentials)
              .map(nonEmptyList => DataResponse.apply(nonEmptyList.toSeq.map(_.toResponse)))
          }
        }
      }
    } ~
    pathPrefix("login") {
      pathPrefix("sso") {
        (post & entity(as[SSOLoginReq])) { ssoLoginReq =>
          complete {
            userClient.loginSSO(ssoLoginReq.authCode)
              .map(resp => DataResponse(SSOLoginResponse.fromTokenResponse(authenticationHandler.dataResponseFromUserMini(resp.userMini), resp.currentUser)))
          }
        }
      }
    }
  }
}
