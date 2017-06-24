package org.red.cerberus.endpoints

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import org.red.cerberus.Implicits._
import org.red.cerberus._
import org.red.cerberus.controllers.{AuthorizationController, UserController}
import org.red.cerberus.external.auth.EveApiClient


trait Base
  extends ApacheLog
    with Middleware
    with AuthenticationHandler
    with RouteHelpers
    with Auth
    with User {

  def baseRoute(implicit authorizationController: AuthorizationController, userController: UserController, eveApiClient: EveApiClient): Route = {
    val rejectionHandler = corsRejectionHandler withFallback RejectionHandler.default
    val handleErrors = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)
    handleErrors {
      cors() {
        handleErrors {
          accessLog(logger)(system.dispatcher, timeout, materializer) {
            pathPrefix(cerberusConfig.getString("basePath")) {
              authEndpoints ~
                authenticateOrRejectWithChallenge(authWithCustomJwt _) { userData: UserData =>
                  authorizeAsync(authorizationController.customAuthorization(userData) _) {
                    userEndpoints(userData)
                  }
                }
            }
          }
        }
      }
    }
  }
}
