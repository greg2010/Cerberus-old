package org.red.cerberus.daemons.teamspeak

import java.util.concurrent.atomic.AtomicBoolean

import com.github.theholywaffle.teamspeak3.TS3Query
import com.github.theholywaffle.teamspeak3.api.reconnect.ConnectionHandler
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.red.cerberus.util.FutureConverters

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

private[this] class CustomConnectionHandler(config: Config)(implicit ec: ExecutionContext) extends ConnectionHandler with LazyLogging {
  @volatile
  private var connected: AtomicBoolean = new AtomicBoolean(false)

  def isConnected: Boolean = connected.synchronized(connected.get())

  override def onDisconnect(ts3Query: TS3Query): Unit = {
    connected.synchronized(connected.set(false))
    logger.warn("Disconnected from teamspeak event=teamspeak.disconnect")
  }

  override def onConnect(ts3Query: TS3Query): Unit = {
    val client = ts3Query.getAsyncApi
    logger.info("Connected to teamspeak event=teamspeak.connect")
    try {
      client.login(config.getString("ts3.serverQueryLogin"), config.getString("ts3.serverQueryPassword"))
      client.selectVirtualServerById(config.getInt("ts3.virtualServerId"))
      client.setNickname(config.getString("ts3.botName"))
      client.registerAllEvents()
      connected.synchronized(connected.set(true))
      logger.info("Successfully connected to teamspeak server and prepared connection event=teamspeak.connect.success")
    } catch {
      case ex: Exception if NonFatal(ex) =>
        logger.error("Exception thrown during teamspeak connection setup event=teamspeak.connect.failure")
    }

    FutureConverters.commandToScalaFuture(client.getServerInfo)
      .onComplete {
        case Success(r) =>
          logger.info(s"Successfully connected to TS3 server " +
            s"teamspeakServerName=${r.getName} " +
            s"teamspeakVersion=${r.getVersion} " +
            s"ip=${r.getIp} " +
            s"ping=${r.getPing} " +
            s"event=teamspeak.connect.success")
        case Failure(ex) =>
          logger.error("Failed to connect to TS3 server event=teamspeak.connect.failure", ex)
      }
  }
}
