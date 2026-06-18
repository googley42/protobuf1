package server

import com.sun.net.httpserver.HttpServer as JHttpServer
import java.net.InetSocketAddress
import java.nio.file.{Files, Path}

object DocsServer:

  def start(port: Int = 8080): Unit =
    val server = JHttpServer.create(new InetSocketAddress(port), 0)

    server.createContext("/", exchange =>
      val docsPath = Path.of("docs/generated/index.html")
      if Files.exists(docsPath) then
        val bytes = Files.readAllBytes(docsPath)
        exchange.getResponseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.length)
        val out = exchange.getResponseBody
        out.write(bytes)
        out.close()
      else
        val msg = "Docs not found — run 'sbt compile' to generate them.".getBytes("UTF-8")
        exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(404, msg.length)
        val out = exchange.getResponseBody
        out.write(msg)
        out.close()
    )

    server.start()
    println(s"API docs available at http://localhost:$port/")
