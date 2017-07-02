package org.red.cerberus.http.endpoints

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import moe.pizza.eveapi.ApiKey
import org.red.cerberus._
import org.red.cerberus.controllers.UserController
import org.red.cerberus.external.auth.EveApiClient
import org.red.cerberus.http._
import org.red.cerberus.util.{LegacyCredentials, SSOAuthCode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Auth
  extends AuthenticationHandler
    with LazyLogging
    with FailFastCirceSupport {
  def authEndpoints(implicit userController: UserController, eveApiClient: EveApiClient): Route = pathPrefix("auth") {
    pathPrefix("api") {
      pathPrefix("legacy") {
        (get & parameters("key_id", "verification_code", "name".?)) { (keyId, verificationCode, name) =>
          complete {
            val credentials = LegacyCredentials(ApiKey(keyId.toInt, verificationCode), name)
            eveApiClient.fetchUser(credentials)
              .map(DataResponse.apply)
          }
        }
      }
    } ~
    pathPrefix("login") {
      pathPrefix("legacy") {
        (get & parameters("name_or_email", "password")) { (nameOrEmail, password) =>
          complete {
            userController.verifyUser(nameOrEmail, password).flatMap { userMini =>
              Future {
                DataResponse(
                  TokenResponse(
                    access_token = generateAccessJwt(userMini),
                    refresh_token = generateRefreshJwt(userMini)
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
                  Some(req.name))
              ).map { _ =>
                HttpResponse(StatusCodes.Created)
              }
            }
          }
      } ~
        pathPrefix("sso") {
          (get & parameters("code", "state")) { (code, state) =>
            complete {
              eveApiClient.fetchCredentials(SSOAuthCode(code))
                .flatMap { res =>
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
                  eveApiClient.fetchCredentials(refreshToken).flatMap { creds =>
                    userController.createUser(email, password, creds)
                  }.map { _ =>
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
              userController.requestPasswordReset(passwordResetRequest.email)
                // https://stackoverflow.com/a/33389526
                .map(_ => HttpResponse(StatusCodes.Accepted))
            }
          } ~
            (put & entity(as[passwordChangeWithTokenReq])) { passwordChangeRequest =>
              complete {
                userController.completePasswordReset(
                  passwordChangeRequest.email,
                  passwordChangeRequest.token,
                  passwordChangeRequest.new_password)
                  .map(_ => HttpResponse(StatusCodes.NoContent))
              }
            }
        }
    }
  }
}
