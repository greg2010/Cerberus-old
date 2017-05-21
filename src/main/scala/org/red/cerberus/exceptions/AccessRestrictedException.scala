package org.red.cerberus.exceptions

import scala.util.control.NoStackTrace


case class AccessRestrictedException(reason: String) extends RuntimeException with NoStackTrace
