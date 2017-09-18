package org.red.cerberus.http

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import com.twitter.finagle._
import io.circe.generic.auto._
import io.circe.syntax._
import org.red.iris._

import scala.language.implicitConversions

trait Middleware extends LazyLogging with FailFastCirceSupport {
  implicit def ErrorResp2ResponseEntity(error: ErrorResponse): ResponseEntity = {
    HttpEntity(ContentType(MediaTypes.`application/json`), error.asJson.toString)
  }

  implicit def exceptionHandler: ExceptionHandler = {
    ExceptionHandler {
      case exc@ResourceNotFoundException(reason) =>
        logger.warn(s"Got resourceNotFoundException with reason $reason")
        complete(StatusCodes.NotFound)
      case exc@AuthenticationException(cause, sub) =>
        logger.warn(s"Failed to Authenticate user, offending sub=$sub")
        complete(HttpResponse(StatusCodes.Unauthorized, entity = ErrorResponse(s"Authentication failed, $cause")))
      case exc@ConflictingEntityException(reason) =>
        logger.error(s"Conflicting entity exception $reason")
        complete(HttpResponse(StatusCodes.Conflict, entity = ErrorResponse(s"Error: conflicting entity $reason")))
      case exc@BadEveCredential(reason, statusCode) =>
        logger.error(s"Bad eve credential, " +
          s"reason=$reason " +
          s"statusCode=$statusCode")
        complete(HttpResponse(StatusCodes.BadRequest, entity = ErrorResponse(s"Error: bad credential", statusCode)))
      case exc@CCPException(reason) =>
        logger.error("CCP Exception, " +
          s"reason=$reason")
        complete(HttpResponse(StatusCodes.ServiceUnavailable, entity = ErrorResponse("Error: CCP exception")))
      case exc: IndividualRequestTimeoutException =>
        logger.error(s"One of the thrift services timed out thriftServiceName=${exc.serviceName}", exc)
        complete(HttpResponse(StatusCodes.BadGateway, entity = ErrorResponse("Service is not available")))
      case exc: com.twitter.finagle.Failure =>
        logger.error(s"One of the thrift services is failed cause=${exc.cause}", exc)
        complete(HttpResponse(StatusCodes.BadGateway, entity = ErrorResponse("Service is not available")))
      case exc@AccessRestrictedException(reason) =>
        logger.info("Banned user attempted to login")
        complete(HttpResponse(StatusCodes.UnavailableForLegalReasons, entity = ErrorResponse(reason)))
      case exc: RuntimeException =>
        logger.error(s"Runtime exception caught, msg=${exc.getMessage}", exc)
        complete(HttpResponse(StatusCodes.InternalServerError, entity = ErrorResponse("Internal Server Error")))
    }
  }
}
