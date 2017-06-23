package org.red.cerberus.exceptions

import scala.util.control.NoStackTrace


case class ConflictingEntityException(reason: String, cause: Option[Exception])
  extends RuntimeException with NoStackTrace