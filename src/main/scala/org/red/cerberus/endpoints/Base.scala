package org.red.cerberus.endpoints

import org.red.cerberus.Implicits._
import org.red.cerberus._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging


trait Base extends LazyLogging
  with ApacheLog
  with RouteHelpers
  with Auth
  with User {
  val baseRoute =
    accessLog(logger)(system.dispatcher, timeout, materializer) {
      pathPrefix(cerberusConfig.getString("basePath")) {
        authEndpoints ~
          authenticateOrRejectWithChallenge(authWithCustomJwt _) { userData: UserData =>
            userEndpoints(userData)
          }
      }
    }
}
