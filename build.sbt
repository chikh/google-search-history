lazy val akkaVersion = "2.5.16"
lazy val akkaHttpVersion = "10.1.4"
lazy val slf4jVersion = "1.7.25"
lazy val logbackVersion = "1.2.3"
lazy val scalamockVersion = "3.5.0"
lazy val scalatestVersion = "3.0.1"

lazy val root = (project in file("."))
  .settings(
    name         := "google-search-cache",
    scalaVersion := "2.12.6",
    version      := "0.1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.scalactic" %% "scalactic" % scalatestVersion,

      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
      "org.scalamock" %% "scalamock-scalatest-support" % scalamockVersion % Test
    ),
    fork in run := true
  )
