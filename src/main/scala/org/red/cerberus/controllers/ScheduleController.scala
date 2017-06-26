package org.red.cerberus.controllers

import java.util.Date

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.quartz.JobBuilder.newJob
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.{CronScheduleBuilder, TriggerKey}
import org.red.cerberus.daemons.ScheduleDaemon
import org.red.cerberus.exceptions.ResourceNotFoundException
import org.red.cerberus.external.auth.EveApiClient
import org.red.cerberus.jobs.quartz.UserJob
import org.red.cerberus.util.CredentialsType
import org.red.db.models.Coalition
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


class ScheduleController(config: Config, userController: => UserController)
                        (implicit dbAgent: JdbcBackend.Database, ec: ExecutionContext) extends LazyLogging {

  private val daemon = new ScheduleDaemon(this, config, userController)
  private val quartzScheduler = daemon.quartzScheduler

  def scheduleUserUpdate(userId: Int): Future[Option[Date]] = {
    val maybeTriggerKey = new TriggerKey(userId.toString, config.getString("quartzUserUpdateGroupName"))
    for {
      ifExists <- Future(quartzScheduler.checkExists(maybeTriggerKey))
      res <- {
        if (ifExists) {
          logger.info(s"Job already exists, skipping id=$userId userId=$userId event=user.schedule")
          Future(None)
        } else {
          logger.info(s"Job doesn't exist, scheduling id=$userId userId=$userId event=user.schedule")
          val j = newJob()
            .withIdentity(userId.toString, config.getString("quartzUserUpdateGroupName"))
          val builtJob = j.ofType((new UserJob).getClass).build()
          builtJob.getJobDataMap.put("userId", userId)
          if (config.getInt("quartzUserUpdateRefreshRate") > 59)
            throw new IllegalArgumentException("quartzUserUpdateRefreshRate must be <60")

          val randNum = Random.nextInt(config.getInt("quartzUserUpdateRefreshRate"))
          val t = newTrigger()
            .forJob(builtJob)
            .withIdentity(maybeTriggerKey)
            .withSchedule(
              CronScheduleBuilder
                .cronSchedule(s"0 $randNum/${config.getString("quartzUserUpdateRefreshRate")} * * * ?")
            )
            .build()
          val r = Some(quartzScheduler.scheduleJob(builtJob, t))
          logger.info(s"Scheduled " +
            s"userId=$userId " +
            s"event=user.schedule.success")
          Future(r)
        }
      }
    } yield res
  }
}
