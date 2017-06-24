package org.red.cerberus.util

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.yaml.parser

import scala.io.BufferedSource


object YamlParser extends LazyLogging {
  def parseResource[T](bufferedSource: BufferedSource)(implicit d: Decoder[T]): T = {
    parser.parse(bufferedSource.getLines().mkString("\n")) match {
      case Right(res) =>
        res.as[T] match {
          case Right(map) => map
          case Left(ex) =>
            logger.error(s"Failed to decode ${res.toString()}", ex)
            throw ex
        }
      case Left(ex) =>
        logger.error(s"Failed to parse yaml file", ex)
        throw ex
    }
  }
}
