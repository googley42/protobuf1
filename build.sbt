ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .enablePlugins(sbtprotoc.ProtocPlugin)
  .settings(
    name := "protobuf1",

    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % "0.11.15",
      "io.grpc" % "grpc-netty-shaded" % "1.56.0",
      "io.grpc" % "grpc-core" % "1.56.0",
      "io.grpc" % "grpc-stub" % "1.56.0",
      "io.grpc" % "grpc-api" % "1.56.0"
    ),

    Compile / PB.protoSources := Seq(baseDirectory.value / "src" / "main" / "proto"),
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
      PB.gens.plugin("doc") -> (Compile / resourceManaged).value / "docs"
    ),

    PB.protocVersion := "3.24.3",

    // Include managed sources for generated protobuf classes
    Compile / unmanagedSourceDirectories += (Compile / sourceManaged).value
  )

// Force all gRPC dependencies to 1.56.0
ThisBuild / dependencyOverrides ++= Seq(
  "io.grpc" % "grpc-netty-shaded" % "1.56.0",
  "io.grpc" % "grpc-core" % "1.56.0",
  "io.grpc" % "grpc-stub" % "1.56.0",
  "io.grpc" % "grpc-api" % "1.56.0"
)
