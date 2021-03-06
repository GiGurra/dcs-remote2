package se.gigurra.dcs.remote

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import com.sun.jna.platform.win32.{Guid, Shell32}
import com.sun.jna.ptr.PointerByReference
import com.twitter.io.Charsets
import se.gigurra.serviceutils.twitter.logging.Logging

import scala.util.{Failure, Success, Try}

/**
  * Created by kjolh on 4/11/2016.
  */
object ScriptInstaller extends Logging {

  val srcFiles = Seq(
    "dcs_remote.lua",
    "dcs_remote_net_utils.lua",
    "dkjson.lua"
  )

  val exportLuaFileName = "Export.lua"

  def apply(): Unit = Try {

    logger.info(s"Installing dcs remote lua scripts -> DCS ..")

    val sources = srcFiles.map(fileName => fileName -> resource2String(s"lua/$fileName"))
    val exportLuaLine = resource2String(s"lua/$exportLuaFileName").lines.toSeq.head
    val defaultDcsDir = ensureDefaultDcsDir(savedGamesFolder)
    val dcsDirs = ensureDcsDirs(savedGamesFolder, defaultDcsDir)

    for (dcsDir <- dcsDirs) {
      logger.info(s" --> Installing dcs remote into: $dcsDir")
      val ScriptsDir = ensureScriptsDir(dcsDir)
      ensureExportsLuaContent(exportLuaLine, ensureExportsLuaFile(ScriptsDir))
      ensureScriptFiles(sources, ScriptsDir)
    }

  } match {
    case Success(_) =>
    case Failure(e) =>
      logger.error(e, s"Unable to auto-install lua scripts for Dcs-Remote into DCS")
  }

  def ensureDcsDirs(saveGamesDir: File, defaultDcsDir: File): Set[File] = {
    logger.info(s"Looking for 'Saved Games/DCS' directories ..")
    saveGamesDir.listFiles().filter(_.isDirectory).filter(_.getName.startsWith("DCS")).toSet ++ Set(defaultDcsDir)
  }

  lazy val savedGamesFolder: File = {
    val guid = Guid.GUID.fromString("4C5C32FF-BB9D-43b0-B5B4-2D72E54EAAA4")
    val ptrByRef = new PointerByReference()
    val result = Shell32.INSTANCE.SHGetKnownFolderPath(guid, 0, null, ptrByRef)
    if (result.intValue() == 0)
      new File(ptrByRef.getValue.getWideString(0))
    else
      throw new RuntimeException(s"Failed to find Saved Games folder")
  }

  def ensureDefaultDcsDir(saveGamesDir: File): File = {
    val defaultDcsDir = new File(s"$saveGamesDir/DCS")
    if (!defaultDcsDir.exists() && !defaultDcsDir.mkdir())
      throw new RuntimeException(s"Unable to create dcs default dir")
    defaultDcsDir
  }

  def ensureScriptFiles(sources: Seq[(String, String)], ScriptsDir: File): Unit = {
    logger.info(s"Adding script files")
    for ((fileName, desiredContents) <- sources) {
      val trgFile = new File(s"$ScriptsDir/$fileName")
      var needsOverwrite = true
      if (trgFile.exists()) {
        val prevContents = file2String(trgFile)
        if (prevContents == desiredContents)
          needsOverwrite = false
      } else {
        if (!trgFile.createNewFile())
          throw new RuntimeException(s"Could not create script file $trgFile")
      }

      if (needsOverwrite) {
        logger.info(s"Need to update $trgFile, doing it ..")
        Files.deleteIfExists(trgFile.toPath)
        Files.write(trgFile.toPath, desiredContents.getBytes(Charsets.Utf8), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
      } else {
        logger.info(s"No need to update $trgFile ..")
      }

    }
  }

  def ensureExportsLuaContent(exportLuaLine: String, trgExportLuaFile: File): Any = {
    logger.info(s"Adding entry to Export.lua if not already there ..")
    val prevContents = file2String(trgExportLuaFile)
    if (prevContents.contains(exportLuaLine)) {
      logger.info(s"No need to append Export.lua - Dcs Remote entry already there")
    } else {
      logger.info(s"Need to append Export.lua - No Dcs Remote entry there")
      Files.write(trgExportLuaFile.toPath, "\n".getBytes(Charsets.Utf8), StandardOpenOption.APPEND)
      Files.write(trgExportLuaFile.toPath, exportLuaLine.getBytes(Charsets.Utf8), StandardOpenOption.APPEND)
      Files.write(trgExportLuaFile.toPath, "\n".getBytes(Charsets.Utf8), StandardOpenOption.APPEND)
    }
  }

  def ensureExportsLuaFile(ScriptsDir: File): File = {
    val trgExportLuaFile = new File(s"$ScriptsDir/$exportLuaFileName")
    if (!trgExportLuaFile.exists() && !trgExportLuaFile.createNewFile())
      throw new RuntimeException(s"Unable to write to file: $trgExportLuaFile")
    trgExportLuaFile
  }

  def ensureScriptsDir(dcsDir: File): File = {
    val ScriptsDir = new File(s"$dcsDir/Scripts")
    if (!ScriptsDir.exists() && !ScriptsDir.mkdir())
      throw new RuntimeException(s"Unable to create Scripts dir: $ScriptsDir")
    ScriptsDir
  }

  def file2String(file: File, enc: String = "UTF-8"): String = {
    val source = scala.io.Source.fromFile(file, enc)
    val out = source.mkString
    source.close()
    out
  }

  def resource2String(path: String, enc: String = "UTF-8"): String = {
    val stream = getClass.getClassLoader.getResourceAsStream(path)
    val source = scala.io.Source.fromInputStream(stream, enc)
    val out = source.mkString
    source.close()
    out
  }

}
