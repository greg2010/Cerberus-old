package org.red.cerberus.daemons

import java.util.Date

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import monix.execution.Scheduler.{global => scheduler}
import org.quartz.JobBuilder.newJob
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{CronScheduleBuilder, Scheduler, SimpleScheduleBuilder, TriggerKey}
import org.red.cerberus.controllers.UserController
import org.red.cerberus.exceptions.ResourceNotFoundException
import org.red.cerberus.external.auth.EveApiClient
import org.red.cerberus.jobs.{DaemonJob, UserJob}
import org.red.cerberus.util.CredentialsType
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


class ScheduleDaemon(config: Config, userController: => UserController, eveApiClient: => EveApiClient)
                    (implicit dbAgent: JdbcBackend.Database, ec: ExecutionContext) extends AbstractDaemon with LazyLogging {
  private val quartzScheduler: Scheduler = new StdSchedulerFactory().getScheduler
  val daemonTriggerName = "userDaemon"

  def initialize(): Unit = {
    quartzScheduler.getContext.put("ec", ec)
    quartzScheduler.getContext.put("dbAgent", dbAgent)
    quartzScheduler.getContext.put("scheduleController", this)
    quartzScheduler.getContext.put("userController", userController)
    quartzScheduler.getContext.put("eveApiClient", eveApiClient)
    quartzScheduler.start()
    scheduler.scheduleWithFixedDelay(0.seconds, 1.minute) {
      this.startUserDaemon()
    }
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
          val builtJob = j.ofType((new UserJob).getClass).build()
          (user.keyId, user.verificationCode, user.evessoRefreshToken) match {
            case (Some(keyId), Some(vCode), _) =>
              builtJob.getJobDataMap.put("credentialsType", CredentialsType.Legacy.toString)
              builtJob.getJobDataMap.put("keyId", keyId)
              builtJob.getJobDataMap.put("vCode", vCode)
              builtJob
            case (_, _, Some(ssoToken)) =>
              builtJob.getJobDataMap.put("credentialsType", CredentialsType.SSO.toString)
              builtJob.getJobDataMap.put("ssoToken", ssoToken)
              builtJob
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
