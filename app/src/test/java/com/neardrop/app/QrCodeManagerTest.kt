package com.neardrop.app

import com.neardrop.app.ui.qr.QrCodeManager
import org.junit.Assert.*
import org.junit.Test

class QrCodeManagerTest {

    @Test
    fun testEncodeAndParseHotspotQr() {
        val payload = QrCodeManager.encodeReceiverInfo(
            ip = "192.168.43.1",
            port = 45454,
            name = "Test Receiver",
            fingerprint = "abcdef123456",
            ssid = "NearDrop-Direct-9988",
            passphrase = "testpassphrase",
            isHotspot = true
        )

        val info = QrCodeManager.parseReceiverInfo(payload)
        assertNotNull(info)
        assertEquals("192.168.43.1", info!!.ip)
        assertEquals(45454, info.port)
        assertEquals("Test Receiver", info.name)
        assertEquals("NearDrop-Direct-9988", info.ssid)
        assertEquals("testpassphrase", info.passphrase)
        assertTrue(info.isHotspot)
    }

    @Test
    fun testStandardWifiQrCodeParsing() {
        val wifiQr = "WIFI:S:NearDrop-AP-1234;T:WPA;P:directpass123;;"
        val info = QrCodeManager.parseReceiverInfo(wifiQr)
        assertNotNull(info)
        assertEquals("NearDrop-AP-1234", info!!.ssid)
        assertEquals("directpass123", info.passphrase)
        assertEquals("192.168.43.1", info.ip)
        assertTrue(info.isHotspot)
    }

    @Test
    fun testPlainIpParsing() {
        val plainIp = "192.168.1.55:45454"
        val info = QrCodeManager.parseReceiverInfo(plainIp)
        assertNotNull(info)
        assertEquals("192.168.1.55", info!!.ip)
        assertEquals(45454, info.port)
        assertFalse(info.isHotspot)
    }
}
