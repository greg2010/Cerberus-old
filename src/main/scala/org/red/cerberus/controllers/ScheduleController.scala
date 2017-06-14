package org.red.cerberus.controllers

import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory


class ScheduleController {
  val quartzScheduler: Scheduler = new StdSchedulerFactory().getScheduler

}
