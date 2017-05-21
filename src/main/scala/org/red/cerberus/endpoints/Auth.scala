package org.red.cerberus.endpoints

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Route
import org.red.cerberus._

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Directives._
import io.circe.generic.auto._
import moe.pizza.eveapi.ApiKey
import org.red.cerberus.controllers.UserController
import org.red.cerberus.external.auth.{LegacyCredentials, SSOAuthCode, SSOCredential}

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
          (post & entity(as[LegacySignupReq])) { req =>
              complete {
                UserController.createUser(req.email, Some(req.password),
                  LegacyCredentials(
                    ApiKey(req.key_id.toInt, req.verification_code),
                    req.name)
                ).map { _ =>
                  HttpResponse(status = 201)
                }
              }
          }
      } ~
        pathPrefix("sso") {
          (get & parameters("code", "state")) { (code, state) =>
            complete {
              SSOAuthCode(code).exchangeCode.flatMap { res =>
                UserController.createUser("", None, res)
              }
            }
          } ~
          (get & parameter("token")) { token =>
            complete {
              HttpResponse(status = 200)
            }
          } ~
            (post & parameters("refresh_token", "email", "password".?)) {
              (refreshToken, email, password) =>
                complete {
                  UserController.createUser(email, password, SSOCredential(refreshToken)
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
