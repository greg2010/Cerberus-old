package org.red.cerberus.util

import org.red.iris.UserMini
import org.red.iris.finagle.clients.PermissionClient

import scala.concurrent.{ExecutionContext, Future}


case class PermissionBitEntry(name: String, bit_position: Int, description: String)


case class PrivateClaim(nme: String, id: Int, cid: Long, prm: Long) {
  def toUserData(permissionClient: PermissionClient)
                (implicit ec: ExecutionContext): Future[UserMini] = {
    permissionClient.getPermissionBits(prm).map { permissionList =>
      UserMini(
        name = nme,
        id = id,
        characterId = cid,
        userPermissions = permissionList
      )
    }
  }
}

object PrivateClaim {

  def fromUserData(userMini: UserMini, permissionClient: PermissionClient)
                    (implicit ec: ExecutionContext): Future[PrivateClaim] = {
    permissionClient.getPermissionMask(userMini.userPermissions).map { permissions =>
      PrivateClaim(
        userMini.name,
        userMini.id,
        userMini.characterId,
        permissions
      )
    }
  }
}