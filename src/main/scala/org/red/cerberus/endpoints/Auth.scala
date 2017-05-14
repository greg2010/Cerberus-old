package org.red.cerberus.endpoints

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Route
import org.red.cerberus.{AuthenticationHandler, Responses}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._

trait Auth extends AuthenticationHandler with LazyLogging with Responses with FailFastCirceSupport {
  def authEndpoints: Route = pathPrefix("user") {
    pathPrefix("login") {
      pathPrefix("legacy") {
        (get & parameters("name_or_email", "password")) { (nameOrEmail, password) =>
          complete {
            HttpResponse(status = 200)
          }
        } ~
          (post & parameters("key_id", "verification_code", "email", "password")) { (keyId, verificationCode,
                                                                                     email, password) =>
            complete {
              HttpResponse(status = 200)
            }
          }
      } ~
        pathPrefix("sso") {
          (get & parameter("token")) { token =>
            complete {
              HttpResponse(status = 200)
            }
          } ~
            (post & parameters("refresh_token", "email", "password".?)) { (refreshToken, email, password) =>
              complete {
                HttpResponse(status = 200)
              }
            }
        } ~
        pathPrefix("refresh") {
          (get & parameter("refresh_token")) { refreshToken =>
            complete {
              DataResponse(generateAccessJwt(extractPayloadFromToken(refreshToken)))
            }
          }
        }
    }
  }
}
