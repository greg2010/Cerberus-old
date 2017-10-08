package org.red.cerberus.util.exceptions

case class BadRequestException(reason: String, errorCode: Int = -1) extends RuntimeException(reason)