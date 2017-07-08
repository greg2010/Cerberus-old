package org.red.cerberus.http

import akka.http.scaladsl.server.RequestContext
import com.netaporter.uri.Uri
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.util.PermissionBitEntry
import org.red.iris.UserMini

import scala.concurrent.{ExecutionContext, Future}


trait AuthorizationHandler extends LazyLogging {

  case class AccessMapEntry(route: String, kind: String, required_permissions: Seq[String])

  case class AccessMapEntryEnhanced(path: Uri, method: Option[String], requiredPermissions: Seq[PermissionBitEntry])

  case class AccessMap(access_map: Seq[AccessMapEntry])


  def getPermissionsForUri(uri: Uri, method: String): Seq[PermissionBitEntry] = {
    /*
    val filteredPermissionMap = permissionMap.filter { entry => entry.method.isEmpty || entry.method.get == method }

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
    YamlParser.parseResource[AccessMap](Source.fromResource("access_map.yml"))
      .access_map.map { entry =>
      AccessMapEntryEnhanced(
        path = Uri.parse(entry.route),
        method = entry.kind match {
          case "*" => None
          case methodName => Some(methodName)
        },
        requiredPermissions =
          entry
            .required_permissions
            .map(permissionController.findPermissionByName)
      )
      */
    Seq[PermissionBitEntry]()
  }


  // TODO: reimplement
  def customAuthorization(userData: UserMini)(ctx: RequestContext)(implicit ec: ExecutionContext): Future[Boolean] = {
    Future { true } /*
      val routeBinPermission =
        permissionController.getBinPermissions(
          getPermissionsForUri(Uri.parse(ctx.unmatchedPath.toString), ctx.request.method.value)
        )
      logger.info(s"Calculated path=${ctx.unmatchedPath.toString} permissions " +
        s"pathPermission=$routeBinPermission userPermission=${userData.permissions}")
      (routeBinPermission & userData.permissions) == routeBinPermission
    }*/
  }
}
