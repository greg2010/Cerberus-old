package org.red.cerberus


trait Responses {

  case class DataResponse[T](data: T)
  case class TokenResponse(access_token: String, refresh_token: String)
}
