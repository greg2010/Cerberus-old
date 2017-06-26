package org.red.cerberus.daemons

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import monix.execution.Cancelable
import monix.execution.Scheduler.{global => scheduler}
import org.quartz.JobBuilder.newJob
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{Scheduler, SimpleScheduleBuilder, TriggerKey}
import org.red.cerberus.controllers.{ScheduleController, TeamspeakController, UserController}
import org.red.cerberus.external.auth.EveApiClient
import org.red.cerberus.jobs.quartz.DaemonJob
import slick.jdbc.JdbcBackend

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class ScheduleDaemon(scheduleController: => ScheduleController, config: Config, userController: => UserController)
                    (implicit dbAgent: JdbcBackend.Database, ec: ExecutionContext) extends LazyLogging {
  val quartzScheduler: Scheduler = new StdSchedulerFactory().getScheduler
  private val daemonTriggerName = "userDaemon"

  quartzScheduler.getContext.put("dbAgent", dbAgent)
  quartzScheduler.getContext.put("ec", ec)
  quartzScheduler.getContext.put("scheduleController", scheduleController)
  quartzScheduler.getContext.put("userController", userController)
  quartzScheduler.start()
  val daemon: Cancelable =
    scheduler.scheduleWithFixedDelay(0.seconds, 1.minute) {
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
}
