package server

import com.sun.net.httpserver.HttpServer as JHttpServer
import java.net.{InetSocketAddress, JarURLConnection}
import scala.jdk.CollectionConverters.*

object DocsServer:

  // Scans all classpath entries (JARs and file directories) for docs/<name>/index.html.
  // This means any JAR on the classpath that bundles docs at this convention is discovered
  // automatically — including JARs published by external-facing proto modules.
  private def discoverServices(): List[String] =
    val cl = Thread.currentThread().getContextClassLoader
    cl.getResources("docs").asScala.toList.flatMap: url =>
      url.getProtocol match
        case "jar" =>
          val conn = url.openConnection().asInstanceOf[JarURLConnection]
          try
            conn.getJarFile.entries().asScala
              .map(_.getName)
              .filter(_.matches("docs/[^/]+/index\\.html"))
              .map(_.split("/")(1))
              .toList
          finally conn.getInputStream.close()
        case "file" =>
          val dir = java.io.File(url.toURI)
          if dir.exists && dir.isDirectory then
            dir.listFiles().toList
              .filter(_.isDirectory)
              .filter(d => java.io.File(d, "index.html").exists())
              .map(_.getName)
          else Nil
        case _ => Nil
    .distinct.sorted

  private def buildIndex(services: List[String]): Array[Byte] =
    val rows = services.map: name =>
      s"""      <tr>
         |        <td><a href="/docs/$name/index.html">$name</a></td>
         |        <td><code>docs/$name/index.html</code></td>
         |      </tr>""".stripMargin
    .mkString("\n")
    val empty = if services.isEmpty then
      """<p style="color:#888">No service docs found on classpath. Run <code>sbt compile</code> to generate them.</p>"""
    else ""
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="utf-8">
       |  <title>API Docs Index</title>
       |  <style>
       |    body { font-family: system-ui, sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; color: #222; }
       |    h1 { border-bottom: 2px solid #ddd; padding-bottom: 8px; }
       |    table { border-collapse: collapse; width: 100%; }
       |    th { text-align: left; padding: 8px 12px; background: #f5f5f5; border-bottom: 2px solid #ddd; }
       |    td { padding: 8px 12px; border-bottom: 1px solid #eee; }
       |    a { color: #0066cc; text-decoration: none; }
       |    a:hover { text-decoration: underline; }
       |    code { background: #f5f5f5; padding: 2px 5px; border-radius: 3px; font-size: 0.9em; }
       |  </style>
       |</head>
       |<body>
       |  <h1>API Docs Index</h1>
       |  <p>Services discovered on the classpath:</p>
       |  $empty
       |  ${if services.nonEmpty then
       s"""<table>
          |    <thead><tr><th>Service</th><th>Classpath resource</th></tr></thead>
          |    <tbody>
          |$rows
          |    </tbody>
          |  </table>""".stripMargin
       else ""}
       |</body>
       |</html>""".stripMargin.getBytes("UTF-8")

  def start(port: Int = 8080): Unit =
    val httpServer = JHttpServer.create(new InetSocketAddress(port), 0)

    // /docs/<service>/... → serve the bundled HTML from classpath
    httpServer.createContext("/docs/", exchange =>
      val path = exchange.getRequestURI.getPath           // e.g. /docs/hello-service/index.html
      val resourcePath = path.stripPrefix("/")            // docs/hello-service/index.html
      Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match
        case Some(s) =>
          val bytes = s.readAllBytes()
          s.close()
          exchange.getResponseHeaders.set("Content-Type", "text/html; charset=utf-8")
          exchange.sendResponseHeaders(200, bytes.length)
          val out = exchange.getResponseBody
          out.write(bytes)
          out.close()
        case None =>
          val msg = s"Not found: $path".getBytes("UTF-8")
          exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=utf-8")
          exchange.sendResponseHeaders(404, msg.length)
          val out = exchange.getResponseBody
          out.write(msg)
          out.close()
    )

    // / → dynamically built index of all discovered services
    httpServer.createContext("/", exchange =>
      val bytes = buildIndex(discoverServices())
      exchange.getResponseHeaders.set("Content-Type", "text/html; charset=utf-8")
      exchange.sendResponseHeaders(200, bytes.length)
      val out = exchange.getResponseBody
      out.write(bytes)
      out.close()
    )

    httpServer.start()
    val services = discoverServices()
    println(s"Docs index:  http://localhost:$port/")
    services.foreach(s => println(s"  $s -> http://localhost:$port/docs/$s/index.html"))
