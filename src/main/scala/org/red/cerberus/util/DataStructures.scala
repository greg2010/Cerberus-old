package org.red.cerberus.util

import java.sql.Timestamp

import moe.pizza.eveapi.ApiKey
import org.red.db.models.Coalition.{UsersRow, UsersViewRow}

sealed trait Credentials

case class LegacyCredentials(apiKey: ApiKey, name: String) extends Credentials

case class SSOCredentials(refreshToken: String, accessToken: String) extends Credentials

case class SSOAuthCode(code: String)

case class User(eveUserData: EveUserData,
                userId: Int,
                email: String,
                password: Option[String],
                salt: Option[String],
                isBanned: Boolean,
                creationTime: Timestamp,
                lastLoggedIn: Option[Timestamp],
                languageCode: String) {
  def toUserMini: UserMini = {
    UserMini(
      name = this.eveUserData.characterName,
      id = this.userId,
      characterId = this.eveUserData.characterId
    )
  }
}

object User {
  def apply(usersViewRow: UsersViewRow): User = {
    // TODO: raise postgres exception on failed get
    val eveUserData = EveUserData(
      characterId = usersViewRow.characterId.get,
      characterName = usersViewRow.characterName.get,
      corporationId = usersViewRow.corporationId.get,
      corporationName = usersViewRow.corporationName.get,
      corporationTicker = usersViewRow.corporationTicker.get,
      allianceId = usersViewRow.allianceId,
      allianceName = usersViewRow.allianceName,
      allianceTicker = usersViewRow.allianceTicker
    )
    User(
      eveUserData = eveUserData,
      userId = usersViewRow.userId.get,
      email = usersViewRow.email.get,
      password = usersViewRow.password,
      salt = usersViewRow.salt,
      isBanned = usersViewRow.banned.get,
      creationTime = usersViewRow.creationTime.get,
      lastLoggedIn = usersViewRow.lastLoggedIn,
      languageCode = usersViewRow.languageCode.get
    )
  }
}

case class EveUserData(characterId: Long,
                       characterName: String,
                       corporationId: Long,
                       corporationName: String,
                       corporationTicker: String,
                       allianceId: Option[Long],
                       allianceName: Option[String],
                       allianceTicker: Option[String])

object CredentialsType extends Enumeration {
  type CredentialsType = Value
  val Legacy = Value("Legacy")
  val SSO = Value("SSO")
}

case class PermissionBitEntry(name: String, bit_position: Int, description: String)

case class TeamspeakGroupMapEntry(bit_name: String, teamspeak_group_id: Int)

case class UserMini(name: String, id: Int, characterId: Long) {
  def toPrivateClaim: PrivateClaim = {
    PrivateClaim(
      nme = name,
      id = id,
      cid = characterId
    )
  }

  def fromUser(user: User): UserMini = {
    UserMini(
      name = user.eveUserData.characterName,
      id = user.userId,
      characterId = user.eveUserData.characterId
    )
  }
}

object UserMini {
  def apply(usersRow: UsersRow): UserMini = {
    UserMini(
      name = usersRow.name,
      id = usersRow.id,
      characterId = usersRow.characterId
    )
  }
}

case class PrivateClaim(nme: String, id: Int, cid: Long) {
  def toUserData: UserMini = {
    UserMini(
      name = nme,
      id = id,
      characterId = cid
    )
  }
}