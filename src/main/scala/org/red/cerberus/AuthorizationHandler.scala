package org.red.cerberus

import akka.http.scaladsl.server.RequestContext
import com.netaporter.uri.{PathPart, Uri}
import com.typesafe.scalalogging.LazyLogging
import io.circe.yaml.parser
import io.circe.generic.auto._
import org.red.cerberus.controllers.PermissionController
import org.red.cerberus.controllers.PermissionController.PermissionBitEntry

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source


trait AuthorizationHandler extends LazyLogging {

  case class AccessMapEntry(route: String, kind: String, required_permissions: Seq[String])
  case class AccessMapEntryEnhanced(path: Uri, method: Option[String], requiredPermissions: Seq[PermissionBitEntry])
  case class AccessMap(access_map: Seq[AccessMapEntry])


  def getPermissionsForUri(uri: Uri, method: String): Seq[PermissionBitEntry] = {
    val filteredPermissionMap = permissionMap.filter { entry => entry.method.isEmpty || entry.method.get == method}
    @tailrec
    def getPermissionsForUriRec(parsed: Seq[PathPart],
                                toParse: Seq[PathPart],
                                soFarPerm: Seq[PermissionBitEntry]
                               ): Seq[PermissionBitEntry] = {
      if (toParse.isEmpty) soFarPerm.distinct
      else {
        logger.debug(s"Parsing uri ${(parsed :+ toParse.head).mkString("/")}")
        getPermissionsForUriRec(
          parsed = parsed :+ toParse.head,
          toParse = toParse.tail,
          soFarPerm = filteredPermissionMap
            .filter(_.path.pathParts == (parsed :+ toParse.head))
            .flatMap(_.requiredPermissions) ++ soFarPerm
        )
      }
    }
    logger.info(s"Calculating permission list for path=${uri.path}")
    getPermissionsForUriRec(Seq(), uri.pathParts, Seq())
  }

  val permissionMap: Seq[AccessMapEntryEnhanced] =
    parser.parse(Source.fromResource("access_map.yml").getLines().mkString("\n")) match {
      case Right(res) =>
        res.as[AccessMap] match {
          case Right(map) => map.access_map.map { entry =>
            AccessMapEntryEnhanced(
              path = Uri.parse(entry.route),
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


  def customAuthorization(userData: UserData)(ctx: RequestContext): Future[Boolean] = {
    Future {
      val routeBinPermission =
        PermissionController.getBinPermissions(
          getPermissionsForUri(Uri.parse(ctx.unmatchedPath.toString), ctx.request.method.value)
        )
      logger.info(s"Calculated path=${ctx.unmatchedPath.toString} permissions " +
        s"pathPermission=$routeBinPermission userPermission=${userData.permissions}")
      (routeBinPermission & userData.permissions) == routeBinPermission
    }
  }
}
