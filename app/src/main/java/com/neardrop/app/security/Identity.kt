package com.neardrop.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.neardrop.app.core.Hex
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Long-term device identity: an ECDSA P-256 key pair. Only signing is exposed; the private key never leaves its store. */
interface IdentityProvider {
    val publicKeyEncoded: ByteArray
    fun sign(data: ByteArray): ByteArray
    val fingerprint: String get() = fingerprintOf(publicKeyEncoded)
}

fun fingerprintOf(publicKeyEncoded: ByteArray): String = Hex.encode(Hex.sha256(publicKeyEncoded))

/** Production identity backed by the Android Keystore (hardware-backed where available). */
class KeystoreIdentity : IdentityProvider {
    private val entry: KeyStore.PrivateKeyEntry

    init {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            gen.initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            gen.generateKeyPair()
        }
        entry = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
    }

    override val publicKeyEncoded: ByteArray get() = entry.certificate.publicKey.encoded

    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(entry.privateKey); update(data); sign() }

    companion object { private const val ALIAS = "neardrop_identity_v1" }
}

/** Software identity for unit tests / JVM peers. */
class SoftwareIdentity : IdentityProvider {
    private val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val priv: PrivateKey = kp.private
    override val publicKeyEncoded: ByteArray get() = kp.public.encoded
    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(priv); update(data); sign() }
}
