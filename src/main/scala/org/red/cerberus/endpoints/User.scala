package org.red.cerberus.endpoints

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.red.cerberus.{RouteHelpers, UserData, passwordChangeReq}
import io.circe.generic.auto._


trait User extends RouteHelpers {
  def userEndpoints(userData: UserData): Route = pathPrefix("user") {
    pathPrefix("self") {
      pathPrefix("logout") {
        post {
          complete {
            HttpResponse(status = 200)
          }
        }
      } ~
      pathPrefix("password") {
        (put & entity(as[passwordChangeReq])) { passwordChangeRequest =>
          // TODO: implement password change logic
          complete {
            HttpResponse(StatusCodes.OK)
          }
        }
      }
    }
  }
}
