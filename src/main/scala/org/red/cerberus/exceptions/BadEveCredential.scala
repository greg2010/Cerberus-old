package org.red.cerberus.exceptions

import org.red.cerberus.util.Credentials

import scala.util.control.NoStackTrace

case class BadEveCredential(offendingCredential: Credentials, reason: String, statusCode: Int)
  extends RuntimeException with NoStackTrace
