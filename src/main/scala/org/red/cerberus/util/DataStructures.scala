package org.red.cerberus.util

import org.red.iris.{PermissionBit, UserMini}

import scala.concurrent.{ExecutionContext}


case class PrivateClaim(nme: String, id: Int, cid: Long, prm: Long) {
  def toUserData(permissionList: Seq[PermissionBit])
                (implicit ec: ExecutionContext): UserMini = {
    val userPermissions = (for {
      (x,i) <- prm.toBinaryString.map(_.toInt).reverse.zipWithIndex
      if x != 0
    } yield permissionList.find(_.bitPosition == i)).flatten

    UserMini(
      name = nme,
      id = id,
      characterId = cid,
      permissions = userPermissions
    )
  }
}

object PrivateClaim {
  def fromUserData(userMini: UserMini)(implicit ec: ExecutionContext): PrivateClaim = {
    PrivateClaim(
      userMini.name,
      userMini.id,
      userMini.characterId,
      userMini.permissions.map(_.bitPosition).fold(0)(_ + 1 << _)
    )
  }
}