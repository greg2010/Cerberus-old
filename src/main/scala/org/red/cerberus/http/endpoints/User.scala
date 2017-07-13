package org.red.cerberus.http.endpoints

import java.net.InetSocketAddress

import akka.http.scaladsl.model.RemoteAddress.IP
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import org.red.cerberus.http.passwordChangeReq
import org.red.iris.{ResourceNotFoundException, UserMini}
import org.red.iris.finagle.clients.{TeamspeakClient, UserClient}

import scala.concurrent.ExecutionContext



trait User
  extends LazyLogging
    with FailFastCirceSupport {
  def userEndpoints(userClient: UserClient, teamspeakClient: TeamspeakClient)(userData: UserMini, address: InetSocketAddress)(implicit ec: ExecutionContext): Route = pathPrefix("user") {
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
              userClient.updatePassword(userData.id, passwordChangeRequest.new_password)
                .map(_ => HttpResponse(StatusCodes.NoContent))
            }
          }
        } ~
        pathPrefix("teamspeak") {
          put {
            complete {
              userClient.getUser(userData.id).flatMap { user =>
                teamspeakClient.registerUserOnTeamspeak(user, address.getAddress.getHostAddress)
              }.map(_ => HttpResponse(StatusCodes.NoContent))
            }
          }
        }
    }
  }
}
