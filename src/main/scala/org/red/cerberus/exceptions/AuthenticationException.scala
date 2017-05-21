package org.red.cerberus.exceptions

import scala.util.control.NoStackTrace


case class AuthenticationException(reason: String, sub: String) extends RuntimeException with NoStackTrace
