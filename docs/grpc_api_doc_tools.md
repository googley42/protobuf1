# gRPC API Doc Generation

API documentation for `HelloService` is generated from `//` comments in `hello.proto` using [`protoc-gen-doc`](https://github.com/pseudomuto/protoc-gen-doc) and served by the running service on port 8080.

## How it works

`protoc-gen-doc` is a `protoc` plugin that reads doc comments from `.proto` files and emits HTML. It runs as part of the normal `sbt compile` step via `sbt-protoc`, alongside the ScalaPB code generator.

The generated `index.html` is written into sbt's managed resource directory so it is bundled on the classpath — meaning it is available in a deployed JAR with no filesystem dependency. `DocsServer` reads it via `getClass.getResourceAsStream("/docs/index.html")` and serves it on port 8080 using the JDK's built-in `HttpServer` (no extra dependencies).

## Setup

### Install `protoc-gen-doc`

```bash
brew install protoc-gen-doc   # macOS
# Linux: download from https://github.com/pseudomuto/protoc-gen-doc/releases and place on PATH
```

Verify: `protoc-gen-doc --version` should print a version string.

### `build.sbt` configuration

The doc generator target is wired into `PB.targets` alongside ScalaPB:

```scala
Compile / PB.targets := Seq(
  scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
  PB.gens.plugin("doc") -> (Compile / resourceManaged).value / "docs"
)
```

Output lands at `target/scala-<version>/resource_managed/main/docs/index.html`, which sbt puts on the classpath automatically.

## Annotating `.proto` files

Place a `//` comment directly above each message, field, enum value, or RPC:

```proto
// HelloRequest carries caller identity and contact preferences for a SayHello call.
message HelloRequest {
  // name is the caller's display name. Required; an empty value returns INVALID_ARGUMENT.
  string name = 1;

  // title is an optional honorific (e.g. "Dr", "Ms").
  optional string title = 2;

  // Language controls the greeting language in the reply.
  enum Language {
    LANGUAGE_UNSPECIFIED = 0; // Default; server falls back to ENGLISH.
    ENGLISH  = 1;
    SPANISH  = 2;
    PUNJABI  = 3;
  }
  ...
}
```

## Generating and viewing docs

```bash
sbt --no-colors compile
sbt --no-colors "runMain server.HelloServer"
```

Then open `http://localhost:8080/`.

## CI

`.github/workflows/ci.yml` installs `protoc-gen-doc`, runs `sbt compile`, and uploads the generated `index.html` as a build artifact on every push to `main` that touches `.proto` files or build configuration.
