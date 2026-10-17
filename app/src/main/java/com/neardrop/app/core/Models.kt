package com.neardrop.app.core

enum class DeviceType { PHONE, TABLET, PC, UNKNOWN;
    companion object { fun parse(s: String?) = values().firstOrNull { it.name.equals(s, true) } ?: UNKNOWN }
}

enum class Via { LAN, WIFI_DIRECT }

enum class Direction { SEND, RECEIVE }

enum class TransferStatus {
    CONNECTING, AWAITING_APPROVAL, TRANSFERRING, VERIFYING,
    COMPLETED, FAILED, INTERRUPTED, CANCELLED, REJECTED;
    val terminal get() = this in setOf(COMPLETED, FAILED, INTERRUPTED, CANCELLED, REJECTED)
}

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val host: String,
    val port: Int,
    val via: Via,
    /** Hex prefix of the peer identity fingerprint advertised via mDNS; checked after the handshake. */
    val fingerprintPrefix: String?,
)

data class FileEntry(val index: Int, val path: String, val size: Long)

data class TransferProgress(
    val id: String,
    val direction: Direction,
    val peerName: String,
    val status: TransferStatus,
    val fileCount: Int = 0,
    val fileIndex: Int = 0,
    val currentFile: String = "",
    val bytesDone: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Double = 0.0,
    val avgBps: Double = 0.0,
    val etaSeconds: Long? = null,
    /** 6-digit code both users can compare to detect a man-in-the-middle. */
    val sas: String? = null,
    val peerFingerprint: String? = null,
    val message: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val fraction: Float get() = if (totalBytes <= 0) 0f else (bytesDone.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

enum class Visibility { EVERYONE, TRUSTED_ONLY, OFF }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ProtocolException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
class TransferCancelledException : RuntimeException("Transfer cancelled")
class TransferRejectedException(val reason: String) : RuntimeException(reason)

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble(); var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format("%.1f %s", v, units[i])
    }
    fun speed(bps: Double) = String.format("%.2f MB/s", bps / (1024 * 1024))
    fun eta(s: Long?): String = when {
        s == null -> "—"
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}
