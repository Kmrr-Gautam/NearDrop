package com.neardrop.app.security

import com.neardrop.app.core.Hex
import com.neardrop.app.core.Proto
import com.neardrop.app.core.ProtocolException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Frame(val type: Byte, val payload: ByteArray)

/**
 * Encrypted, authenticated, replay-protected frame channel (AES-256-GCM, per-direction keys,
 * strictly increasing counter used as the GCM nonce). Frame = int32 length || ciphertext(type || payload).
 */
class SecureChannel(
    private val din: DataInputStream,
    private val dout: DataOutputStream,
    sendKey: ByteArray,
    recvKey: ByteArray,
    private val sendDir: Int,
    private val recvDir: Int,
    val peerIdentity: ByteArray,
    val sas: String,
) {
    private val sk = SecretKeySpec(sendKey, "AES")
    private val rk = SecretKeySpec(recvKey, "AES")
    private var sendCounter = 0L
    private var recvCounter = 0L
    val peerFingerprint: String get() = fingerprintOf(peerIdentity)

    private fun nonce(dir: Int, c: Long): ByteArray = ByteBuffer.allocate(12).putInt(dir).putLong(c).array()

    @Synchronized
    fun send(type: Byte, data: ByteArray, off: Int = 0, len: Int = data.size) {
        if (len > Proto.MAX_FRAME) throw ProtocolException("Frame too large")
        val pt = ByteArray(len + 1)
        pt[0] = type
        System.arraycopy(data, off, pt, 1, len)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, sk, GCMParameterSpec(128, nonce(sendDir, sendCounter++)))
        val ct = c.doFinal(pt)
        dout.writeInt(ct.size)
        dout.write(ct)
        dout.flush()
    }

    fun receive(): Frame {
        val n = din.readInt()
        if (n < 17 || n > Proto.MAX_FRAME + 17) throw ProtocolException("Invalid frame size")
        val ct = ByteArray(n)
        din.readFully(ct)
        val pt = try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, rk, GCMParameterSpec(128, nonce(recvDir, recvCounter)))
            c.doFinal(ct)
        } catch (e: GeneralSecurityException) {
            throw ProtocolException("Frame authentication failed (tampered, replayed or reordered)", e)
        }
        recvCounter++
        return Frame(pt[0], pt.copyOfRange(1, pt.size))
    }
}

/**
 * Mutually authenticated ECDH (P-256) handshake with HKDF-SHA256 and AES-GCM. Built only from JCA primitives.
 *
 * 1. Both sides exchange Hello = magic || version || identityPub || ephemeralPub || nonce.
 * 2. Both sides sign (label || role || SHA-256(transcript)) with their long-term identity key and verify the peer's signature.
 * 3. Session keys = HKDF(ECDH(ephemerals), salt = transcript hash). Forward secrecy comes from the ephemeral keys.
 * 4. Both users can compare the 6-digit SAS derived from the transcript to detect a man-in-the-middle.
 */
object SecureHandshake {
    private const val LABEL = "NearDrop-v1-auth"

    fun perform(socket: Socket, initiator: Boolean, identity: IdentityProvider): SecureChannel {
        val din = DataInputStream(BufferedInputStream(socket.getInputStream(), 64 * 1024))
        val dout = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 64 * 1024))

        val eph = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val myHello = encodeHello(identity.publicKeyEncoded, eph.public.encoded, nonce)

        val peerHello: ByteArray
        if (initiator) { writeBlob(dout, myHello); peerHello = readBlob(din, 1024) }
        else { peerHello = readBlob(din, 1024); writeBlob(dout, myHello) }
        val (peerIdentity, peerEph) = parseHello(peerHello)

        val transcript = if (initiator) myHello + peerHello else peerHello + myHello
        val th = Hex.sha256(transcript)
        val myRole = if (initiator) 0 else 1
        val mySig = identity.sign(signedData(myRole, th))
        val peerSig: ByteArray
        if (initiator) { writeBlob(dout, mySig); peerSig = readBlob(din, 256) }
        else { peerSig = readBlob(din, 256); }
        verify(peerIdentity, signedData(1 - myRole, th), peerSig)
        if (!initiator) writeBlob(dout, mySig)

        val kf = KeyFactory.getInstance("EC")
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(eph.private)
            doPhase(kf.generatePublic(X509EncodedKeySpec(peerEph)), true)
            generateSecret()
        }
        val prk = hmac(th, secret)
        val c2s = hmac(prk, "neardrop-v1 c2s".toByteArray() + byteArrayOf(1))
        val s2c = hmac(prk, "neardrop-v1 s2c".toByteArray() + byteArrayOf(1))
        val sasBytes = Hex.sha256("sas".toByteArray() + th)
        val sasInt = ((sasBytes[0].toInt() and 0x7f) shl 24) or ((sasBytes[1].toInt() and 0xff) shl 16) or
            ((sasBytes[2].toInt() and 0xff) shl 8) or (sasBytes[3].toInt() and 0xff)
        val sas = "%06d".format(sasInt % 1_000_000)

        return if (initiator)
            SecureChannel(din, dout, c2s, s2c, 0, 1, peerIdentity, sas)
        else
            SecureChannel(din, dout, s2c, c2s, 1, 0, peerIdentity, sas)
    }

    private fun signedData(role: Int, th: ByteArray) = LABEL.toByteArray() + byteArrayOf(role.toByte()) + th

    private fun verify(pub: ByteArray, data: ByteArray, sig: ByteArray) {
        val ok = try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pub)))
                update(data); verify(sig)
            }
        } catch (e: GeneralSecurityException) { false }
        if (!ok) throw ProtocolException("Peer authentication failed")
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    private fun encodeHello(identity: ByteArray, eph: ByteArray, nonce: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(); val o = DataOutputStream(bos)
        o.writeInt(Proto.MAGIC); o.writeByte(Proto.VERSION)
        for (b in listOf(identity, eph, nonce)) { o.writeShort(b.size); o.write(b) }
        return bos.toByteArray()
    }

    private fun parseHello(h: ByteArray): Pair<ByteArray, ByteArray> {
        val i = DataInputStream(h.inputStream())
        try {
            if (i.readInt() != Proto.MAGIC) throw ProtocolException("Not a NearDrop peer")
            val v = i.readUnsignedByte()
            if (v != Proto.VERSION) throw ProtocolException("Unsupported protocol version $v")
            fun blob(max: Int): ByteArray { val n = i.readUnsignedShort(); if (n > max) throw ProtocolException("Bad hello"); return ByteArray(n).also { i.readFully(it) } }
            val id = blob(256); val eph = blob(256); val nonce = blob(32)
            if (nonce.size != 16) throw ProtocolException("Bad nonce")
            return id to eph
        } catch (e: java.io.EOFException) { throw ProtocolException("Truncated hello", e) }
    }

    private fun writeBlob(o: DataOutputStream, b: ByteArray) { o.writeInt(b.size); o.write(b); o.flush() }
    private fun readBlob(i: DataInputStream, max: Int): ByteArray {
        val n = i.readInt()
        if (n < 0 || n > max) throw ProtocolException("Handshake message too large")
        return ByteArray(n).also { i.readFully(it) }
    }
}
