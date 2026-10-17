package com.neardrop.app

import com.neardrop.app.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtocolTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun sanitizerAcceptsNormalPaths() {
        assertEquals("a/b/c.txt", PathSanitizer.sanitize("a/b/c.txt"))
        assertEquals("Holiday photo (1).jpg", PathSanitizer.sanitize("Holiday photo (1).jpg"))
    }

    @Test fun sanitizerRejectsTraversalAndAbsolute() {
        for (bad in listOf("../x", "a/../../x", "/etc/passwd", "a//b", "a\\b", "..", ".", "a/./b", "c:evil", "x\u0000y", "", "trail.", "a/b/")) {
            try { PathSanitizer.sanitize(bad); fail("accepted: $bad") } catch (e: ProtocolException) { /* expected */ }
        }
    }

    @Test fun manifestRoundTrip() {
        val files = listOf(FileEntry(0, "a.txt", 5), FileEntry(1, "dir/b.bin", 0))
        assertEquals(files, Manifest.parse(Manifest.toJson(files)))
        assertEquals(5L, Manifest.totalSize(files))
    }

    @Test fun manifestRejectsInvalid() {
        fun arr(vararg o: JSONObject) = JSONArray().also { a -> o.forEach { a.put(it) } }
        fun e(i: Int, p: String, s: Long) = JSONObject().put("i", i).put("p", p).put("s", s)
        val cases = listOf(
            arr(e(0, "a", -1)), arr(e(1, "a", 1)), arr(e(0, "a", 1), e(1, "A", 1)), arr(e(0, "../a", 1)),
        )
        for (c in cases) try { Manifest.parse(c); fail("accepted $c") } catch (ex: ProtocolException) { }
    }

    @Test fun overflowingTotalIsRejected() {
        try { Manifest.totalSize(listOf(FileEntry(0, "a", Long.MAX_VALUE), FileEntry(1, "b", 1))); fail() } catch (e: ArithmeticException) { }
    }

    @Test fun collisionsGetNumberedNames() {
        val dir = tmp.newFolder()
        val first = FileNames.uniqueFile(dir, "photo.jpg").also { it.writeText("1") }
        val second = FileNames.uniqueFile(dir, "photo.jpg").also { it.writeText("2") }
        val nested = FileNames.uniqueFile(dir, "sub/dir/photo.jpg")
        assertEquals("photo.jpg", first.name); assertEquals("photo (1).jpg", second.name)
        assertTrue(nested.parentFile!!.isDirectory)
    }

    @Test fun checksumsAndPrefix() {
        val f = tmp.newFile().also { it.writeBytes(ByteArray(300_000) { i -> (i % 251).toByte() }) }
        val full = IntegrityVerifier.sha256Hex(f)
        assertEquals(64, full.length)
        assertEquals(IntegrityVerifier.prefixHex(f, f.length()), full)
        assertNotEquals(IntegrityVerifier.prefixHex(f, 1000), full)
    }

    @Test fun terminalStates() {
        assertTrue(TransferStatus.COMPLETED.terminal); assertTrue(TransferStatus.INTERRUPTED.terminal)
        assertFalse(TransferStatus.TRANSFERRING.terminal); assertFalse(TransferStatus.AWAITING_APPROVAL.terminal)
    }
}
