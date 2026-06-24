# Protobuf + gRPC Learning Example (Scala)

This project demonstrates these Protobuf modeling features in `src/main/proto/hello.proto`:

- optional fields
- enums
- nested messages
- oneof fields

The Scala apps in `src/main/scala/server/HelloServer.scala` and `src/main/scala/client/HelloClient.scala` show how to set and read those fields end-to-end.

## Quick try

```bash
sbt --no-colors compile
```

Terminal 1:

```bash
sbt --no-colors "runMain server.HelloServer"
```

Terminal 2:

```bash
sbt --no-colors "runMain client.HelloClient"
```

You should see two calls:

1. A rich request using optional title, enum language, nested client info, and oneof email.
2. An invalid request path that returns `INVALID_ARGUMENT` and a debug id.

## API docs

`sbt compile` generates HTML documentation from `hello.proto` comments and bundles it on the classpath. When the server runs, `DocsServer` starts an HTTP server on port 8080:

| URL | Content |
|---|---|
| `http://localhost:8080/` | Index of all service docs discovered on the classpath |
| `http://localhost:8080/docs/hello-service/index.html` | HelloService API reference |

The index is built dynamically by scanning for `META-INF/grpc-docs` manifest files on the classpath. Any JAR that follows the convention (a `META-INF/grpc-docs` manifest + docs at `docs/<service-name>/index.html`) appears automatically in the index. This means adding a dependency on another proto module's published JAR brings its docs along for free — no extra configuration.

To regenerate after editing `hello.proto`:

```bash
sbt --no-colors compile
```

Then restart the server. See [docs/grpc_api_doc_tools.md](docs/grpc_api_doc_tools.md) for the full design including the multi-module architecture.

