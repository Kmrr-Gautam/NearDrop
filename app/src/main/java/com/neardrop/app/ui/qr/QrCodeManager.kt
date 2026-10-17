package com.neardrop.app.ui.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.EnumMap

data class ReceiverQrInfo(
    val ip: String,
    val port: Int,
    val name: String,
    val fingerprint: String? = null,
    val ssid: String? = null,
    val passphrase: String? = null,
    val isHotspot: Boolean = false
)

object QrCodeManager {

    /**
     * Encodes receiver connection details into a compact JSON string.
     */
    fun encodeReceiverInfo(
        ip: String,
        port: Int,
        name: String,
        fingerprint: String? = null,
        ssid: String? = null,
        passphrase: String? = null,
        isHotspot: Boolean = false
    ): String {
        return JSONObject().apply {
            put("protocol", "neardrop")
            put("ip", ip)
            put("port", port)
            put("name", name)
            if (fingerprint != null) put("fp", fingerprint)
            if (ssid != null) put("ssid", ssid)
            if (passphrase != null) put("pass", passphrase)
            if (isHotspot) put("hotspot", true)
        }.toString()
    }

    /**
     * Parses receiver connection details from JSON, URI string, plain IP, Wi-Fi QR, or web payloads.
     */
    fun parseReceiverInfo(rawText: String): ReceiverQrInfo? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        try {
            // 0. Standard Wi-Fi QR format: WIFI:S:<ssid>;T:<WPA|WEP|nopass>;P:<passphrase>;;
            if (text.startsWith("WIFI:", ignoreCase = true)) {
                val ssid = Regex("""S:([^;]+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
                val pass = Regex("""P:([^;]+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1) ?: ""
                if (!ssid.isNullOrBlank()) {
                    return ReceiverQrInfo(
                        ip = "192.168.43.1",
                        port = 45454,
                        name = "Hotspot ($ssid)",
                        ssid = ssid,
                        passphrase = pass,
                        isHotspot = true
                    )
                }
            }

            // 1. JSON format (Android native, Hotspot, or PC Web)
            if (text.startsWith("{")) {
                val json = JSONObject(text)
                val ip = json.optString("ip").takeIf { it.isNotBlank() }
                    ?: json.optString("host").takeIf { it.isNotBlank() }
                val port = json.optInt("port", 45454)
                val name = json.optString("name").takeIf { it.isNotBlank() } ?: "Receiver"
                val fp = json.optString("fp").takeIf { it.isNotBlank() }
                val ssid = json.optString("ssid").takeIf { it.isNotBlank() }
                val pass = json.optString("pass").takeIf { it.isNotBlank() }
                val isHotspot = json.optBoolean("hotspot", false) || ssid != null
                if (ip != null) {
                    return ReceiverQrInfo(ip, port, name, fp, ssid, pass, isHotspot)
                }

                // If JSON only has room code (WebRTC room from PC)
                val room = json.optString("room").takeIf { it.isNotBlank() }
                if (room != null) {
                    return ReceiverQrInfo(ip = "room:$room", port = 0, name = name, fingerprint = room)
                }
            }

            // 2. HTTP / HTTPS URL format (e.g. http://192.168.1.15:45455 or http://192.168.1.15:45454)
            if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
                val uri = URI(text)
                val host = uri.host
                if (!host.isNullOrBlank()) {
                    val port = if (uri.port > 0) uri.port else if (text.startsWith("https", ignoreCase = true)) 443 else 80
                    val query = uri.query ?: ""
                    val name = if (query.contains("name=")) {
                        query.substringAfter("name=").substringBefore("&")
                    } else "Web ($host)"
                    return ReceiverQrInfo(host, port, name)
                }
            }

            // 3. NearDrop custom URI format: neardrop://connect?ip=...&port=...&name=...
            if (text.startsWith("neardrop://", ignoreCase = true)) {
                val uri = URI(text)
                val query = uri.query ?: ""
                val params = query.split("&").associate { param ->
                    val parts = param.split("=", limit = 2)
                    val k = URLDecoder.decode(parts[0], "UTF-8")
                    val v = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                    k to v
                }
                val ip = params["ip"] ?: uri.host ?: return null
                val port = params["port"]?.toIntOrNull() ?: if (uri.port > 0) uri.port else 45454
                val name = params["name"] ?: "Receiver"
                val fp = params["fp"]
                return ReceiverQrInfo(ip, port, name, fp)
            }

            // 4. Plain IP or IP:Port format (e.g. 192.168.1.15:45454 or 192.168.1.15)
            val ipRegex = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
            val match = ipRegex.find(text)
            if (match != null) {
                val foundIp = match.value
                val portRegex = Regex("""(?::|/|: )(\d{2,5})""")
                val portMatch = portRegex.find(text.substring(match.range.last))
                val foundPort = portMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 45454
                return ReceiverQrInfo(foundIp, foundPort, "Device ($foundIp)")
            }

            // 5. 6-digit room code format (e.g. 582914)
            if (text.matches(Regex("""^\d{6}$"""))) {
                return ReceiverQrInfo(ip = "room:$text", port = 0, name = "Web Room ($text)", fingerprint = text)
            }
        } catch (_: Exception) {
            // ignore parsing failures
        }
        return null
    }

    /**
     * Generates a high-contrast QR code Bitmap using ZXing.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 1)
            }
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Analyzes an ImageProxy from CameraX to detect a QR code accurately.
     * Corrects row stride padding, handles rotation (portrait/landscape),
     * and utilizes dual binarizers for optimal detection on PC monitors.
     */
    @OptIn(ExperimentalGetImage::class)
    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        val image = imageProxy.image ?: return null
        val planes = image.planes
        if (planes.isEmpty()) return null

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height

        // Extract tightly packed Y plane bytes without rowStride padding
        val yBytes = ByteArray(width * height)
        val origPos = yBuffer.position()
        for (y in 0 until height) {
            yBuffer.position(origPos + y * rowStride)
            yBuffer.get(yBytes, y * width, width)
        }
        yBuffer.position(origPos)

        val rotation = imageProxy.imageInfo.rotationDegrees

        // Rotate if necessary to match display orientation
        val (finalData, finalWidth, finalHeight) = when (rotation) {
            90 -> {
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[x * height + (height - y - 1)] = yBytes[y * width + x]
                    }
                }
                Triple(rotated, height, width)
            }
            180 -> {
                val rotated = ByteArray(width * height)
                val total = width * height
                for (i in 0 until total) {
                    rotated[total - i - 1] = yBytes[i]
                }
                Triple(rotated, width, height)
            }
            270 -> {
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[(width - x - 1) * height + y] = yBytes[y * width + x]
                    }
                }
                Triple(rotated, height, width)
            }
            else -> Triple(yBytes, width, height)
        }

        val source = PlanarYUVLuminanceSource(
            finalData,
            finalWidth,
            finalHeight,
            0,
            0,
            finalWidth,
            finalHeight,
            false
        )

        val reader = MultiFormatReader().apply {
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
                put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            }
            setHints(hints)
        }

        // Try standard HybridBinarizer first
        val hybridBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = reader.decodeWithState(hybridBitmap)
            return result.text
        } catch (_: Exception) {
            // Fall through to GlobalHistogramBinarizer
        } finally {
            reader.reset()
        }

        // Fallback to GlobalHistogramBinarizer for bright PC monitors & screen glare
        return try {
            val globalBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val result = reader.decodeWithState(globalBitmap)
            result.text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
