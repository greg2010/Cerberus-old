package org.red.cerberus.http.endpoints

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import org.red.cerberus.Implicits._
import org.red.cerberus._
import org.red.cerberus.http.{ApacheLog, AuthenticationHandler, AuthorizationHandler, Middleware}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.red.iris.UserMini
import org.red.iris.finagle.clients.UserClient

import scala.concurrent.ExecutionContext


trait Base
  extends ApacheLog
    with Middleware
    with AuthorizationHandler
    with Auth
    with User
    with LazyLogging
    with FailFastCirceSupport {

  def baseRoute(userClient: UserClient, authenticationHandler: AuthenticationHandler)(implicit ec: ExecutionContext): Route = {
    val rejectionHandler = corsRejectionHandler withFallback RejectionHandler.default
    val handleErrors = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)
    handleErrors {
      cors() {
        handleErrors {
          accessLog(logger)(system.dispatcher, timeout, materializer) {
            pathPrefix(cerberusConfig.getString("basePath")) {
              authEndpoints(userClient, authenticationHandler) ~
                authenticateOrRejectWithChallenge(authenticationHandler.authWithCustomJwt _) { userMini: UserMini =>
                  authorize(customAuthorization(userMini) _) {
                    userEndpoints(userClient)(userMini)
                  }
                }
            }
          }
        }
      }
    }
  }
}
