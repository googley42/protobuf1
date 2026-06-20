package server

import com.sun.net.httpserver.HttpServer as JHttpServer
import java.net.InetSocketAddress

object DocsServer:

  def start(port: Int = 8080): Unit =
    val server = JHttpServer.create(new InetSocketAddress(port), 0)

    server.createContext("/", exchange =>
      val stream = Option(getClass.getResourceAsStream("/docs/index.html"))
      stream match
        case Some(s) =>
          val bytes = s.readAllBytes()
          s.close()
          exchange.getResponseHeaders.set("Content-Type", "text/html; charset=utf-8")
          exchange.sendResponseHeaders(200, bytes.length)
          val out = exchange.getResponseBody
          out.write(bytes)
          out.close()
        case None =>
          val msg = "Docs not found — run 'sbt compile' to generate them.".getBytes("UTF-8")
          exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=utf-8")
          exchange.sendResponseHeaders(404, msg.length)
          val out = exchange.getResponseBody
          out.write(msg)
          out.close()
    )

    server.start()
    println(s"API docs available at http://localhost:$port/")
