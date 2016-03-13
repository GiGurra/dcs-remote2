val dcs_remote = Project(id = "dcs-remote", base = file("."))
  .settings(
    organization := "se.gigurra",
    version := "SNAPSHOT",

    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")
  )
  .dependsOn(uri("git://github.com/GiGurra/service-utils.git#0.1.5"))
