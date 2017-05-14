package org.red.cerberus.exceptions


case class ResourceNotFoundException(reason: String) extends RuntimeException
