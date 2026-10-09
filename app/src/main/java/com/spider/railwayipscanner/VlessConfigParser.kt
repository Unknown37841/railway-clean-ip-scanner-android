package com.spider.railwayipscanner

import android.net.Uri
import java.net.URLDecoder

data class VlessConfig(
    val uuid: String,
    val address: String,
    val port: Int = 443,
    val domain: String,
    val path: String = "/",
    val security: String = "tls",
    val type: String = "ws",
    val name: String = "Clean Railway"
)

object VlessConfigParser {

    fun parse(vlessUri: String): VlessConfig? {
        try {
            val trimmed = vlessUri.trim()
            if (!trimmed.startsWith("vless://")) return null

            val withoutScheme = trimmed.substring("vless://".length)
            val atIndex = withoutScheme.indexOf('@')
            if (atIndex == -1) return null

            val uuid = withoutScheme.substring(0, atIndex)
            val rest = withoutScheme.substring(atIndex + 1)

            val questionIndex = rest.indexOf('?')
            val hashIndex = rest.indexOf('#')

            val hostPort = when {
                questionIndex != -1 -> rest.substring(0, questionIndex)
                hashIndex != -1 -> rest.substring(0, hashIndex)
                else -> rest
            }

            val hostParts = hostPort.split(":")
            val address = hostParts[0]
            val port = if (hostParts.size > 1) hostParts[1].toIntOrNull() ?: 443 else 443

            var path = "/"
            var sni = address
            var security = "tls"
            var type = "ws"
            var name = "Clean Railway"

            if (hashIndex != -1) {
                name = URLDecoder.decode(rest.substring(hashIndex + 1), "UTF-8")
            }

            if (questionIndex != -1) {
                val queryEnd = if (hashIndex != -1) hashIndex else rest.length
                val queryStr = rest.substring(questionIndex + 1, queryEnd)
                val params = queryStr.split("&")
                for (param in params) {
                    val kv = param.split("=")
                    if (kv.size == 2) {
                        val k = kv[0].lowercase()
                        val v = URLDecoder.decode(kv[1], "UTF-8")
                        when (k) {
                            "path" -> path = v
                            "sni" -> sni = v
                            "host" -> if (sni.isEmpty() || sni == address) sni = v
                            "security" -> security = v
                            "type" -> type = v
                        }
                    }
                }
            }

            if (sni.isEmpty()) sni = address

            return VlessConfig(
                uuid = uuid,
                address = address,
                port = port,
                domain = sni,
                path = path,
                security = security,
                type = type,
                name = name
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Preserves exact original config name without adding ping or rank suffixes.
     */
    fun buildConfigLink(baseConfig: VlessConfig, newIp: String): String {
        val encodedPath = Uri.encode(baseConfig.path)
        val serverName = Uri.encode(baseConfig.name)
        return "vless://${baseConfig.uuid}@$newIp:${baseConfig.port}?path=$encodedPath&security=${baseConfig.security}&encryption=none&host=${baseConfig.domain}&type=${baseConfig.type}&sni=${baseConfig.domain}#$serverName"
    }
}
