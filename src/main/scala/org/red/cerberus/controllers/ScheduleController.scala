package org.red.cerberus.controllers

import java.util.Date

import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import monix.execution.Cancelable
import org.quartz.JobBuilder.newJob
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.{CronScheduleBuilder, Scheduler, SimpleScheduleBuilder, TriggerKey}
import org.red.cerberus.exceptions.ResourceNotFoundException
import org.red.cerberus.jobs.{DaemonJob, UpdateLegacyUserJob, UpdateSSOUserJob}
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._
import monix.execution.Scheduler.{global => scheduler}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Random


class ScheduleController(quartzScheduler: Scheduler, config: Config, userController: UserController)(implicit dbAgent: JdbcBackend.Database) extends LazyLogging {

  quartzScheduler.getContext.put("dbAgent", dbAgent)
  quartzScheduler.getContext.put("scheduleController", this)
  quartzScheduler.getContext.put("userController", userController)
  val daemonTriggerName = "userDaemon"
  private val daemonTask: Cancelable = scheduler.scheduleWithFixedDelay(0.seconds, 1.minute) {
    this.startUserDaemon()
  }

  def startUserDaemon(): Future[Date] = Future {
    val maybeTriggerKey = new TriggerKey(daemonTriggerName, config.getString("quartzUserUpdateGroupName"))
    if (quartzScheduler.checkExists(maybeTriggerKey)) {
      logger.info("Daemon has already started, doing nothing event=user.schedule")
      quartzScheduler.getTrigger(maybeTriggerKey).getNextFireTime
    } else {
      val j = newJob((new DaemonJob).getClass)
        .withIdentity(daemonTriggerName, config.getString("quartzUserUpdateGroupName"))
        .build()
      val t = newTrigger()
        .withIdentity(daemonTriggerName, config.getString("quartzUserUpdateGroupName"))
        .forJob(j)
        .withSchedule(SimpleScheduleBuilder
          .repeatMinutelyForever(config.getInt("quartzUserUpdateDaemonRefreshRate"))
        )
        .startNow()
        .build()
      quartzScheduler.scheduleJob(j, t)
    }
  }

  def scheduleUserUpdate(user: Coalition.EveApiRow): Future[Option[Date]] = {
    val maybeTriggerKey = new TriggerKey(user.id.toString, config.getString("quartzUserUpdateGroupName"))
    for {
      ifExists <- Future(quartzScheduler.checkExists(maybeTriggerKey))
      res <- {
        if (ifExists) {
          logger.info(s"Job already exists, skipping id=${user.id} userId=${user.userId} event=user.schedule")
          Future(None)
        } else {
          logger.info(s"Job doesn't exist, scheduling id=${user.id} userId=${user.userId} event=user.schedule")
          val j = newJob()
            .withIdentity(user.id.toString, config.getString("quartzUserUpdateGroupName"))
          val builtJob = (user.keyId, user.verificationCode, user.evessoRefreshToken) match {
            case (Some(keyId), Some(vCode), _) =>
              val b = j.ofType((new UpdateLegacyUserJob).getClass).build()
              b.getJobDataMap.put("keyId", keyId)
              b.getJobDataMap.put("vCode", vCode)
              b
            case (_, _, Some(ssoToken)) =>
              val b = j.ofType((new UpdateSSOUserJob).getClass).build()
              b.getJobDataMap.put("ssoToken", ssoToken)
              b
            case _ =>
              throw ResourceNotFoundException("Not found eve API credentials for user")
          }
          builtJob.getJobDataMap.put("userId", user.userId)
          builtJob.getJobDataMap.put("characterId", user.characterId)
          dbAgent.run(Coalition.Character.filter(_.id === user.characterId).map(_.name).take(1).result)
            .map { name =>
              name.headOption match {
                case Some(n) =>
                  builtJob.getJobDataMap.put("characterName", n)
                  if (config.getInt ("quartzUserUpdateRefreshRate") > 59)
                    throw new IllegalArgumentException("quartzUserUpdateRefreshRate must be <60")

                  val randNum = Random.nextInt(config.getInt ("quartzUserUpdateRefreshRate") )
                  val t = newTrigger()
                    .forJob (builtJob)
                    .withIdentity(maybeTriggerKey)
                    .withSchedule(
                      CronScheduleBuilder
                        .cronSchedule(s"0 $randNum/${config.getString ("quartzUserUpdateRefreshRate")} * * * ?")
                    )
                    .build()
                  val r = Some(quartzScheduler.scheduleJob(builtJob, t))
                  logger.info(s"Scheduled " +
                    s"jobId=${user.id} " +
                    s"userId=${user.userId} " +
                    s"characterId=${user.characterId} " +
                    s"characterName=$n " +
                    s"event=user.schedule.success")
                  r
                case None =>
                  logger.error(s"Didn't find user's character name " +
                    s"characterId=${user.characterId} " +
                    s"userId=${user.userId} " +
                    s"event=user.schedule.failure")
                  throw ResourceNotFoundException("Didn't find user's character name")
              }
            }
        }
      }
    } yield res
  }
}
