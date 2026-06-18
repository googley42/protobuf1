# GRPC API Doc Tools

What we would like is a way of annotating proto IDL files with documentation that ideally then gets rendered in 
a manner similar to OpenApi docs ie html files that could also be served by the service itself. 
If this is not possible then a markdown file output would be OK. 

Post your options in a new section below. The next step after that would be to 

---

## Options

### Option 1: protoc-gen-doc (recommended)

[protoc-gen-doc](https://github.com/pseudomuto/protoc-gen-doc) is a `protoc` plugin that reads doc comments directly from `.proto` files and emits HTML, Markdown, JSON, or DocBook. Because this project already uses `sbt-protoc`, adding it is a one-liner in `build.sbt`.

**Comment style** — place a comment directly above each element:

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

  Language preferred_language = 3;

  // ClientInfo identifies the calling application for observability.
  message ClientInfo {
    string app_name    = 1; // e.g. "my-service"
    string app_version = 2; // e.g. "1.4.2"
  }

  ClientInfo client = 4;

  // contact_method is mutually exclusive: supply either email or phone_number.
  oneof contact_method {
    string email        = 5;
    string phone_number = 6;
  }
}
```

**SBT integration** — add the generator target alongside the existing ScalaPB target in `build.sbt`:

```scala
Compile / PB.targets := Seq(
  scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
  PB.gens.plugin("doc") -> (baseDirectory.value / "docs" / "generated")
)
```

Then install the binary (e.g. `brew install protoc-gen-doc` on macOS) and `sbt compile` produces `docs/generated/index.html` alongside the Scala sources. The HTML output looks and navigates similarly to OpenAPI/Swagger UI — collapsible sections per message and service, field tables, enum value tables.

The generated file can be served statically by adding a simple HTTP handler to `HelloServer` (e.g. via the JDK's built-in `com.sun.net.httpserver.HttpServer`) so the running service itself hosts its own docs.

**How to run the documentation process**

1. **Install `protoc-gen-doc`**

   ```bash
   brew install protoc-gen-doc   # macOS
   # Linux: download the binary from https://github.com/pseudomuto/protoc-gen-doc/releases
   #        and place it on your PATH
   ```

   Verify: `protoc-gen-doc --version` should print a version string.

2. **Update `build.sbt`** — extend `PB.targets` to add the doc generator alongside the existing ScalaPB target:

   ```scala
   Compile / PB.targets := Seq(
     scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
     PB.gens.plugin("doc") -> (baseDirectory.value / "docs" / "generated")
   )
   ```

3. **Create the output directory** (only needed once):

   ```bash
   mkdir -p docs/generated
   ```

4. **Annotate `src/main/proto/hello.proto`** with doc comments as shown in the *Comment style* example above — one `//` comment directly above each message, field, enum value, or RPC.

5. **Generate the docs**:

   ```bash
   sbt --no-colors compile
   ```

   ScalaPB and `protoc-gen-doc` run in the same `protoc` invocation. The plugin writes `docs/generated/index.html`.

6. **View the output**:

   ```bash
   open docs/generated/index.html   # macOS — opens in your default browser
   ```

   The HTML contains a section per message and per service, with a field-by-field table and the inline comments rendered as descriptions.

---

### Option 2: Buf Schema Registry (BSR)

[Buf](https://buf.build) provides hosted, auto-rendered HTML documentation for any module pushed to its schema registry. No protoc plugin required — the `buf` CLI reads the `.proto` files directly.

```bash
brew install bufbuild/buf/buf
buf registry login          # one-time, free account
buf push --tag v0.1.0       # publishes to buf.build/<org>/protobuf1
```

After the push, `buf.build/<org>/protobuf1` shows navigable HTML docs with search, field descriptions, and cross-references — no build step needed. Inline comments in the `.proto` files appear as field descriptions automatically.

**Trade-off:** docs live on Buf's servers, not served by the service itself. Fine for internal tooling; not suitable if you need fully self-hosted docs.

---

### Option 3: grpc-gateway + OpenAPI UI

[grpc-gateway](https://github.com/grpc-ecosystem/grpc-gateway) transcodes gRPC to HTTP/JSON and generates an OpenAPI 2.0 spec, which can then be served via Swagger UI embedded in the service. This gives the closest parity to OpenAPI/Swagger UX but requires the most setup:

1. Add `google/api/annotations.proto` HTTP bindings to the service definition.
2. Run `protoc-gen-openapiv2` to emit a `.swagger.json` file.
3. Bundle Swagger UI as a static resource and serve it from the running server.

For a service like `HelloService` with a single unary RPC and no REST requirement, this is heavy machinery. Worth considering only if you also want a REST gateway alongside the gRPC endpoint.

---

### CI with GitHub Actions (protoc-gen-doc)

The official Docker image `pseudomuto/protoc-gen-doc` (Docker Hub, latest stable `1.5.1`) is the simplest approach in GHA — Docker is pre-installed on `ubuntu-latest` runners, so no extra setup step is needed.

The image uses two volume mounts:
- `/protos` — proto source directory
- `/out` — where generated docs are written

**Option A: Docker (zero toolchain setup)**

```yaml
name: Generate API Docs

on:
  push:
    paths:
      - 'src/main/proto/**'

jobs:
  docs:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Create output directory
        run: mkdir -p docs/generated

      - name: Generate HTML docs from proto
        run: |
          docker run --rm \
            -v ${{ github.workspace }}/src/main/proto:/protos \
            -v ${{ github.workspace }}/docs/generated:/out \
            pseudomuto/protoc-gen-doc:1.5.1 \
            --doc_opt=html,index.html \
            hello.proto

      - name: Upload docs artifact
        uses: actions/upload-artifact@v4
        with:
          name: api-docs
          path: docs/generated/
```

**Option B: go install + setup-protoc (no Docker pull)**

Slightly faster if you cache the Go module cache, and avoids the Docker daemon entirely:

```yaml
      - uses: actions/setup-go@v5
        with:
          go-version: '1.22'

      - uses: arduino/setup-protoc@v3

      - name: Install protoc-gen-doc
        run: go install github.com/pseudomuto/protoc-gen-doc/cmd/protoc-gen-doc@latest

      - name: Generate HTML docs
        run: |
          protoc \
            --doc_out=docs/generated \
            --doc_opt=html,index.html \
            -I src/main/proto \
            src/main/proto/hello.proto
```

**Notes:**
- The image hasn't been updated since v1.5.1 (~2022) but proto3 support is complete and stable.
- No official community GHA Action wraps it; `docker run` in a `run:` step is the standard pattern.
- To commit generated docs back to the repo instead of uploading an artifact, add a `git commit && git push` step using `GITHUB_TOKEN` after generation.

---

### Recommendation

Use **Option 1 (protoc-gen-doc)** for self-hosted HTML served by the service, or **Option 2 (BSR)** if hosted docs with zero build integration is acceptable. Option 3 is only worth the effort if a REST/HTTP gateway is also needed.