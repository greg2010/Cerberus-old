package org.red.cerberus.daemons.teamspeak

import com.gilt.gfc.concurrent.ScalaFutures.FutureOps
import com.github.theholywaffle.teamspeak3.api.reconnect.ReconnectStrategy
import com.github.theholywaffle.teamspeak3.{TS3ApiAsync, TS3Config, TS3Query}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import monix.execution.Cancelable
import monix.execution.Scheduler.{global => scheduler}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class TeamspeakDaemon(config: Config)
                     (implicit ec: ExecutionContext) extends LazyLogging {

  private val ts3Conf = new TS3Config
  ts3Conf.setHost(config.getString("ts3.host"))
  private val connectionHandler = new CustomConnectionHandler(config)
  ts3Conf.setConnectionHandler(connectionHandler)
  ts3Conf.setReconnectStrategy(ReconnectStrategy.exponentialBackoff())

  private val ts3Query = new TS3Query(ts3Conf)
  val client: TS3ApiAsync = ts3Query.getAsyncApi
  val daemon: Cancelable =
    scheduler.scheduleWithFixedDelay(0.seconds, 1.minute) {
      if (!connectionHandler.isConnected)
        Future(ts3Query.connect()).withTimeout(55.seconds)
          .onComplete {
            case Success(r) =>
              logger.info(s"Connected to teamspeak teamspeakHost=${config.getString("ts3.host")} " +
                s"event=teamspeak.connect.success")
            case Failure(ex) =>
              logger.error("Failed to connect to teamspeak, retrying event=teamspeak.connect.failure")
          }
    }

  def isConnected: Boolean = connectionHandler.isConnected
}
