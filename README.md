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

The server serves HTML documentation generated from `hello.proto` comments at `http://localhost:8080/`. The docs are bundled on the classpath as part of `sbt compile` (no separate output directory) and read via the classpath at runtime, so they work in a deployed JAR without any filesystem dependency.

To regenerate after editing `hello.proto`:

```bash
sbt --no-colors compile
```

Then restart the server.

