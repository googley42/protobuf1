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

