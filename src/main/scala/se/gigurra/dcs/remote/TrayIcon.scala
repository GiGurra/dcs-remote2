package se.gigurra.dcs.remote

import java.awt
import java.awt._
import java.awt.event.{ActionEvent, ActionListener}
import javax.swing.ImageIcon

import se.gigurra.serviceutils.twitter.logging.Logging

case class TrayIcon(exitCallback: () => Unit) {

  val base = createImage("tray_icons/base.png", "base")
  val popup = new PopupMenu()
  val trayIcon = new awt.TrayIcon(base)
  val tray = SystemTray.getSystemTray
  val exitItem = new MenuItem("Exit")
  exitItem.addActionListener(new ActionListener { override def actionPerformed(e: ActionEvent): Unit = exitCallback() })

  trayIcon.setToolTip("DCS-Remote REST Proxy")
  popup.add(exitItem)
  trayIcon.setPopupMenu(popup)
  tray.add(trayIcon)

  def createImage(path: String, description: String): Image = {
    val imageURL = getClass.getClassLoader.getResource(path)
    if (imageURL == null) {
      throw new RuntimeException("Resource not found: " + path)
    } else {
      new ImageIcon(imageURL, description).getImage
    }
  }

}

object TrayIcon extends Logging{

  def setup(): Option[TrayIcon] = {
    SystemTray.isSupported match {
      case true =>
        logger.info("Tray icon supported! Setting up tray icon..")
        Some(TrayIcon(() => System.exit(0)))
      case false =>
        logger.info("Tray icon not supported!")
        None
    }
  }
}
