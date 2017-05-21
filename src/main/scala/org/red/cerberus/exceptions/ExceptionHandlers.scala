package org.red.cerberus.exceptions

import org.postgresql.util.PSQLException

import scala.concurrent.Future


object ExceptionHandlers {
  def dbExceptionHandler[T]: PartialFunction[Throwable, Future[T]] = {
    // Conflicting entities (eg duplicate unique key)
    case ex: PSQLException if ex.getSQLState == "23505" =>
      throw ConflictingEntityException(ex.getServerErrorMessage.getMessage, ex)
  }
}
