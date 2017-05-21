package org.red.cerberus.exceptions


case class CCPException(reason: String, cause: Option[Exception] = None) extends RuntimeException