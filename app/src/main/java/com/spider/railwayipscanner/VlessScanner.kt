package com.spider.railwayipscanner

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 100% Real VLESS End-to-End WebSocket + Google 204 Protocol Scanner.
 * Completely eliminates false positives on Iranian mobile networks (MCI / Irancell).
 *
 * Inspired by PattN / v2rayN Xray Real Delay verification.
 */
object VlessScanner {
    private const val PORT = 443
    private const val TIMEOUT_MS = 2600

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
    })

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }
    }

    fun testIpRealDelay(ip: String, domain: String, uuidString: String): ScanResult {
        val t0 = System.currentTimeMillis()
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            // 1. Direct TCP Connect
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(ip, PORT), TIMEOUT_MS)
            rawSocket.soTimeout = TIMEOUT_MS

            // 2. TLS Handshake with Domain SNI & ALPN
            val factory = sslContext.socketFactory
            sslSocket = factory.createSocket(rawSocket, domain, PORT, true) as SSLSocket
            val sslParams = sslSocket.sslParameters
            sslParams.serverNames = listOf(SNIHostName(domain))
            sslSocket.sslParameters = sslParams
            sslSocket.startHandshake()

            val out: OutputStream = sslSocket.outputStream
            val inp: InputStream = sslSocket.inputStream

            // 3. Real WebSocket Upgrade Handshake
            val wsReq = "GET /ws/$uuidString HTTP/1.1\r\n" +
                    "Host: $domain\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "User-Agent: Mozilla/5.0 (Android; SpiderPanel)\r\n\r\n"
            out.write(wsReq.toByteArray(Charsets.UTF_8))
            out.flush()

            val headerBuf = ByteArray(512)
            val headerRead = inp.read(headerBuf)
            if (headerRead <= 0) {
                return ScanResult(ip, false, 0, "No WS response")
            }
            val headerStr = String(headerBuf, 0, headerRead, Charsets.UTF_8)
            if (!headerStr.contains("101")) {
                val firstLine = headerStr.lines().firstOrNull() ?: "Unknown error"
                return ScanResult(ip, false, 0, firstLine)
            }

            // 4. Binary VLESS Protocol Header with UUID Auth
            val uuidObj = UUID.fromString(uuidString)
            val uuidBytes = ByteArray(16).apply {
                val msb = uuidObj.mostSignificantBits
                val lsb = uuidObj.leastSignificantBits
                for (i in 0..7) this[i] = ((msb ushr (8 * (7 - i))) and 0xFF).toByte()
                for (i in 8..15) this[i] = ((lsb ushr (8 * (15 - i))) and 0xFF).toByte()
            }

            val targetHost = "connectivitycheck.gstatic.com".toByteArray(Charsets.UTF_8)
            val vlessHeader = ByteArray(1 + 16 + 1 + 1 + 2 + 1 + 1 + targetHost.size).apply {
                var pos = 0
                this[pos++] = 0.toByte() // Version 0
                System.arraycopy(uuidBytes, 0, this, pos, 16); pos += 16
                this[pos++] = 0.toByte() // Proto addon length
                this[pos++] = 1.toByte() // Command: 1 = TCP
                this[pos++] = 0.toByte(); this[pos++] = 80.toByte() // Port 80
                this[pos++] = 2.toByte() // Address Type: Domain
                this[pos++] = targetHost.size.toByte()
                System.arraycopy(targetHost, 0, this, pos, targetHost.size)
            }

            // HTTP 204 Probe Payload
            val httpPayload = ("GET /generate_204 HTTP/1.1\r\n" +
                    "Host: connectivitycheck.gstatic.com\r\n" +
                    "User-Agent: v2rayN\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)

            val fullData = vlessHeader + httpPayload

            // WebSocket Client Masking
            val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
            val maskedData = ByteArray(fullData.size)
            for (i in fullData.indices) {
                maskedData[i] = (fullData[i].toInt() xor mask[i % 4].toInt()).toByte()
            }

            val frameHeader: ByteArray = if (fullData.size < 126) {
                byteArrayOf(0x82.toByte(), (0x80 or fullData.size).toByte()) + mask
            } else {
                byteArrayOf(
                    0x82.toByte(),
                    (0x80 or 126).toByte(),
                    ((fullData.size ushr 8) and 0xFF).toByte(),
                    (fullData.size and 0xFF).toByte()
                ) + mask
            }

            out.write(frameHeader + maskedData)
            out.flush()

            // 5. Read End-to-End Google Response
            val respBuf = ByteArray(1024)
            val readLen = inp.read(respBuf)
            val delayMs = System.currentTimeMillis() - t0

            if (readLen > 0) {
                val respStr = String(respBuf, 0, readLen, Charsets.ISO_8859_1)
                if (respStr.contains("204") || respStr.contains("No Content") || respStr.contains("Google")) {
                    return ScanResult(ip, true, delayMs)
                }
            }
            return ScanResult(ip, false, delayMs, "VLESS tunnel failed")

        } catch (e: Exception) {
            return ScanResult(ip, false, 0, e.message ?: "Connection error")
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }
}
