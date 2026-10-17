package com.neardrop.app

import com.neardrop.app.core.*
import com.neardrop.app.security.SoftwareIdentity
import com.neardrop.app.transfer.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Random
import kotlin.concurrent.thread

/** Real encrypted transfers over localhost sockets with the production sender/receiver classes. */
class TransferLoopbackTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cfg = TransferConfig(chunkSize = 64 * 1024, ioTimeoutMs = 5_000, handshakeTimeoutMs = 5_000, approvalTimeoutMs = 5_000)
    private val senderId = SoftwareIdentity()
    private val receiverId = SoftwareIdentity()

    private class Src(override val entry: FileEntry, val bytes: ByteArray, val failAt: Int = -1) : SendSource {
        override fun open(): InputStream = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (failAt in 0..pos) throw IOException("simulated link loss")
                val cap = if (failAt >= 0) minOf(len, failAt - pos).coerceAtLeast(1) else len
                return super.read(b, off, cap)
            }
        }
    }

    private fun src(i: Int, path: String, bytes: ByteArray, failAt: Int = -1) = Src(FileEntry(i, path, bytes.size.toLong()), bytes, failAt)
    private fun rnd(n: Int, seed: Long = 1) = ByteArray(n).also { Random(seed).nextBytes(it) }

    private class Outcome { @Volatile var files: List<File>? = null; @Volatile var error: Throwable? = null }

    private fun runPair(
        dest: File, partial: File, sources: List<SendSource>, id: String = "transfer-0001",
        allow: Boolean = true, accept: Boolean = true, expectedPrefix: String? = null,
        senderListener: TransferListener = object : TransferListener {},
    ): Pair<Outcome, Throwable?> {
        val ss = ServerSocket(0)
        val out = Outcome()
        val t = thread {
            try {
                ss.accept().use { s ->
                    out.files = TransferReceiver(receiverId, partial, dest, cfg).handle(s, object : TransferListener {}, { allow }, { accept })
                }
            } catch (e: Throwable) { out.error = e } finally { ss.close() }
        }
        var senderErr: Throwable? = null
        try {
            Socket("127.0.0.1", ss.localPort).use { TransferSender(senderId, LocalDeviceInfo("Test sender", DeviceType.PHONE), cfg).send(it, id, sources, expectedPrefix, senderListener) { false } }
        } catch (e: Throwable) { senderErr = e }
        t.join(10_000)
        return out to senderErr
    }

    @Test fun multiFileTransferWithFolderAndEmptyFileMatchesChecksums() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        val data = listOf(rnd(5_000_000, 1), rnd(1, 2), ByteArray(0), rnd(200_000, 3))
        val names = listOf("big.bin", "docs/one.txt", "docs/empty.txt", "photos/2026/p.jpg")
        val (out, err) = runPair(dest, partial, data.mapIndexed { i, b -> src(i, names[i], b) })
        assertNull(err); assertNull(out.error)
        names.forEachIndexed { i, n -> assertArrayEquals(n, data[i], File(dest, n).readBytes()) }
        assertTrue(partial.listFiles().isNullOrEmpty())
    }

    @Test fun rejectedRequestReachesSenderAndStoresNothing() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        val (out, err) = runPair(dest, partial, listOf(src(0, "a.txt", rnd(100))), accept = false)
        assertTrue(err is TransferRejectedException)
        assertTrue(out.error is TransferRejectedException)
        assertTrue(dest.listFiles().isNullOrEmpty())
    }

    @Test fun visibilityPolicyBlocksUnknownPeerBeforeAnyRequest() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        val (out, err) = runPair(dest, partial, listOf(src(0, "a.txt", rnd(100))), allow = false)
        assertTrue(err is TransferRejectedException)
        assertTrue(dest.listFiles().isNullOrEmpty())
    }

    @Test fun wrongExpectedFingerprintAbortsSender() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        val (_, err) = runPair(dest, partial, listOf(src(0, "a.txt", rnd(100))), expectedPrefix = "0000deadbeef")
        assertTrue(err is ProtocolException)
        assertTrue(dest.listFiles().isNullOrEmpty())
    }

    @Test fun maliciousPathIsRejectedByReceiver() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        val (out, _) = runPair(dest, partial, listOf(src(0, "../evil.txt", rnd(100))))
        assertTrue(out.error is ProtocolException)
        assertFalse(File(dest.parentFile, "evil.txt").exists())
    }

    @Test fun interruptedTransferResumesFromVerifiedPartialData() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        val data = rnd(2_000_000, 9)
        val (_, err1) = runPair(dest, partial, listOf(src(0, "movie.bin", data, failAt = 900_000)))
        assertTrue("first attempt must fail", err1 is IOException)
        assertTrue(dest.listFiles().isNullOrEmpty())

        var firstProgress = -1L
        val (out, err2) = runPair(dest, partial, listOf(src(0, "movie.bin", data)), senderListener = object : TransferListener {
            override fun onProgress(bytesDone: Long, totalBytes: Long, fileIndex: Int, fileName: String) { if (firstProgress < 0) firstProgress = bytesDone }
        })
        assertNull(err2); assertNull(out.error)
        assertTrue("should resume past 0, started at $firstProgress", firstProgress > 0)
        assertArrayEquals(data, File(dest, "movie.bin").readBytes())
    }

    @Test fun resumeWithDifferentSourceContentRestartsAndStillVerifies() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        val a = rnd(1_000_000, 1); val b = rnd(1_000_000, 2) // same size, different content
        runPair(dest, partial, listOf(src(0, "f.bin", a, failAt = 400_000)))
        val (out, err) = runPair(dest, partial, listOf(src(0, "f.bin", b)))
        assertNull(err); assertNull(out.error)
        assertArrayEquals(b, File(dest, "f.bin").readBytes()) // prefix hash mismatch => sender restarts from 0
    }

    @Test fun twoSendsOfSameNameDoNotOverwrite() {
        val dest = tmp.newFolder(); val partial = tmp.newFolder()
        runPair(dest, partial, listOf(src(0, "x.txt", rnd(10, 1))), id = "transfer-0001")
        runPair(dest, partial, listOf(src(0, "x.txt", rnd(10, 2))), id = "transfer-0002")
        assertTrue(File(dest, "x.txt").exists()); assertTrue(File(dest, "x (1).txt").exists())
    }
}
