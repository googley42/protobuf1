package client

import hello.hello._

import io.grpc.ManagedChannelBuilder

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object HelloClient extends App {

  given ExecutionContext = ExecutionContext.global

  val channel =
    ManagedChannelBuilder
      .forAddress("localhost", 50051)
      .usePlaintext()
      .build()

  val stub = HelloServiceGrpc.stub(channel)

  val richRequest = HelloRequest(
    name = "Avinder",
    title = Some("Dr."),
    preferredLanguage = HelloRequest.Language.ENGLISH,
    client = Some(
      HelloRequest.ClientInfo(
        appName = "protobuf-learning-client",
        appVersion = "1.0.0"
      )
    ),
    contactMethod = HelloRequest.ContactMethod.Email("avinder@example.com")
  )

  val invalidRequest = HelloRequest(
    name = " ",
    preferredLanguage = HelloRequest.Language.SPANISH,
    contactMethod = HelloRequest.ContactMethod.PhoneNumber("+44-1234-567890")
  )

  def printResponse(label: String, responseF: Future[HelloReply]): Unit = {
    val response = Await.result(responseF, 3.seconds)

    println(s"[$label] message: ${response.message}")
    println(s"[$label] status: ${response.status}")
    println(s"[$label] debug_id: ${response.debugId.getOrElse("<none>")}")
    println(
      s"[$label] metadata: ${response.metadata.map(m => s"${m.server} @ ${m.generatedAtEpochMs}").getOrElse("<none>")}"
    )

    response.extraData match {
      case HelloReply.ExtraData.FollowUp(value) =>
        println(s"[$label] follow_up: $value")
      case HelloReply.ExtraData.RawPayload(value) =>
        println(s"[$label] raw_payload_bytes: ${value.size()}")
      case HelloReply.ExtraData.Empty =>
        println(s"[$label] extra_data: <none>")
    }

    println()
  }

  printResponse("rich-request", stub.sayHello(richRequest))
  printResponse("invalid-request", stub.sayHello(invalidRequest))

  channel.shutdown()
}
