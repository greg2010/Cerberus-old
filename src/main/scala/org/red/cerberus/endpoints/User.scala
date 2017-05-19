package org.red.cerberus.endpoints

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.red.cerberus.{AuthenticationHandler, Responses, RouteHelpers, UserData}


trait User extends RouteHelpers {
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
