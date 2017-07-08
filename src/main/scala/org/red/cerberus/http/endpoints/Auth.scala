package org.red.cerberus.http.endpoints

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.red.cerberus.util.converters._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import org.red.cerberus.http._
import org.red.iris.finagle.clients.UserClient
import org.red.iris.{LegacyCredentials, SSOCredentials}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Auth
  extends AuthenticationHandler
    with LazyLogging
    with FailFastCirceSupport {
  def authEndpoints(userClient: UserClient): Route = pathPrefix("auth") {
    pathPrefix("api") {
      pathPrefix("legacy") {
        (get & parameters("key_id", "verification_code", "name".?)) { (keyId, verificationCode, name) =>
          complete {
            val credentials = LegacyCredentials(keyId.toInt, verificationCode, None, name)
            userClient.getEveUser(credentials)
              .map(nonEmptyList => DataResponse.apply(nonEmptyList.toSeq.map(_.toResponse)))
          }
        }
      }
    } ~
    pathPrefix("login") {
      pathPrefix("legacy") {
        (get & parameters("name_or_email", "password")) { (nameOrEmail, password) =>
          complete {
            userClient.verifyUserLegacy(nameOrEmail, password).flatMap { userMini =>
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
              userClient.createLegacyUser(req.email,
                LegacyCredentials(req.key_id.toInt, req.verification_code, None, Some(req.name)), req.password)
                .map { _ =>
                HttpResponse(StatusCodes.Created)
              }
            }
          }
      } ~
        pathPrefix("sso") {
          (get & parameters("code", "state")) { (code, state) =>
            complete {
              StatusCodes.OK
              // TODO: figure out what to do with this endpoint
              /*eveApiClient.fetchCredentials(SSOAuthCode(code))
                .flatMap { res =>
                  userController.createUser("", None, res)
                }*/
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
                  userClient.createSSOUser(email, SSOCredentials(refreshToken, None), password)
                  .map { _ =>
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
              userClient.requestPasswordReset(passwordResetRequest.email)
                // https://stackoverflow.com/a/33389526
                .map(_ => HttpResponse(StatusCodes.Accepted))
            }
          } ~
            (put & entity(as[passwordChangeWithTokenReq])) { passwordChangeRequest =>
              complete {
                userClient.completePasswordReset(
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
