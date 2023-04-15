name := "SCP"

assembly / assemblyMergeStrategy  := {
  case PathList("module-info.class") => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case PathList("META-INF", _*) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

version := "1.0"

scalaVersion := "2.13.1"
scalacOptions ++= Seq("-unchecked", "-deprecation")

lazy val akkaVersion = "2.7.0"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed"         % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.11"
)
// dependencyOverrides ++= Set(
//   "org.agrona" % "agrona" % "1.16.0"
// )
