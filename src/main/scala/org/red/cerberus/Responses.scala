package org.red.cerberus


case class DataResponse[T](data: T)
case class TokenResponse(access_token: String, refresh_token: String)
case class ErrorResponse(reason: String, code: Int = 1)
