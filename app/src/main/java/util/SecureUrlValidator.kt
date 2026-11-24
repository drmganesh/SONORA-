package util

import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.nio.charset.Charset
import java.util.Locale

/**
 * Centralized URL validation, SSRF host filtering, bounded HTTP body reading,
 * and diagnostic log redaction for SONORA.
 */
object SecureUrlValidator {

    const val DEFAULT_MAX_TEXT_BYTES: Long = 2L * 1024L * 1024L // 2 MB
    const val MAX_NEWPIPE_RESPONSE_BYTES: Long = 8L * 1024L * 1024L // 8 MB
    const val MAX_IMAGE_BYTES: Long = 2L * 1024L * 1024L // 2 MB

    private val CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
    private val IPV4_LITERAL_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val BEARER_TOKEN_REGEX = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}")
    private val SENSITIVE_QUERY_PARAM_REGEX = Regex(
        "(?i)([?&](?:access_token|token|auth|key|api_key|apikey|cookie|sig|signature|password|secret)=)[^&\\s]+"
    )
    private val SENSITIVE_HEADER_REGEX = Regex(
        "(?i)((?:authorization|cookie|set-cookie|x-api-key)\\s*[:=]\\s*)[^\\r\\n]+"
    )

    /**
     * Validates that [rawUrl] is a well-formed public web URL.
     *
     * Rejects:
     * - Blank strings, control characters / CRLF injection, or unparseable URLs
     * - Embedded userinfo credentials (`user:pass@host`)
     * - Non-HTTPS schemes (unless [allowHttp] is explicitly true)
     * - Localhost, loopback, link-local (including cloud metadata 169.254.169.254),
     *   private RFC1918 / RFC4193 ranges, and unspecified addresses (unless [allowPrivateHosts] is true)
     */
    fun isSafeHttpsUrl(
        rawUrl: String?,
        allowHttp: Boolean = false,
        allowPrivateHosts: Boolean = false
    ): Boolean {
        val parsed = parseSafeHttpUrl(rawUrl, allowHttp = allowHttp, allowPrivateHosts = allowPrivateHosts)
        return parsed != null
    }

    fun parseSafeHttpUrl(
        rawUrl: String?,
        allowHttp: Boolean = false,
        allowPrivateHosts: Boolean = false
    ): HttpUrl? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (trimmed.length > 4096 || CONTROL_CHAR_REGEX.containsMatchIn(trimmed)) return null

        val httpUrl = trimmed.toHttpUrlOrNull() ?: return null
        val scheme = httpUrl.scheme.lowercase(Locale.ROOT)
        if (scheme != "https" && !(allowHttp && scheme == "http")) return null

        // Block embedded credentials in URL (phishing / auth header confusion)
        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) return null

        val host = httpUrl.host.lowercase(Locale.ROOT).trimEnd('.')
        if (host.isBlank()) return null

        if (!allowPrivateHosts && isPrivateOrLocalHost(host)) return null
        return httpUrl
    }

    /**
     * Sanitizes a URL before launching an external browser / official app via `ACTION_VIEW`.
     * Strictly requires HTTPS and a public host.
     */
    fun sanitizeExternalBrowseUrl(rawUrl: String?): Uri? {
        val safeUrl = parseSafeHttpUrl(rawUrl, allowHttp = false, allowPrivateHosts = false) ?: return null
        return runCatching { Uri.parse(safeUrl.toString()) }.getOrNull()
    }

    /**
     * Returns true if [host] refers to localhost, loopback, private LAN, link-local,
     * cloud metadata, or internal-only hostnames/IP literals.
     */
    fun isPrivateOrLocalHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT).trim().removePrefix("[").removeSuffix("]").trimEnd('.')
        if (normalized.isBlank()) return true

        if (normalized == "localhost" ||
            normalized.endsWith(".localhost") ||
            normalized.endsWith(".local") ||
            normalized.endsWith(".internal") ||
            normalized.endsWith(".home.arpa")
        ) {
            return true
        }

        // Check IPv4 dotted-quad literal directly without triggering DNS
        val ipv4Match = IPV4_LITERAL_REGEX.matchEntire(normalized)
        if (ipv4Match != null) {
            val octets = (1..4).mapNotNull { ipv4Match.groupValues[it].toIntOrNull() }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return true
            return isPrivateIpv4(octets[0], octets[1], octets[2], octets[3])
        }

        // Reject decimal / hex / octal single-integer IPv4 obfuscation (e.g., 2130706433 or 0x7f000001)
        if (normalized.all { it.isDigit() } || normalized.startsWith("0x")) {
            return true
        }

        // Check IPv6 literals directly without DNS
        if (normalized.contains(':')) {
            val addr = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return true
            return isPrivateInetAddress(addr)
        }

        return false
    }

    private fun isPrivateIpv4(a: Int, b: Int, c: Int, d: Int): Boolean {
        if (a == 0) return true // 0.0.0.0/8 unspecified
        if (a == 10) return true // 10.0.0.0/8 private
        if (a == 127) return true // 127.0.0.0/8 loopback
        if (a == 169 && b == 254) return true // 169.254.0.0/16 link-local & cloud metadata
        if (a == 172 && b in 16..31) return true // 172.16.0.0/12 private
        if (a == 192 && b == 168) return true // 192.168.0.0/16 private
        if (a == 100 && b in 64..127) return true // 100.64.0.0/10 CGNAT
        if (a >= 224) return true // multicast / reserved / broadcast
        if (a == 192 && b == 0 && c == 0) return true
        if (a == 192 && b == 0 && c == 2) return true
        if (a == 198 && (b == 18 || b == 19)) return true
        return false
    }

    private fun isPrivateInetAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress
        ) {
            return true
        }
        val bytes = addr.address
        if (bytes.size == 4) {
            return isPrivateIpv4(
                bytes[0].toInt() and 0xFF,
                bytes[1].toInt() and 0xFF,
                bytes[2].toInt() and 0xFF,
                bytes[3].toInt() and 0xFF
            )
        }
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xFF
            // fc00::/7 Unique Local Address
            if ((first and 0xFE) == 0xFC) return true
            // ::ffff:0:0/96 IPv4-mapped IPv6 address
            val isIpv4Mapped = (0 until 10).all { bytes[it].toInt() == 0 } &&
                (bytes[10].toInt() and 0xFF) == 0xFF &&
                (bytes[11].toInt() and 0xFF) == 0xFF
            if (isIpv4Mapped) {
                return isPrivateIpv4(
                    bytes[12].toInt() and 0xFF,
                    bytes[13].toInt() and 0xFF,
                    bytes[14].toInt() and 0xFF,
                    bytes[15].toInt() and 0xFF
                )
            }
        }
        return false
    }

    /**
     * Reads at most [maxBytes] from [input] into a [ByteArray].
     * Throws [IOException] if the stream exceeds [maxBytes], preventing memory exhaustion.
     */
    @Throws(IOException::class)
    fun readBoundedBytes(input: InputStream, maxBytes: Long): ByteArray {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        val limit = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val out = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buf)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IOException("Response body exceeded safe limit of $maxBytes bytes")
            }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Reads an OkHttp [ResponseBody] as text up to [maxBytes] bytes.
     * Enforces both declared Content-Length and actual streamed byte count.
     */
    @Throws(IOException::class)
    fun readBoundedString(body: ResponseBody?, maxBytes: Long = DEFAULT_MAX_TEXT_BYTES): String {
        if (body == null) return ""
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            throw IOException("Declared response length ($declaredLength) exceeded safe limit ($maxBytes)")
        }
        val charset: Charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val bytes = body.byteStream().use { stream ->
            readBoundedBytes(stream, maxBytes)
        }
        return String(bytes, charset)
    }

    /**
     * Redacts Bearer tokens, cookies, API keys, and sensitive query parameters before logging.
     */
    fun redactSensitiveForLogs(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var sanitized = BEARER_TOKEN_REGEX.replace(raw, "$1[REDACTED]")
        sanitized = SENSITIVE_QUERY_PARAM_REGEX.replace(sanitized, "$1[REDACTED]")
        sanitized = SENSITIVE_HEADER_REGEX.replace(sanitized, "$1[REDACTED]")
        return sanitized.take(512)
    }
}
