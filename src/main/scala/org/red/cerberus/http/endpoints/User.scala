package org.red.cerberus.http.endpoints

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import org.red.cerberus.controllers.UserController
import org.red.cerberus.http.passwordChangeReq
import org.red.cerberus.util.UserMini

import scala.concurrent.ExecutionContext.Implicits.global


trait User
  extends LazyLogging
    with FailFastCirceSupport {
  def userEndpoints(userData: UserMini)(implicit userController: UserController): Route = pathPrefix("user") {
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
