package org.red.cerberus.http.endpoints

import java.net.InetSocketAddress

import akka.http.scaladsl.model.RemoteAddress.IP
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import org.red.cerberus.util.converters._
import org.red.cerberus.http.{DataResponse, ErrorResponse, TeamspeakRegistrationResponse, passwordChangeReq}
import org.red.cerberus.util.exceptions.BadRequestException
import org.red.iris.{ResourceNotFoundException, UserMini}
import org.red.iris.finagle.clients.{TeamspeakClient, UserClient}

import scala.concurrent.{ExecutionContext, Future}



trait User
  extends LazyLogging
    with FailFastCirceSupport {
  def userEndpoints(userClient: => UserClient, teamspeakClient: => TeamspeakClient)(userData: UserMini, address: InetSocketAddress)(implicit ec: ExecutionContext): Route = pathPrefix("user") {
    pathPrefix("self") {
      pathPrefix("logout") {
        post {
          complete {
            HttpResponse(StatusCodes.OK)
          }
        }
      } ~
        pathPrefix("eve") {
          get {
            complete {
              userClient.getUser(userData.id).map { user =>
                val resp = Seq(user.eveUserDataList.head) ++ user.eveUserDataList.tail
                DataResponse(resp.map(_.toResponse))
              }
            }
          }
        } ~
        pathPrefix(LongNumber) { characterId =>
          pathPrefix("teamspeak") {
            put {
              complete {
                userClient.getUser(userData.id).flatMap { user =>
                  if ((user.eveUserDataList.tail :+ user.eveUserDataList.head).exists(_.characterId == characterId)) {
                    teamspeakClient.registerUserOnTeamspeak(user, characterId, address.getAddress.getHostAddress)
                      .map(r => DataResponse(TeamspeakRegistrationResponse(r)))
                  } else {
                    Future.failed(BadRequestException("Account doesn't own such eve user"))
                  }
                }
              }
            }
          }
        }
    }
  }
}
