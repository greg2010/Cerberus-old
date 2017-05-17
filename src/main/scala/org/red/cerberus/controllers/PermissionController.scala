package org.red.cerberus.controllers


import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.yaml._
import org.red.cerberus.exceptions.ResourceNotFoundException
import org.red.db.dbAgent
import slick.jdbc.PostgresProfile.api._
import org.red.db.models.Coalition

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.io.Source


object PermissionController extends LazyLogging {
  private case class PermissionMap(permission_map: Seq[PermissionBitEntry])
  case class PermissionBitEntry(name: String, bit_position: Int, description: String)

  val permissionMap: Seq[PermissionBitEntry] = parser.parse(Source.fromResource("permission_map.yml").getLines().mkString("\n")) match {
    case Right(res) =>
      res.as[PermissionMap] match {
        case Right(map) => map.permission_map
        case Left(ex) =>
          logger.error(s"Failed to decode ${res.toString()}", ex)
          throw ex
      }
    case Left(ex) =>
      logger.error("Failed to parse permissions yaml file", ex)
      throw ex
  }

  def findPermissionByName(name: String): PermissionBitEntry = {
    permissionMap.find(_.name == name) match {
      case Some(res) => res
      case None => throw ResourceNotFoundException("No permission exists with such name")
    }
  }

  def calculateAclPermission(characterId: Option[Long],
                             corporationId: Option[Long],
                             allianceId: Option[Long]): Future[Long] = {
    val q = Coalition.Acl.filter (
      r =>
        (r.characterId === characterId || r.characterId.isEmpty) &&
          (r.corporationId === corporationId  || r.corporationId.isEmpty) &&
          (r.allianceId === allianceId  || r.allianceId.isEmpty) &&
          (r.characterId.isDefined || r.corporationId.isDefined || r.allianceId.isDefined)
    ).map(_.entityPermission)

    dbAgent.run(q.result).map { res =>
      res.foldLeft(0L)((l, r) => l | r)
    }
  }

  def getAclPermission(aclMask: Long): Seq[PermissionBitEntry] = {
    @tailrec
    def getBitsRec(aclMask: Long, curPosn: Int, soFar: Seq[Int]): Seq[Int] = {
      val mask = 1
      if (aclMask == 0) soFar
      else if ((aclMask & mask) == 1) getBitsRec(aclMask >> 1, curPosn + 1, soFar :+ curPosn)
      else getBitsRec(aclMask >> 1, curPosn + 1, soFar)
    }
    getBitsRec(aclMask, 0, Seq()).map { bit =>
      permissionMap.find(_.bit_position == bit) match {
        case Some(entry) => entry
        case None => throw new RuntimeException("Bad bit") //FIXME: change exception type
      }
    }
  }
}
