package org.red.cerberus.http.endpoints

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import org.red.cerberus.Implicits._
import org.red.cerberus._
import org.red.cerberus.controllers.{AuthorizationController, UserController}
import org.red.cerberus.external.auth.EveApiClient
import org.red.cerberus.http.{ApacheLog, AuthenticationHandler, Middleware}
import org.red.cerberus.util.UserMini

import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport


trait Base
  extends ApacheLog
    with Middleware
    with AuthenticationHandler
    with Auth
    with User
    with LazyLogging
    with FailFastCirceSupport {

  def baseRoute(implicit authorizationController: AuthorizationController, userController: UserController, eveApiClient: EveApiClient): Route = {
    val rejectionHandler = corsRejectionHandler withFallback RejectionHandler.default
    val handleErrors = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)
    handleErrors {
      cors() {
        handleErrors {
          accessLog(logger)(system.dispatcher, timeout, materializer) {
            pathPrefix(cerberusConfig.getString("basePath")) {
              authEndpoints ~
                authenticateOrRejectWithChallenge(authWithCustomJwt _) { userMini: UserMini =>
                  authorizeAsync(authorizationController.customAuthorization(userMini) _) {
                    userEndpoints(userMini)
                  }
                }
            }
          }
        }
      }
    }
  }
}
