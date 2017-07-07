package org.red.cerberus.util

import org.red.iris.UserMini


case class PermissionBitEntry(name: String, bit_position: Int, description: String)


case class PrivateClaim(nme: String, id: Int, cid: Long) {
  def toUserData: UserMini = {
    UserMini(
      name = nme,
      id = id,
      characterId = cid
    )
  }
}