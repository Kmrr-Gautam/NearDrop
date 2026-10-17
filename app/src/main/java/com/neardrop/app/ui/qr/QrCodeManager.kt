package com.neardrop.app.ui.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
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
    val fingerprint: String? = null
)

object QrCodeManager {

    /**
     * Encodes receiver connection details into a compact JSON string.
     */
    fun encodeReceiverInfo(ip: String, port: Int, name: String, fingerprint: String? = null): String {
        return JSONObject().apply {
            put("protocol", "neardrop")
            put("ip", ip)
            put("port", port)
            put("name", name)
            if (fingerprint != null) put("fp", fingerprint)
        }.toString()
    }

    /**
     * Parses receiver connection details from JSON or URI string.
     */
    fun parseReceiverInfo(rawText: String): ReceiverQrInfo? {
        val text = rawText.trim()
        try {
            // Try JSON format
            if (text.startsWith("{")) {
                val json = JSONObject(text)
                val ip = json.optString("ip").takeIf { it.isNotBlank() } ?: return null
                val port = json.optInt("port", 45454)
                val name = json.optString("name").takeIf { it.isNotBlank() } ?: "Receiver"
                val fp = json.optString("fp").takeIf { it.isNotBlank() }
                return ReceiverQrInfo(ip, port, name, fp)
            }

            // Try URI format: neardrop://connect?ip=...&port=...&name=...
            if (text.startsWith("neardrop://")) {
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

            // Plain ip or ip:port format
            if (text.contains(".") && !text.contains(" ")) {
                val parts = text.split(":")
                val ip = parts[0].trim()
                val port = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 45454 else 45454
                return ReceiverQrInfo(ip, port, "Device ($ip)")
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
     * Analyzes an ImageProxy from CameraX to detect a QR code.
     */
    @OptIn(ExperimentalGetImage::class)
    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        val image = imageProxy.image ?: return null
        val planes = image.planes
        if (planes.isEmpty()) return null

        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = imageProxy.width
        val height = imageProxy.height

        val source = PlanarYUVLuminanceSource(
            bytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
            }
            setHints(hints)
        }

        return try {
            val result = reader.decodeWithState(binaryBitmap)
            result.text
        } catch (_: NotFoundException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
