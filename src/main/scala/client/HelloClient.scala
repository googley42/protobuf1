package client

import hello.hello._

import io.grpc.ManagedChannelBuilder

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object HelloClient extends App {

  given ExecutionContext = ExecutionContext.global

  val channel =
    ManagedChannelBuilder
      .forAddress("localhost", 50051)
      .usePlaintext()
      .build()

  val stub = HelloServiceGrpc.stub(channel)

  val response: Future[HelloReply] =
    stub.sayHello(HelloRequest("Avinder"))

  response.foreach(r => println(r.message))

  Thread.sleep(2000)

  channel.shutdown()
}
