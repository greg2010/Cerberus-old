package org.red.cerberus.http.endpoints

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import org.red.cerberus.Implicits._
import org.red.cerberus._
import org.red.cerberus.http.{ApacheLog, AuthenticationHandler, Middleware}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.red.cerberus.finagle.UserClient
import org.red.iris.UserMini


trait Base
  extends ApacheLog
    with Middleware
    with AuthenticationHandler
    with Auth
    with User
    with LazyLogging
    with FailFastCirceSupport {

  def baseRoute(userClient: UserClient): Route = {
    val rejectionHandler = corsRejectionHandler withFallback RejectionHandler.default
    val handleErrors = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)
    handleErrors {
      cors() {
        handleErrors {
          accessLog(logger)(system.dispatcher, timeout, materializer) {
            pathPrefix(cerberusConfig.getString("basePath")) {
              authEndpoints(userClient) ~
                authenticateOrRejectWithChallenge(authWithCustomJwt _) { userMini: UserMini =>
                  authorizeAsync(authorizationController.customAuthorization(userMini) _) {
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
