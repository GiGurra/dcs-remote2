val dcs_remote = Project(id = "dcs-remote", base = file("."))
  .settings(
    organization := "se.gigurra",
    version := "SNAPSHOT",

    scalaVersion := "2.11.8",
    scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation"),

    libraryDependencies ++= Seq(
      "com.typesafe.akka"     % "akka-actor_2.11"         % "2.4.2",
      "net.java.dev.jna"      %   "jna-platform"          % "4.2.2",
      "net.java.dev.jna"      %   "jna"                   % "4.2.2"
    )
  )
  .dependsOn(uri("git://github.com/GiGurra/service-utils.git#0.1.10"))
