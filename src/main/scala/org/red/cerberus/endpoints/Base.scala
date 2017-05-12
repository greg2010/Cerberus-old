package org.red.cerberus.endpoints

import org.red.cerberus.Implicits._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.{ApacheLog, AuthenticationHandler, Middleware}


trait Base extends LazyLogging with ApacheLog with AuthenticationHandler with Middleware {
  val baseRoute =
    accessLog(logger)(system.dispatcher, timeout, materializer) {
      pathPrefix("auth") {
        pathPrefix("login") {
          get {
            complete(HttpEntity(encodeJwt(generateAccessPayload(123))))
          }
        }
      } ~
        path("settings") {
          get {
            authenticateOrRejectWithChallenge(authWithCustomJwt _) { (userId: Long) =>
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>Say hello to akka-http, $userId </h1>"))
            }
          }
        }
    }
}
