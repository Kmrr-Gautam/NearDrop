package com.neardrop.app.web

import android.content.Context
import com.neardrop.app.core.Hex
import com.neardrop.app.data.TransferRecord
import com.neardrop.app.transfer.NearDropEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID

/**
 * Lightweight embedded HTTP/Web bridge server running on Android.
 * Enables direct PC-to-Mobile and Mobile-to-PC file streaming over local Wi-Fi.
 */
class WebBridgeServer(
    private val ctx: Context,
    private val engine: NearDropEngine,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(DEFAULT_PORT)
    val port = _port.asStateFlow()

    // Outgoing file queue (files queued on Android to be downloaded by PC Web)
    private val outgoingQueue = mutableListOf<PendingWebFile>()

    data class PendingWebFile(
        val id: String,
        val file: File,
        val name: String,
        val size: Long
    )

    fun start(bindPort: Int = DEFAULT_PORT) {
        if (_isRunning.value) return
        job = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket(bindPort).apply {
                    reuseAddress = true
                }
                serverSocket = ss
                _port.value = ss.localPort
                _isRunning.value = true

                while (isActive && !ss.isClosed) {
                    val client = try { ss.accept() } catch (e: IOException) { break }
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                // If port is busy, fallback to dynamic port
                if (serverSocket == null) {
                    try {
                        val ss = ServerSocket(0).apply { reuseAddress = true }
                        serverSocket = ss
                        _port.value = ss.localPort
                        _isRunning.value = true
                        while (isActive && !ss.isClosed) {
                            val client = try { ss.accept() } catch (e: IOException) { break }
                            launch(Dispatchers.IO) {
                                handleClient(client)
                            }
                        }
                    } catch (_: Exception) {}
                }
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        job?.cancel()
        job = null
    }

    fun queueFileForPc(file: File) {
        synchronized(outgoingQueue) {
            outgoingQueue.add(PendingWebFile(UUID.randomUUID().toString(), file, file.name, file.length()))
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            // Read HTTP request line
            val requestLine = readLine(input) ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = if (rawPath.contains("?")) rawPath.substringBefore("?") else rawPath
            val queryStr = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""
            val queryParams = parseQuery(queryStr)

            // Read Headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            // Handle CORS Preflight
            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                return@withContext
            }

            // Route Requests
            when {
                path == "/api/info" && method == "GET" -> {
                    val deviceName = engine.currentDeviceName
                    val json = JSONObject().apply {
                        put("device", deviceName)
                        put("version", "0.2.1")
                        put("port", _port.value)
                        put("type", "android")
                        put("status", "ready")
                    }
                    sendJsonResponse(output, 200, json)
                }

                path == "/api/queue" && method == "GET" -> {
                    val arr = JSONArray()
                    synchronized(outgoingQueue) {
                        for (f in outgoingQueue) {
                            arr.put(JSONObject().apply {
                                put("id", f.id)
                                put("name", f.name)
                                put("size", f.size)
                            })
                        }
                    }
                    sendJsonResponse(output, 200, arr)
                }

                path == "/api/download" && method == "GET" -> {
                    val id = queryParams["id"]
                    val pending = synchronized(outgoingQueue) { outgoingQueue.find { it.id == id } }
                    if (pending != null && pending.file.exists()) {
                        sendFileResponse(output, pending.file)
                    } else {
                        sendResponse(output, 404, "Not Found", "text/plain", "File not found".toByteArray())
                    }
                }

                path == "/api/upload" && method == "POST" -> {
                    val fileName = queryParams["name"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "received_file_${System.currentTimeMillis()}"
                    val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L

                    val targetFile = getUniqueFile(engine.receivedDir, fileName)
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L

                    FileOutputStream(targetFile).use { fos ->
                        if (contentLength > 0) {
                            var remaining = contentLength
                            while (remaining > 0) {
                                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                val n = input.read(buffer, 0, toRead)
                                if (n < 0) break
                                fos.write(buffer, 0, n)
                                written += n
                                remaining -= n
                            }
                        } else {
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                fos.write(buffer, 0, n)
                                written += n
                            }
                        }
                    }

                    // Record into DB
                    val record = TransferRecord(
                        id = UUID.randomUUID().toString(),
                        direction = "RECV",
                        peerName = "NearDrop PC Web",
                        peerFingerprint = "pc-web",
                        fileCount = 1,
                        totalBytes = written,
                        bytesDone = written,
                        status = "COMPLETED",
                        message = null,
                        startedAt = System.currentTimeMillis(),
                        finishedAt = System.currentTimeMillis(),
                        fileNames = targetFile.name
                    )
                    engine.db.transfers().upsert(record)

                    val respJson = JSONObject().apply {
                        put("status", "ok")
                        put("name", targetFile.name)
                        put("size", written)
                    }
                    sendJsonResponse(output, 200, respJson)
                }

                // Serve Bundled Claymorphic Web App from Assets
                else -> {
                    val assetPath = if (path == "/" || path == "/index.html") "/index.html" else path
                    val mime = when {
                        assetPath.endsWith(".html") -> "text/html; charset=utf-8"
                        assetPath.endsWith(".js") -> "application/javascript; charset=utf-8"
                        assetPath.endsWith(".css") -> "text/css; charset=utf-8"
                        assetPath.endsWith(".svg") -> "image/svg+xml"
                        assetPath.endsWith(".png") -> "image/png"
                        assetPath.endsWith(".json") -> "application/json"
                        else -> "application/octet-stream"
                    }
                    try {
                        val cleanPath = "web" + (if (assetPath.startsWith("/")) assetPath else "/$assetPath")
                        val bytes = ctx.assets.open(cleanPath).use { it.readBytes() }
                        sendResponse(output, 200, "OK", mime, bytes)
                    } catch (_: Exception) {
                        if (path == "/" || path == "/index.html") {
                            val html = buildLandingHtml(engine.currentDeviceName)
                            sendResponse(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                        } else {
                            sendResponse(output, 404, "Not Found", "text/plain", "Not Found".toByteArray())
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readLine(ins: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (ins.read().also { c = it } != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return if (sb.isEmpty() && c == -1) null else sb.toString()
    }

    private fun parseQuery(qs: String): Map<String, String> {
        if (qs.isBlank()) return emptyMap()
        return qs.split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else null
        }.toMap()
    }

    private fun sendJsonResponse(output: OutputStream, code: Int, json: Any) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, code, "OK", "application/json; charset=utf-8", bytes)
    }

    private fun sendResponse(output: OutputStream, code: Int, statusText: String, contentType: String, data: ByteArray) {
        val headers = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        if (data.isNotEmpty()) {
            output.write(data)
        }
        output.flush()
    }

    private fun sendFileResponse(output: OutputStream, file: File) {
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                output.write(buf, 0, n)
            }
        }
        output.flush()
    }

    private fun getUniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.')
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var idx = 1
        while (f.exists()) {
            f = File(dir, "$base ($idx)$ext")
            idx++
        }
        return f
    }

    private fun buildLandingHtml(deviceName: String): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>NearDrop Bridge · $deviceName</title>
              <style>
                body { font-family: system-ui, -apple-system, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 32px 20px; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 90vh; }
                .card { background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 24px; padding: 32px; max-width: 480px; width: 100%; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }
                h1 { margin: 0 0 8px; font-size: 24px; color: #818cf8; }
                p { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
                .dropzone { border: 2px dashed #6366f1; border-radius: 16px; padding: 36px 20px; cursor: pointer; background: rgba(99, 102, 241, 0.05); transition: all 0.2s; }
                .dropzone:hover { background: rgba(99, 102, 241, 0.1); transform: scale(1.02); }
                .btn { display: inline-block; margin-top: 18px; padding: 12px 24px; background: #6366f1; color: white; border-radius: 12px; font-weight: bold; text-decoration: none; cursor: pointer; border: none; }
                .btn:hover { background: #4f46e5; }
                .status { margin-top: 16px; font-size: 13px; color: #10b981; font-weight: bold; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>NearDrop Web Bridge</h1>
                <p>Connected to <strong>$deviceName</strong> (Android)</p>
                <div class="dropzone" id="dz" onclick="document.getElementById('fi').click()">
                  <input type="file" id="fi" multiple style="display:none">
                  <div style="font-size:36px;margin-bottom:8px">📤</div>
                  <strong>Click or Drag Files Here</strong>
                  <div style="font-size:12px;color:#94a3b8;margin-top:4px">Sends files directly to your Android device</div>
                </div>
                <div class="status" id="st">Ready for transfer</div>
              </div>
              <script>
                const fi = document.getElementById('fi'), st = document.getElementById('st'), dz = document.getElementById('dz');
                fi.onchange = (e) => uploadFiles(Array.from(e.target.files));
                dz.ondragover = (e) => { e.preventDefault(); dz.style.borderColor = '#10b981'; };
                dz.ondragleave = (e) => { e.preventDefault(); dz.style.borderColor = '#6366f1'; };
                dz.ondrop = (e) => { e.preventDefault(); dz.style.borderColor = '#6366f1'; uploadFiles(Array.from(e.dataTransfer.files)); };
                async function uploadFiles(files) {
                  for (const f of files) {
                    st.textContent = 'Sending ' + f.name + ' (' + Math.round(f.size/1024) + ' KB)...';
                    try {
                      const res = await fetch('/api/upload?name=' + encodeURIComponent(f.name) + '&size=' + f.size, { method: 'POST', body: f });
                      if (res.ok) st.textContent = '✅ ' + f.name + ' received successfully!';
                      else st.textContent = '❌ Failed to send ' + f.name;
                    } catch(err) { st.textContent = '❌ Error: ' + err.message; }
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        const val DEFAULT_PORT = 45455
    }
}
