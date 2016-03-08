package se.gigurra.dcs.remote.dcsclient

import se.gigurra.serviceutils.twitter.logging.Logging

trait DcsRemoteListener extends Logging {
  def onConnect() { logger.info("connected") }
  def onFailedConnect() { logger.info("failedConnect") }
  def onDisconnect() { logger.info("onDisconnect") }
}