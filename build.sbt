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
      PB.gens.plugin("doc") -> (Compile / resourceManaged).value / "docs" / "hello-service"
    ),

    PB.protocVersion := "3.24.3",

    // Include managed sources for generated protobuf classes
    Compile / unmanagedSourceDirectories += (Compile / sourceManaged).value,

    // sbt-protoc writes doc HTML to resourceManaged but doesn't register those files with sbt's
    // resource tracking, so copyResources never moves them to classes/. Copy them explicitly
    // after compile (by which time protoc has already run and generated the HTML).
    Compile / compile := {
      val result = (Compile / compile).value
      val src = (Compile / resourceManaged).value
      val dst = (Compile / classDirectory).value
      if (src.exists) IO.copyDirectory(src, dst)
      result
    }
  )

// Force all gRPC dependencies to 1.56.0
ThisBuild / dependencyOverrides ++= Seq(
  "io.grpc" % "grpc-netty-shaded" % "1.56.0",
  "io.grpc" % "grpc-core" % "1.56.0",
  "io.grpc" % "grpc-stub" % "1.56.0",
  "io.grpc" % "grpc-api" % "1.56.0"
)
