package server

import hello.hello._
import io.grpc.ServerBuilder

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class HelloServiceImpl(using ExecutionContext)
  extends HelloServiceGrpc.HelloService {

  override def sayHello(req: HelloRequest): Future[HelloReply] = {
    if (req.name.trim.isEmpty) {
      Future.successful(
        HelloReply(
          message = "Name is required.",
          status = HelloReply.Status.INVALID_ARGUMENT,
          debugId = Some(s"dbg-${Instant.now().toEpochMilli}"),
          metadata = Some(
            HelloReply.ReplyMetadata(
              generatedAtEpochMs = Instant.now().toEpochMilli,
              server = "hello-server:50051"
            )
          ),
          extraData = HelloReply.ExtraData.FollowUp("Please send a non-empty name.")
        )
      )
    } else {
      val greeting = req.preferredLanguage match {
        case HelloRequest.Language.ENGLISH => "Hello"
        case HelloRequest.Language.SPANISH => "Hola"
        case HelloRequest.Language.PUNJABI => "Sat Sri Akaal"
        case _                             => "Hello"
      }

      val displayName = req.title match {
        case Some(t) => s"$t ${req.name}"
        case None    => req.name
      }

      val contactSummary = req.contactMethod match {
        case HelloRequest.ContactMethod.Email(value)       => s"We can email you at $value."
        case HelloRequest.ContactMethod.PhoneNumber(value) => s"We can call you at $value."
        case HelloRequest.ContactMethod.Empty              => "No contact method shared."
      }

      val clientSummary = req.client
        .map(c => s" Requested from ${c.appName} ${c.appVersion}.")
        .getOrElse("")

      Future.successful(
        HelloReply(
          message = s"$greeting, $displayName!$clientSummary",
          status = HelloReply.Status.OK,
          metadata = Some(
            HelloReply.ReplyMetadata(
              generatedAtEpochMs = Instant.now().toEpochMilli,
              server = "hello-server:50051"
            )
          ),
          extraData = HelloReply.ExtraData.FollowUp(contactSummary)
        )
      )
    }
  }
}

object HelloServer extends App {

  given ExecutionContext = ExecutionContext.global

  val service = HelloServiceGrpc.bindService(
    new HelloServiceImpl,
    summon[ExecutionContext]
  )

  val server =
    ServerBuilder
      .forPort(50051)
      .addService(service)
      .build

  server.start()

  println("gRPC server started on port 50051")

  // Add shutdown hook for graceful termination
  sys.addShutdownHook {
    println("Shutting down gRPC server...")
    server.shutdown()
    println("gRPC server stopped.")
  }

  server.awaitTermination()
}
