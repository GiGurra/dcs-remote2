val dcs_remote = Project(id = "dcs-remote", base = file("."))
  .settings(
    organization := "se.gigurra",
    version := "SNAPSHOT",

    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")
/*
    libraryDependencies ++= Seq(
      "com.twitter"       %%  "finagle-http"      %   "6.31.0",
      "org.json4s"        %%  "json4s-core"       %   "3.3.0",
      "org.json4s"        %%  "json4s-jackson"    %   "3.3.0",
      "com.jsuereth"      %%  "scala-arm"         %   "1.4",
      "org.scalatest"     %%  "scalatest"         %   "2.2.4"     %   "test",
      "org.mockito"       %   "mockito-core"      %   "1.10.19"   %   "test",
      "com.github.t3hnar" %%  "scala-bcrypt"      %   "2.5"
    ),*/

   // resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/",

   // packSettings,
  //  packMain := Map("valhalla-server" -> "com.saiaku.valhalla.server.Server")
  )
  .dependsOn(uri("git://github.com/GiGurra/service-utils.git#0.1.4"))
  
