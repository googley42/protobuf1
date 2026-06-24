# gRPC API Doc Generation

API documentation for `HelloService` is generated from `//` comments in `hello.proto` using [`protoc-gen-doc`](https://github.com/pseudomuto/protoc-gen-doc) and served by the running service on port 8080.

## How it works

`protoc-gen-doc` is a `protoc` plugin that reads doc comments from `.proto` files and emits HTML. It runs as part of the normal `sbt compile` step via `sbt-protoc`, alongside the ScalaPB code generator.

The generated `index.html` is written into a **namespaced** path in sbt's managed resource directory:

```
docs/<service-name>/index.html
```

This means the HTML is bundled on the classpath at a predictable, service-scoped location — available in a deployed JAR with no filesystem dependency.

When the server starts, `DocsServer` scans the classpath for all resources matching `docs/*/index.html` and builds a root index dynamically. Any JAR on the classpath that follows this convention is discovered automatically — you get its docs for free just by having it as a dependency.

## Multi-module architecture

The convention scales naturally to environments with multiple SBT modules (e.g., a separate module for external-facing protocols with its own publishing lifecycle):

```
my-org/
  proto-external/   ← publishes JAR with docs/external-api/index.html
  proto-internal/   ← publishes JAR with docs/internal-api/index.html
  my-service/       ← depends on both; server discovers both at startup
```

The external proto module team does not need to run a docs server. They bundle docs at the standard path when they publish. Any internal service that takes a dependency on their JAR gets those docs surfaced automatically in its own docs index. The running service's index at `/` dynamically reflects whatever is on the classpath at that moment.

```
GET /                            → dynamic index of all discovered services
GET /docs/<service>/index.html   → docs for that specific service
```

## Classpath mechanics

Three things must be true for the docs server to work at runtime:

**1. Doc HTML on the classpath**

sbt-protoc writes `docs/<service-name>/index.html` to `target/.../resource_managed/main/` but does not register those files with sbt's managed resource tracking. `copyResources` therefore never moves them to `classes/`. A compile hook in `build.sbt` fills this gap:

```scala
Compile / compile := {
  val result = (Compile / compile).value
  val src = (Compile / resourceManaged).value
  val dst = (Compile / classDirectory).value
  if (src.exists) IO.copyDirectory(src, dst)
  result
}
```

This runs after protoc (which is part of the compile step), so the HTML exists in `resource_managed/` before the copy happens.

**2. Service manifest on the classpath**

`src/main/resources/META-INF/grpc-docs` is a static unmanaged resource containing the service name (`hello-service`). sbt's `copyResources` — which runs as part of `sbt run`'s dependency chain — copies it into `classes/`. For published JARs, the file is bundled at package time automatically.

For multi-module pipelines, each module has its own `META-INF/grpc-docs`. `ClassLoader.getResources("META-INF/grpc-docs")` returns one URL per JAR/directory on the classpath, so all contributing modules are discovered in one call — the same pattern Java's own `ServiceLoader` uses.

**3. Correct classloader in HTTP handler threads**

The JDK `HttpServer` dispatches requests on its own thread pool. Those threads have a different (JDK AppClassLoader) context classloader, not sbt's `LayeredClassLoader`. `DocsServer` therefore captures `getClass.getClassLoader` once as a `val` at object-init time and uses it for all classpath lookups, rather than calling `Thread.currentThread().getContextClassLoader` inside handlers.

## Setup

### Install `protoc-gen-doc`

```bash
brew install protoc-gen-doc   # macOS
# Linux: download from https://github.com/pseudomuto/protoc-gen-doc/releases and place on PATH
```

Verify: `protoc-gen-doc --version` should print a version string.

### `build.sbt` configuration

The doc generator target is wired into `PB.targets` alongside ScalaPB, with an explicit service name in the output path:

```scala
Compile / PB.targets := Seq(
  scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
  PB.gens.plugin("doc") -> (Compile / resourceManaged).value / "docs" / "hello-service"
)
```

Output lands at `target/scala-<version>/resource_managed/main/docs/hello-service/index.html`, which sbt puts on the classpath automatically.

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

Then open `http://localhost:8080/` for the index, or `http://localhost:8080/docs/hello-service/index.html` directly.

An example of the rendered output is at [docs/index.html](index.html).

## CI

`.github/workflows/ci.yml` installs `protoc-gen-doc`, runs `sbt compile`, and uploads the generated `index.html` as a build artifact on every push to `main` that touches `.proto` files or build configuration.

### Trigger

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'src/main/proto/**'
      - 'build.sbt'
      - 'project/**'
      - '.github/workflows/ci.yml'
```

### Steps

```yaml
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - uses: sbt/setup-sbt@v1

      - name: Install protoc-gen-doc
        run: |
          VERSION=1.5.1
          curl -sSL "https://github.com/pseudomuto/protoc-gen-doc/releases/download/v${VERSION}/protoc-gen-doc_${VERSION}_linux_amd64.tar.gz" \
            | tar -xz protoc-gen-doc
          sudo mv protoc-gen-doc /usr/local/bin/

      - name: Generate HTML docs
        run: sbt --no-colors compile

      - name: Upload docs artifact
        uses: actions/upload-artifact@v4
        with:
          name: api-docs
          path: target/scala-3.3.7/resource_managed/main/docs/hello-service/index.html
          if-no-files-found: error
```

| Step | Purpose |
|---|---|
| `actions/setup-java@v4` (Temurin 21) | SBT requires a JVM; Temurin is the recommended open-source OpenJDK build for CI. |
| `sbt/setup-sbt@v1` | Installs a pinned SBT launcher rather than relying on whatever version is pre-installed on the runner. |
| `Install protoc-gen-doc` | Downloads the pre-built Linux binary from the GitHub release — no Go toolchain or Docker daemon needed. Pinned to `1.5.1` for reproducibility. |
| `sbt --no-colors compile` | Runs the full compile, which invokes `protoc` with both the ScalaPB and `protoc-gen-doc` targets in one pass, writing `index.html` into the managed resource directory so it is bundled on the classpath. `--no-colors` suppresses ANSI codes that would clutter the GHA log. |
| `upload-artifact@v4` | Stores the generated HTML as a downloadable artifact on the workflow run. `if-no-files-found: error` causes the step to fail explicitly if the file is absent rather than silently uploading an empty archive. |

### Artifact path

Because the doc target is wired to `(Compile / resourceManaged).value / "docs" / "hello-service"` in `build.sbt`, the generated file lands at:

```
target/scala-3.3.7/resource_managed/main/docs/hello-service/index.html
```

If the Scala version changes, update the path to match (e.g. `target/scala-3.4.x/...`).
