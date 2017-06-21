package org.red.cerberus.endpoints

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import org.red.cerberus._

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Directives._
import io.circe.generic.auto._
import moe.pizza.eveapi.ApiKey
import org.red.cerberus.controllers.UserController
import org.red.cerberus.external.auth.{LegacyCredentials, SSOAuthCode, SSOCredential}

import scala.concurrent.Future

trait Auth extends RouteHelpers with AuthenticationHandler {
  def authEndpoints(implicit userController: UserController): Route = pathPrefix("auth") {
    pathPrefix("login") {
      pathPrefix("legacy") {
        (get & parameters("name_or_email", "password")) { (nameOrEmail, password) =>
          complete {
            userController.legacyLogin(nameOrEmail, password).flatMap { userData =>
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
                userController.createUser(req.email, Some(req.password),
                  LegacyCredentials(
                    ApiKey(req.key_id.toInt, req.verification_code),
                    req.name)
                ).map { _ =>
                  HttpResponse(StatusCodes.Created)
                }
              }
          }
      } ~
        pathPrefix("sso") {
          (get & parameters("code", "state")) { (code, state) =>
            complete {
              SSOAuthCode(code).exchangeCode.flatMap { res =>
                userController.createUser("", None, res)
              }
            }
          } ~
          (get & parameter("token")) { token =>
            complete {
              HttpResponse(StatusCodes.OK)
            }
          } ~
            (post & parameters("refresh_token", "email", "password".?)) {
              (refreshToken, email, password) =>
                complete {
                  userController.createUser(email, password, SSOCredential(refreshToken)
                  ).map { _ =>
                    HttpResponse(StatusCodes.Created)
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
        } ~
        pathPrefix("password") {
          (post & entity(as[passwordResetRequestReq])) { passwordResetRequest =>
            complete {
              userController.initializePasswordReset(passwordResetRequest.email)
                // https://stackoverflow.com/a/33389526
                .map(_ => HttpResponse(StatusCodes.Accepted))
            }
          } ~
            (put & entity(as[passwordChangeWithTokenReq])) { passwordChangeRequest =>
              // TODO: implement token check and password reset logic
              complete {
                HttpResponse(StatusCodes.OK)
              }
            }
        }
    }
  }
}
