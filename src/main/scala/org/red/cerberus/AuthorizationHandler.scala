package org.red.cerberus

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.RequestContext
import com.typesafe.scalalogging.LazyLogging
import io.circe.yaml.parser
import io.circe.generic.auto._
import org.red.cerberus.controllers.PermissionController
import org.red.cerberus.controllers.PermissionController.PermissionBitEntry
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.io.Source


trait AuthorizationHandler extends LazyLogging {

  case class AccessMapEntry(route: String, required_permissions: Seq[String])
  case class AccessMapEntryEnhanced(route: Uri, requiredPermissions: Seq[PermissionBitEntry])
  case class AccessMap(access_map: Seq[AccessMapEntry])

  val permissionMap: Seq[AccessMapEntryEnhanced] =
    parser.parse(Source.fromResource("permission_map.yml").getLines().mkString("\n")) match {
      case Right(res) =>
        res.as[AccessMap] match {
          case Right(map) => map.access_map.map { entry =>
            AccessMapEntryEnhanced(
              route = Uri.from(queryString = Some(entry.route)),
              requiredPermissions =
                entry
                  .required_permissions
                  .map(PermissionController.findPermissionByName)
            )
          }
          case Left(ex) =>
            logger.error(s"Failed to decode ${res.toString()}", ex)
            throw ex
        }
    case Left(ex) =>
      logger.error("Failed to parse permissions yaml file", ex)
      throw ex
  }


  def customAuthorization(ctx: RequestContext): Future[Boolean] = {
    Future {
      permissionMap.find(_.route.path == ctx.unmatchedPath) match {
        case Some(accessMapEntryEnhanced) => true //FIXME: implement actual permission check
        case None => false
      }
    }
  }
}
