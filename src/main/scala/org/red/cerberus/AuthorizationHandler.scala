package org.red.cerberus

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpRequest, Uri}
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

  case class AccessMapEntry(route: String, kind: String, required_permissions: Seq[String])
  case class AccessMapEntryEnhanced(path: Path, method: Option[String], requiredPermissions: Seq[PermissionBitEntry])
  case class AccessMap(access_map: Seq[AccessMapEntry])


  def getPermissionsForPath(path: Path): Seq[PermissionBitEntry] = {
    def pathMatherRec(pathMatched: Path, pathToMatch: Path, permissionsMathced: Seq[PermissionBitEntry]): Seq[PermissionBitEntry] = {
      permissionMap.filter(perm => perm.path.startsWith(pathMatched ++ pathToMatch.head))
    }
  }

  val permissionMap: Seq[AccessMapEntryEnhanced] =
    parser.parse(Source.fromResource("access_map.yml").getLines().mkString("\n")) match {
      case Right(res) =>
        res.as[AccessMap] match {
          case Right(map) => map.access_map.map { entry =>
            AccessMapEntryEnhanced(
              path = Uri.from(path = entry.route).path,
              method = entry.kind match {
                case "*" => None
                case methodName => Some(methodName)
              },
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
      permissionMap.find(_.path == ctx.unmatchedPath) match {
        case Some(accessMapEntryEnhanced) => true //FIXME: implement actual permission check
        case None => false
      }
    }
  }
}
