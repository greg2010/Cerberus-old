package org.red.cerberus.exceptions

import org.red.cerberus.external.auth.Credentials

import scala.util.control.NoStackTrace

case class BadEveCredential(offendingCredential: Credentials, reason: String, statusCode: Int)
  extends RuntimeException with NoStackTrace
