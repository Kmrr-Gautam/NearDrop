package com.neardrop.app

import com.neardrop.app.core.Proto
import com.neardrop.app.core.ProtocolException
import com.neardrop.app.security.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class SecureHandshakeTest {
    private fun pair(a: IdentityProvider, b: IdentityProvider): Pair<SecureChannel, SecureChannel> {
        val ss = ServerSocket(0); var server: SecureChannel? = null
        val t = thread { ss.accept().let { server = SecureHandshake.perform(it, false, b) } }
        val client = SecureHandshake.perform(Socket("127.0.0.1", ss.localPort), true, a)
        t.join(5_000); ss.close()
        return client to server!!
    }

    @Test fun bothSidesAgreeOnSasAndSeeEachOthersIdentity() {
        val a = SoftwareIdentity(); val b = SoftwareIdentity()
        val (c, s) = pair(a, b)
        assertEquals(c.sas, s.sas); assertEquals(6, c.sas.length)
        assertEquals(b.fingerprint, c.peerFingerprint); assertEquals(a.fingerprint, s.peerFingerprint)
    }

    @Test fun framesRoundTripBothDirections() {
        val (c, s) = pair(SoftwareIdentity(), SoftwareIdentity())
        c.send(Proto.FRAME_DATA, byteArrayOf(1, 2, 3)); val f = s.receive()
        assertEquals(Proto.FRAME_DATA, f.type); assertArrayEquals(byteArrayOf(1, 2, 3), f.payload)
        s.send(Proto.FRAME_CONTROL, "hi".toByteArray()); assertEquals("hi", String(c.receive().payload))
    }

    @Test fun garbageInsteadOfHelloIsRejected() {
        val ss = ServerSocket(0); var err: Throwable? = null
        val t = thread { try { SecureHandshake.perform(ss.accept(), false, SoftwareIdentity()) } catch (e: Throwable) { err = e } }
        Socket("127.0.0.1", ss.localPort).use { it.getOutputStream().apply { write(ByteArray(64) { 7 }); flush() }; t.join(5_000) }
        ss.close()
        assertTrue(err is java.io.IOException)
    }
}
