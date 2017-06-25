lazy val root = (project in file("."))
  .settings(
    name         := "google-search-cache",
    scalaVersion := "2.12.2",
    version      := "0.1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.4.19",
      "com.typesafe.akka" %% "akka-testkit" % "2.4.19" % Test,
      "com.typesafe.akka" %% "akka-http" % "10.0.8",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.8",

      "org.scalactic" %% "scalactic" % "3.0.1",
      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % Test
    )
  )
