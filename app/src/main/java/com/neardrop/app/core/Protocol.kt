package com.neardrop.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** NearDrop wire protocol v1 constants. See docs/protocol.md. */
object Proto {
    const val VERSION = 1
    const val MAGIC = 0x4E445250 // "NDRP"
    const val FRAME_CONTROL: Byte = 1
    const val FRAME_DATA: Byte = 2
    const val MAX_FRAME = 8 * 1024 * 1024
    const val DEFAULT_CHUNK = 256 * 1024
    const val DEFAULT_PORT = 45454
    const val SERVICE_TYPE = "_neardrop._tcp."
    const val BLE_SERVICE_UUID = "7d1a6a4e-0b3c-4f0a-9f5e-2f6a1c7b0d11"
    const val MAX_FILES = 50_000
    const val MAX_NAME = 64
}

object Hex {
    fun encode(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}

/** Validates and normalises relative paths from untrusted peers (path traversal protection). */
object PathSanitizer {
    private const val MAX_SEGMENT = 255
    private const val MAX_DEPTH = 32
    private const val MAX_TOTAL = 1024

    fun sanitize(raw: String): String {
        if (raw.isEmpty() || raw.length > MAX_TOTAL) throw ProtocolException("Invalid path length")
        if (raw.startsWith("/") || raw.contains('\\') || raw.contains('\u0000')) throw ProtocolException("Invalid path")
        val segs = raw.split('/')
        if (segs.size > MAX_DEPTH) throw ProtocolException("Path too deep")
        for (s in segs) {
            if (s.isEmpty() || s == "." || s == ".." || s.length > MAX_SEGMENT) throw ProtocolException("Invalid path segment")
            if (s.any { it.isISOControl() } || s.contains(':') || s.endsWith(".") || s.endsWith(" ")) throw ProtocolException("Invalid path segment")
        }
        return segs.joinToString("/")
    }
}

object Manifest {
    fun toJson(files: List<FileEntry>): JSONArray = JSONArray().also { arr ->
        files.forEach { arr.put(JSONObject().put("i", it.index).put("p", it.path).put("s", it.size)) }
    }

    fun parse(arr: JSONArray): List<FileEntry> {
        if (arr.length() > Proto.MAX_FILES) throw ProtocolException("Too many files")
        val out = ArrayList<FileEntry>(arr.length())
        val seen = HashSet<String>()
        for (n in 0 until arr.length()) {
            val o = arr.optJSONObject(n) ?: throw ProtocolException("Bad manifest entry")
            val idx = o.optInt("i", -1)
            val size = o.optLong("s", -1)
            if (idx != n) throw ProtocolException("Manifest indices must be sequential")
            if (size < 0) throw ProtocolException("Negative file size")
            val path = PathSanitizer.sanitize(o.optString("p"))
            if (!seen.add(path.lowercase())) throw ProtocolException("Duplicate path")
            out.add(FileEntry(idx, path, size))
        }
        return out
    }

    fun totalSize(files: List<FileEntry>): Long {
        var t = 0L
        for (f in files) { t = Math.addExact(t, f.size) }
        return t
    }
}

object FileNames {
    /** Returns a non-existing file inside [dir] for [relPath], keeping folder structure and adding " (n)" on collision. */
    fun uniqueFile(dir: File, relPath: String): File {
        val clean = PathSanitizer.sanitize(relPath)
        val root = dir.canonicalFile
        val target = File(root, clean)
        val parent = target.parentFile ?: throw ProtocolException("Invalid path")
        parent.mkdirs()
        if (!parent.canonicalPath.startsWith(root.path + File.separator) && parent.canonicalPath != root.path)
            throw ProtocolException("Path escapes destination")
        if (!target.exists()) return target
        val name = target.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val c = File(parent, "$base ($n)$ext")
            if (!c.exists()) return c
            n++
        }
    }
}

object IntegrityVerifier {
    fun newDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun sha256Hex(file: File): String = file.inputStream().use { ins ->
        val d = newDigest(); val buf = ByteArray(64 * 1024)
        while (true) { val n = ins.read(buf); if (n < 0) break; d.update(buf, 0, n) }
        Hex.encode(d.digest())
    }

    /** SHA-256 over the first [length] bytes of [file]. Used to validate resume offsets. */
    fun prefixHex(file: File, length: Long): String = file.inputStream().use { ins ->
        val d = newDigest(); val buf = ByteArray(64 * 1024); var left = length
        while (left > 0) {
            val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw ProtocolException("Short file")
            d.update(buf, 0, n); left -= n
        }
        Hex.encode(d.digest())
    }
}
