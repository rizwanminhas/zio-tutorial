name := "zio-tutorial"

version := "0.1"

scalaVersion := "2.13.6"

val ZioVersion = "1.0.9"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % ZioVersion,
  "dev.zio" %% "zio-streams" % ZioVersion
)
