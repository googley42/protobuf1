package server

import hello.hello._
import io.grpc.ServerBuilder

import scala.concurrent.{ExecutionContext, Future}

class HelloServiceImpl(using ExecutionContext)
  extends HelloServiceGrpc.HelloService {

  override def sayHello(req: HelloRequest): Future[HelloReply] =
    Future.successful(
      HelloReply(message = s"Hello ${req.name}")
    )
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
