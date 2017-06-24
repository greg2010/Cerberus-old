package org.red.cerberus.endpoints

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import org.red.cerberus.controllers.UserController
import org.red.cerberus.{RouteHelpers, UserData, passwordChangeReq}

import scala.concurrent.ExecutionContext.Implicits.global


trait User extends RouteHelpers {
  def userEndpoints(userData: UserData)(implicit userController: UserController): Route = pathPrefix("user") {
    pathPrefix("self") {
      pathPrefix("logout") {
        post {
          complete {
            HttpResponse(StatusCodes.OK)
          }
        }
      } ~
        pathPrefix("password") {
          (put & entity(as[passwordChangeReq])) { passwordChangeRequest =>
            complete {
              userController.updatePassword(userData.id, passwordChangeRequest.new_password)
                .map(_ => HttpResponse(StatusCodes.NoContent))
            }
          }
        }
    }
  }
}
