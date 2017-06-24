package org.red.cerberus


import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.syntax._
import org.red.cerberus.exceptions.{AccessRestrictedException, AuthenticationException, BadEveCredential, ConflictingEntityException}

import scala.language.implicitConversions

trait Middleware extends LazyLogging with FailFastCirceSupport {
  implicit def ErrorResp2ResponseEntity(error: ErrorResponse): ResponseEntity = {
    HttpEntity(ContentType(MediaTypes.`application/json`), error.asJson.toString)
  }

  implicit def exceptionHandler: ExceptionHandler = {
    ExceptionHandler {
      case exc@AuthenticationException(cause, sub) =>
        logger.warn(s"Failed to Authenticate user, offending sub=$sub")
        complete(HttpResponse(StatusCodes.Unauthorized, entity = ErrorResponse(s"Authentication failed, $cause")))
      case exc@ConflictingEntityException(reason, cause) =>
        logger.error(s"Conflicting entity exception $reason", cause)
        complete(HttpResponse(StatusCodes.Conflict, entity = ErrorResponse(s"Error: conflicting entity $reason")))
      case exc@BadEveCredential(offendingCredential, reason, statusCode) =>
        logger.error(s"Bad eve credential, " +
          s"credentialType=${offendingCredential.getClass.getName} " +
          s"reason=$reason " +
          s"statusCode=$statusCode")
        complete(HttpResponse(StatusCodes.BadRequest, entity = ErrorResponse(s"Error: bad credential", statusCode)))
      case exc@AccessRestrictedException(reason) =>
        logger.info("Banned user attempted to login")
        complete(HttpResponse(StatusCodes.UnavailableForLegalReasons, entity = ErrorResponse(reason)))
      case exc: RuntimeException =>
        logger.error(s"Runtime exception caught, msg=${exc.getMessage}", exc)
        complete(HttpResponse(StatusCodes.InternalServerError, entity = ErrorResponse("Internal Server Error")))
    }
  }
}
