package org.red.cerberus


import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.exceptions.AuthenticationException

trait Middleware extends LazyLogging {
  implicit def exceptionHandler: ExceptionHandler = {
    ExceptionHandler {
      case exc @ AuthenticationException(cause, sub) =>
        logger.warn(s"Failed to Authenticate user, offending sub=$sub", exc)
        complete(HttpResponse(StatusCodes.Unauthorized, entity = s"Authentication failed, $cause"))
    }
  }
}
