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

The path filter skips unrelated commits. `build.sbt` and `project/**` are included so the job re-runs when the ScalaPB or `protoc-gen-doc` plugin configuration changes, not only when proto source changes.

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
          path: target/scala-3.3.7/resource_managed/main/docs/index.html
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

Because the doc target is wired to `(Compile / resourceManaged).value / "docs"` in `build.sbt`, the generated file lands at:

```
target/scala-3.3.7/resource_managed/main/docs/index.html
```

The `upload-artifact` path above reflects this. If the Scala version changes, update the path to match (e.g. `target/scala-3.4.x/resource_managed/main/docs/index.html`).
