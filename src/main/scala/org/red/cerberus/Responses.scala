package org.red.cerberus


trait Responses {

  case class DataResponse[T](data: T)

}
