package com.neardrop.app.transfer

import com.neardrop.app.core.*
import com.neardrop.app.security.IdentityProvider
import com.neardrop.app.security.SecureChannel
import com.neardrop.app.security.SecureHandshake
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.Socket
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class LocalDeviceInfo(val name: String, val type: DeviceType)

data class TransferConfig(
    val chunkSize: Int = Proto.DEFAULT_CHUNK,
    val handshakeTimeoutMs: Int = 15_000,
    val ioTimeoutMs: Int = 30_000,
    val approvalTimeoutMs: Int = 90_000,
)

interface TransferListener {
    fun onHandshake(peerFingerprint: String, sas: String) {}
    fun onStatus(status: TransferStatus, message: String? = null) {}
    fun onProgress(bytesDone: Long, totalBytes: Long, fileIndex: Int, fileName: String) {}
    fun onManifest(peerName: String, files: List<FileEntry>) {}
}

interface SendSource {
    val entry: FileEntry
    fun open(): InputStream
}

data class IncomingRequest(
    val transferId: String,
    val senderName: String,
    val senderType: DeviceType,
    val peerFingerprint: String,
    val sas: String,
    val files: List<FileEntry>,
) { val totalBytes get() = files.sumOf { it.size } }

private fun JSONObject.ctrl(type: String) = put("type", type)

private fun SecureChannel.sendControl(o: JSONObject) {
    val b = o.toString().toByteArray(Charsets.UTF_8)
    send(Proto.FRAME_CONTROL, b)
}

private fun SecureChannel.readControl(): JSONObject {
    val f = receive()
    if (f.type != Proto.FRAME_CONTROL) throw ProtocolException("Expected control frame")
    val o = try { JSONObject(String(f.payload, Charsets.UTF_8)) } catch (e: Exception) { throw ProtocolException("Bad control message", e) }
    if (o.optString("type") == "cancel") throw TransferCancelledException()
    return o
}

class TransferSender(
    private val identity: IdentityProvider,
    private val local: LocalDeviceInfo,
    private val config: TransferConfig = TransferConfig(),
) {
    /**
     * Blocking. Throws [TransferCancelledException], [TransferRejectedException] or [IOException].
     * An IOException after bytes were exchanged means "interrupted" and may be resumed by calling again with the same [transferId].
     */
    fun send(
        socket: Socket,
        transferId: String,
        sources: List<SendSource>,
        expectedFingerprintPrefix: String?,
        listener: TransferListener,
        isCancelled: () -> Boolean,
    ) {
        socket.tcpNoDelay = true
        socket.soTimeout = config.handshakeTimeoutMs
        val ch = SecureHandshake.perform(socket, true, identity)
        if (expectedFingerprintPrefix != null && !ch.peerFingerprint.startsWith(expectedFingerprintPrefix, true))
            throw ProtocolException("Peer identity does not match the discovered device")
        listener.onHandshake(ch.peerFingerprint, ch.sas)

        val files = sources.map { it.entry }
        val total = Manifest.totalSize(files)
        listener.onStatus(TransferStatus.AWAITING_APPROVAL)
        ch.sendControl(JSONObject().ctrl("request")
            .put("v", Proto.VERSION).put("id", transferId)
            .put("name", local.name).put("device", local.type.name)
            .put("files", Manifest.toJson(files)))

        socket.soTimeout = config.approvalTimeoutMs
        val resp = ch.readControl()
        if (resp.optString("type") != "response") throw ProtocolException("Expected response")
        if (!resp.optBoolean("accept")) throw TransferRejectedException(resp.optString("reason", "Rejected by recipient"))
        socket.soTimeout = config.ioTimeoutMs

        val resume = HashMap<Int, Pair<Long, String>>()
        resp.optJSONArray("resume")?.let { arr ->
            for (n in 0 until arr.length()) {
                val o = arr.getJSONObject(n)
                val i = o.getInt("index")
                if (i in files.indices) resume[i] = o.getLong("offset") to o.getString("prefix")
            }
        }

        var done = 0L
        val buf = ByteArray(config.chunkSize)
        listener.onStatus(TransferStatus.TRANSFERRING)
        for (src in sources) {
            val e = src.entry
            var offset = 0L
            var digest = IntegrityVerifier.newDigest()
            var ins = src.open()
            try {
                val claim = resume[e.index]
                if (claim != null && claim.first in 1..e.size) {
                    val ok = hashPrefix(ins, digest, claim.first) &&
                        Hex.encode((digest.clone() as java.security.MessageDigest).digest()) == claim.second
                    if (ok) offset = claim.first
                    else { ins.close(); ins = src.open(); digest = IntegrityVerifier.newDigest() }
                }
                ch.sendControl(JSONObject().ctrl("file_start").put("index", e.index).put("offset", offset))
                var sent = offset
                done += offset
                listener.onProgress(done, total, e.index, e.path)
                while (sent < e.size) {
                    if (isCancelled()) { runCatching { ch.sendControl(JSONObject().ctrl("cancel")) }; throw TransferCancelledException() }
                    val want = minOf(buf.size.toLong(), e.size - sent).toInt()
                    val n = ins.read(buf, 0, want)
                    if (n < 0) throw ProtocolException("Source file shrank while sending: ${e.path}")
                    digest.update(buf, 0, n)
                    ch.send(Proto.FRAME_DATA, buf, 0, n)
                    sent += n; done += n
                    listener.onProgress(done, total, e.index, e.path)
                }
                ch.sendControl(JSONObject().ctrl("file_end").put("index", e.index).put("sha256", Hex.encode(digest.digest())))
                listener.onStatus(TransferStatus.VERIFYING)
                val ack = ch.readControl()
                if (ack.optString("type") != "file_ok" || ack.optInt("index") != e.index)
                    throw ProtocolException("Receiver rejected file: ${ack.optString("reason", "verification failed")}")
                listener.onStatus(TransferStatus.TRANSFERRING)
            } finally { runCatching { ins.close() } }
        }
        ch.sendControl(JSONObject().ctrl("done"))
        val fin = ch.readControl()
        if (fin.optString("type") != "complete") throw ProtocolException("Missing completion")
        listener.onStatus(TransferStatus.COMPLETED)
    }

    private fun hashPrefix(ins: InputStream, d: java.security.MessageDigest, length: Long): Boolean {
        val b = ByteArray(64 * 1024); var left = length
        while (left > 0) {
            val n = ins.read(b, 0, minOf(b.size.toLong(), left).toInt())
            if (n < 0) return false
            d.update(b, 0, n); left -= n
        }
        return true
    }
}

class TransferReceiver(
    private val identity: IdentityProvider,
    private val partialRoot: File,
    private val finalDir: File,
    private val config: TransferConfig = TransferConfig(),
) {
    /**
     * Blocking. [allowPeer] runs right after authentication (visibility policy); [decide] must block until the
     * user (or an auto-accept rule) decides. Returns the saved files on success.
     */
    fun handle(
        socket: Socket,
        listener: TransferListener,
        allowPeer: (peerFingerprint: String) -> Boolean,
        decide: (IncomingRequest) -> Boolean,
    ): List<File> {
        socket.tcpNoDelay = true
        socket.soTimeout = config.handshakeTimeoutMs
        val ch = SecureHandshake.perform(socket, false, identity)
        val fp = ch.peerFingerprint
        listener.onHandshake(fp, ch.sas)
        socket.soTimeout = config.approvalTimeoutMs
        val req = ch.readControl()
        if (!allowPeer(fp)) { // policy check happens after authentication, before any manifest is processed
            ch.sendControl(JSONObject().ctrl("response").put("accept", false).put("reason", "Receiver only accepts trusted devices"))
            throw TransferRejectedException("Blocked by visibility setting")
        }
        if (req.optString("type") != "request" || req.optInt("v") != Proto.VERSION) throw ProtocolException("Bad request")
        val tid = req.optString("id")
        if (!Regex("[A-Za-z0-9-]{8,64}").matches(tid)) throw ProtocolException("Bad transfer id")
        val senderName = req.optString("name").take(Proto.MAX_NAME).filter { !it.isISOControl() }.ifBlank { "Unknown device" }
        val files = Manifest.parse(req.optJSONArray("files") ?: throw ProtocolException("Missing files"))
        val total = Manifest.totalSize(files)
        val incoming = IncomingRequest(tid, senderName, DeviceType.parse(req.optString("device")), fp, ch.sas, files)
        listener.onManifest(senderName, files)
        listener.onStatus(TransferStatus.AWAITING_APPROVAL)

        if (!decide(incoming)) {
            ch.sendControl(JSONObject().ctrl("response").put("accept", false).put("reason", "Declined"))
            throw TransferRejectedException("Declined")
        }
        partialRoot.mkdirs(); finalDir.mkdirs()
        val dir = File(partialRoot, Hex.encode(Hex.sha256((fp + tid).toByteArray())).take(32))
        val manifestFile = File(dir, "manifest.json")
        val manifestJson = Manifest.toJson(files).toString()
        val resumable = manifestFile.exists() && manifestFile.readText() == manifestJson
        if (!resumable) { dir.deleteRecursively(); dir.mkdirs(); manifestFile.writeText(manifestJson) }

        val resumeArr = JSONArray()
        var alreadyHave = 0L
        for (f in files) {
            val part = File(dir, "${f.index}.part")
            if (part.exists() && resumable) {
                val len = minOf(part.length(), f.size)
                if (len > 0) {
                    resumeArr.put(JSONObject().put("index", f.index).put("offset", len).put("prefix", IntegrityVerifier.prefixHex(part, len)))
                    alreadyHave += len
                }
            } else part.delete()
        }
        if (partialRoot.usableSpace < total - alreadyHave + 1_048_576) {
            ch.sendControl(JSONObject().ctrl("response").put("accept", false).put("reason", "Not enough free storage"))
            throw IOException("Not enough free storage")
        }
        ch.sendControl(JSONObject().ctrl("response").put("accept", true).put("resume", resumeArr))
        socket.soTimeout = config.ioTimeoutMs
        listener.onStatus(TransferStatus.TRANSFERRING)

        val saved = ArrayList<File>()
        var base = 0L
        for (f in files) {
            val st = ch.readControl()
            if (st.optString("type") != "file_start" || st.optInt("index", -1) != f.index) throw ProtocolException("Unexpected message, wanted file_start ${f.index}")
            val offset = st.optLong("offset", -1)
            val part = File(dir, "${f.index}.part")
            if (offset < 0 || offset > f.size || (offset > 0 && offset > part.length())) throw ProtocolException("Invalid resume offset")
            val digest = IntegrityVerifier.newDigest()
            RandomAccessFile(part, "rw").use { raf ->
                raf.setLength(offset)
                if (offset > 0) {
                    part.inputStream().use { ins ->
                        val b = ByteArray(64 * 1024); var left = offset
                        while (left > 0) {
                            val n = ins.read(b, 0, minOf(b.size.toLong(), left).toInt()); if (n < 0) throw ProtocolException("Short partial")
                            digest.update(b, 0, n); left -= n
                        }
                    }
                }
                raf.seek(offset)
                var written = offset
                listener.onProgress(base + written, total, f.index, f.path)
                while (true) {
                    val fr = ch.receive()
                    if (fr.type == Proto.FRAME_DATA) {
                        if (written + fr.payload.size > f.size) throw ProtocolException("Peer sent more data than declared")
                        raf.write(fr.payload); digest.update(fr.payload); written += fr.payload.size
                        listener.onProgress(base + written, total, f.index, f.path)
                    } else {
                        val c = JSONObject(String(fr.payload, Charsets.UTF_8))
                        if (c.optString("type") == "cancel") throw TransferCancelledException()
                        if (c.optString("type") != "file_end" || c.optInt("index") != f.index) throw ProtocolException("Unexpected control message")
                        listener.onStatus(TransferStatus.VERIFYING)
                        val ok = written == f.size && Hex.encode(digest.digest()) == c.optString("sha256")
                        if (!ok) {
                            raf.close(); part.delete()
                            ch.sendControl(JSONObject().ctrl("file_error").put("index", f.index).put("reason", "checksum mismatch"))
                            throw ProtocolException("Checksum mismatch for ${f.path}")
                        }
                        break
                    }
                }
            }
            val target = FileNames.uniqueFile(finalDir, f.path)
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            saved.add(target)
            base += f.size
            ch.sendControl(JSONObject().ctrl("file_ok").put("index", f.index))
            listener.onStatus(TransferStatus.TRANSFERRING)
        }
        val done = ch.readControl()
        if (done.optString("type") != "done") throw ProtocolException("Missing done")
        ch.sendControl(JSONObject().ctrl("complete"))
        dir.deleteRecursively()
        listener.onStatus(TransferStatus.COMPLETED)
        return saved
    }
}
