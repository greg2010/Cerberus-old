package org.red.cerberus.endpoints

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.red.cerberus.{AuthenticationHandler, Responses}


trait User extends AuthenticationHandler with FailFastCirceSupport with Responses {
  def userEndpoints(userData: UserData): Route = pathPrefix("user") {
    path("logout") {
      post {
        complete {
          HttpResponse(status = 200)
        }
      }
    }
  }
}
