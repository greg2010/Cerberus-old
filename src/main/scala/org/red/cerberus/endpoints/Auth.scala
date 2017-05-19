package org.red.cerberus.endpoints

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Route
import org.red.cerberus.{AuthenticationHandler, Responses, RouteHelpers}
import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Directives._
import io.circe.generic.auto._
import moe.pizza.eveapi.ApiKey
import org.red.cerberus.controllers.UserController
import org.red.cerberus.external.auth.{LegacyCredentials, SSOCredentials}

import scala.concurrent.Future

trait Auth extends RouteHelpers {
  def authEndpoints: Route = pathPrefix("user") {
    pathPrefix("login") {
      pathPrefix("legacy") {
        (get & parameters("name_or_email", "password")) { (nameOrEmail, password) =>
          complete {
            UserController.legacyLogin(nameOrEmail, password).flatMap { userData =>
              Future {
                DataResponse(
                  TokenResponse(
                    access_token = generateAccessJwt(userData),
                    refresh_token = generateRefreshJwt(userData)
                  )
                )
              }
            }
          }
        } ~
          (post & parameters("key_id", "verification_code", "name", "email", "password")) {
            (keyId, verificationCode, name, email, password) =>
              complete {
                UserController.createUser(email, Some(password),
                  LegacyCredentials(
                    ApiKey(keyId.toInt, verificationCode),
                    name)
                ).map { _ =>
                  HttpResponse(status = 201)
                }
              }
          }
      } ~
        pathPrefix("sso") {
          (get & parameter("token")) { token =>
            complete {
              HttpResponse(status = 200)
            }
          } ~
            (post & parameters("refresh_token", "email", "password".?)) {
              (refreshToken, email, password) =>
                complete {
                  UserController.createUser(email, password, SSOCredentials(refreshToken)
                  ).map { _ =>
                    HttpResponse(status = 201)
                  }
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
