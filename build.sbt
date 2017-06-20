lazy val root = (project in file("."))
  .settings(
    name         := "google-search-cache",
    scalaVersion := "2.12.2",
    version      := "0.1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.3",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.3" % Test,

      "org.scalactic" %% "scalactic" % "3.0.1",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    )
  )
