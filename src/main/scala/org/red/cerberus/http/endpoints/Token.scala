package org.red.cerberus.http.endpoints

import akka.http.scaladsl.server.Directives.{complete, get, parameter, pathPrefix}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.red.cerberus.http.{AccessTokenResponse, AuthenticationHandler, DataResponse}
import io.circe.generic.auto._
import org.red.iris.finagle.clients.UserClient

import scala.concurrent.ExecutionContext

trait Token extends LazyLogging
  with FailFastCirceSupport {
  def tokenEndpoints(userClient: => UserClient, authenticationHandler: => AuthenticationHandler)
                   (implicit ec: ExecutionContext): Route = pathPrefix("token") {
    pathPrefix("refresh") {
      (get & parameter("refreshToken")) { refreshToken =>
        complete {
          authenticationHandler
            .extractPayloadFromToken(refreshToken)
            .flatMap(u => userClient.getUserMini(u.id))
            .map(u => DataResponse(AccessTokenResponse(authenticationHandler.generateAccessJwt(u))))
        }
      }
    }
  }
}
