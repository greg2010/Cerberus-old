package org.red.cerberus.endpoints

import org.red.cerberus.Implicits._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.ApacheLog


trait Base extends LazyLogging with ApacheLog {
  val baseRoute =
    accessLog(logger)(system.dispatcher, timeout, materializer) {
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      }
    }
}
