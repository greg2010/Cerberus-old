package org.red.cerberus.endpoints

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.red.cerberus.Implicits._
import org.red.cerberus._
import org.red.cerberus.controllers.{AuthorizationController, UserController}


trait Base
  extends ApacheLog
  with AuthenticationHandler
  with RouteHelpers
  with Auth
  with User {
  def baseRoute(implicit authorizationController: AuthorizationController, userController: UserController): Route =
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
