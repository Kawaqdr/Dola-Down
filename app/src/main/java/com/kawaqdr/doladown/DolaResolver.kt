package com.kawaqdr.doladown

import android.net.Uri
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DolaMetadata(
    val prompt: String = "",
    val poster: String = "",
    val videoId: String = "",
    val keySeed: String = "",
    val messageId: String = ""
)

sealed class DolaResolveResult {
    data class Success(
        val url: String, val filename: String, val prompt: String,
        val poster: String, val width: Int, val height: Int, val bitrate: Long
    ) : DolaResolveResult()
    data class Failure(val message: String) : DolaResolveResult()
}

object DolaResolver {
    private const val SALT_HEX =
        "4dd4c2e6b83162090e52b3c7a6733ba4" +
        "1cb2462b829ab58a196b39db57177524" +
        "f49baf7f08e8d68d26a72e37c1a95a2f" +
        "1f05a51892aef2949732b62a38aadd58"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun resolveFallback(fallbackUrl: String, metadata: DolaMetadata, cookies: String, referer: String): DolaResolveResult {
        val normalized = fallbackUrl.replace("\\u0026", "&").replace("\\/", "/").replace("\\\"", "\"")
        val apiUrl = normalized.toHttpUrlOrNull()
            ?: return DolaResolveResult.Failure("Captured fallback_api is not a valid URL.")
        val requestUrl = apiUrl.newBuilder()
            .setQueryParameter("channel", "no")
            .setQueryParameter("codec_type", "8")
            .setQueryParameter("logo_type", "unwatermarked")
            .build()
        val builder = Request.Builder().url(requestUrl)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", referer)
            .header("Origin", "https://www.dola.com")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
        if (cookies.isNotBlank()) builder.header("Cookie", cookies)

        return try {
            http.newCall(builder.get().build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return DolaResolveResult.Failure("Dola API HTTP " + response.code + ": " + body.take(180))
                }
                val payload = try { JSONObject(body) } catch (_: Exception) {
                    val kind = if (body.trimStart().startsWith("<")) "HTML/login/error page" else "invalid JSON"
                    return DolaResolveResult.Failure("Fallback API returned " + kind + " instead of JSON.")
                }
                val videoData = payload.optJSONObject("video_info")?.optJSONObject("data")
                    ?: payload.optJSONObject("data")?.optJSONObject("video_info")
                    ?: payload.optJSONObject("data") ?: payload
                val rendition = choose720p(videoData)
                    ?: return DolaResolveResult.Failure("No explicit 720P rendition exists in video_list.")
                val keySeed = findKeySeed(payload).ifBlank { queryParameter(normalized, "key_seed") }
                    .ifBlank { metadata.keySeed }
                val token = rendition.optString("main_url").ifBlank { rendition.optString("play_url") }
                val videoUrl = decodeVideoUrl(token, keySeed)
                    ?: return DolaResolveResult.Failure("720P token decode failed; key_seed may be missing or token format changed.")
                val prompt = metadata.prompt.ifBlank { payload.optString("prompt") }
                    .ifBlank { videoData.optString("prompt") }.ifBlank { "Dola_Video" }
                DolaResolveResult.Success(
                    videoUrl, sanitizeFilename(prompt) + "_720P.mp4", prompt,
                    metadata.poster.ifBlank { videoData.optString("poster_url").ifBlank { videoData.optString("poster") } },
                    rendition.optInt("width", rendition.optInt("video_width", 1280)),
                    rendition.optInt("height", rendition.optInt("video_height", 720)),
                    rendition.optLong("bitrate", 0L)
                )
            }
        } catch (error: Exception) {
            DolaResolveResult.Failure("API request failed: " + (error.message ?: error.javaClass.simpleName))
        }
    }

    fun resolveSharedText(text: String, cookies: String, referer: String): DolaResolveResult {
        val direct = Regex("""https?://[^\s"'<>]+""").find(text)?.value?.trimEnd(')', ']', '}', ',', '.')
        if (direct != null && isDirectVideoUrl(direct)) {
            return DolaResolveResult.Success(direct, "Dola_Shared_720P.mp4", "Dola_Shared", "", 0, 0, 0)
        }
        val fallback = extractFallbackApi(text)
            ?: return DolaResolveResult.Failure("No fallback_api found. A normal Dola share link is not the extension's captured network payload.")
        val metadata = DolaMetadata(
            prompt = extractString(text, "prompt"),
            poster = extractString(text, "poster_url"),
            videoId = extractString(text, "vid"),
            keySeed = extractString(text, "key_seed"),
            messageId = extractString(text, "message_id")
        )
        return resolveFallback(fallback, metadata, cookies, referer)
    }

    private fun extractFallbackApi(text: String): String? {
        val patterns = listOf(
            Regex(""""fallback_api"\s*:\s*"((?:\\.|[^"\\])*)""""),
            Regex("""fallback_api\\":\\"(.*?)\\"""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            return match.groupValues[1].replace("\\u0026", "&").replace("\\/", "/").replace("\\\"", "\"")
        }
        return null
    }

    private fun extractString(text: String, key: String): String {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return pattern.find(text)?.groupValues?.get(1).orEmpty()
            .replace("\\u0026", "&").replace("\\/", "/")
    }

    private fun queryParameter(url: String, name: String): String =
        try { Uri.parse(url).getQueryParameter(name).orEmpty() } catch (_: Exception) { "" }

    private fun isDirectVideoUrl(value: String): Boolean {
        val path = try { Uri.parse(value).path.orEmpty().lowercase() } catch (_: Exception) { "" }
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".webm")
    }

    private fun choose720p(data: JSONObject): JSONObject? {
        val list = data.opt("video_list")
        val candidates = mutableListOf<JSONObject>()
        when (list) {
            is JSONArray -> for (i in 0 until list.length()) list.optJSONObject(i)?.let { if (is720p(it, "")) candidates += it }
            is JSONObject -> {
                val keys = list.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    list.optJSONObject(key)?.let { if (is720p(it, key)) candidates += it }
                }
            }
            else -> if (is720p(data, "")) candidates += data
        }
        return candidates.filter { it.optString("main_url").isNotBlank() || it.optString("play_url").isNotBlank() }
            .maxByOrNull { it.optLong("bitrate", 0L) }
    }

    private fun is720p(item: JSONObject, qualityKey: String): Boolean {
        val quality = listOf(qualityKey, item.optString("quality"), item.optString("definition"),
            item.optString("resolution"), item.optString("gear_name")).joinToString(" ").lowercase()
        if (Regex("""\b720\s*p?\b""").containsMatchIn(quality)) return true
        val width = item.optInt("width", item.optInt("video_width", 0))
        val height = item.optInt("height", item.optInt("video_height", 0))
        return (width == 1280 && height == 720) || (width == 720 && height == 1280)
    }

    private fun findKeySeed(value: Any?, depth: Int = 0): String {
        if (depth > 12 || value == null) return ""
        when (value) {
            is JSONObject -> {
                val direct = value.optString("key_seed")
                if (direct.isNotBlank() && direct != "null") return direct
                val keys = value.keys()
                while (keys.hasNext()) {
                    val found = findKeySeed(value.opt(keys.next()), depth + 1)
                    if (found.isNotBlank()) return found
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                val found = findKeySeed(value.opt(i), depth + 1)
                if (found.isNotBlank()) return found
            }
            is String -> {
                val match = Regex("""(?:^|[?&])key_seed=([^&"'<>\\s]+)""").find(value)
                if (match != null) return try { URLDecoder.decode(match.groupValues[1], "UTF-8") }
                    catch (_: Exception) { match.groupValues[1] }
            }
        }
        return ""
    }

    private fun decodeVideoUrl(token: String, keySeed: String): String? {
        if (token.startsWith("https://") || token.startsWith("http://")) return token
        val decoded = decodeBase64Loose(token) ?: return null
        val direct = asciiUrl(decoded).orEmpty()
        if (direct.startsWith("https://") || direct.startsWith("http://")) return direct
        return if (token.startsWith("qAAB") && keySeed.isNotBlank()) decodeQaab(token, keySeed) else null
    }

    private fun decodeQaab(token: String, keySeed: String): String? {
        val data = decodeBase64Loose(token) ?: return null
        val seed = decodeBase64Loose(keySeed) ?: return null
        val firstDigest = MessageDigest.getInstance("SHA-512").digest(seed.copyOfRange(0, minOf(32, seed.size)))
        val secondDigest = MessageDigest.getInstance("SHA-512").digest(firstDigest + hexToBytes(SALT_HEX))
        val key = secondDigest.copyOfRange(0, 16)
        val iv = secondDigest.copyOfRange(16, 32)
        val attempts = mutableListOf<Triple<ByteArray, ByteArray, ByteArray>>()
        if (data.size >= 4 && data[0] == 0xA8.toByte() && data[1] == 0.toByte() &&
            data[2] == 1.toByte() && data[3] == 0.toByte()) {
            attempts += Triple(data.copyOfRange(4, data.size), key, iv)
            attempts += Triple(data.copyOfRange(4, data.size), iv, key)
            if (data.size > 36) {
                attempts += Triple(data.copyOfRange(36, data.size), key, data.copyOfRange(20, 36))
                attempts += Triple(data.copyOfRange(36, data.size), key, iv)
            }
        } else attempts += Triple(data, key, iv)
        for ((payload, attemptKey, attemptIv) in attempts) {
            decryptAesCbc(payload, attemptKey, attemptIv)?.let { return it }
        }
        return null
    }

    private fun decryptAesCbc(payload: ByteArray, key: ByteArray, iv: ByteArray): String? {
        if (payload.isEmpty() || payload.size % 16 != 0) return null
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            asciiUrl(cipher.doFinal(payload))?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        } catch (_: Exception) { null }
    }

    private fun decodeBase64Loose(value: String): ByteArray? {
        val input = value.trim()
        val variants = listOf(input, input.replace('$', '_').replace('@', '/').replace('#', '.'),
            input.replace('$', '+').replace('@', '/').replace('#', '='))
        for (variant in variants) {
            try {
                val normalized = variant.replace('-', '+').replace('_', '/').replace(".", "=")
                    .replace(Regex("""\s"""), "")
                val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
                return Base64.decode(padded, Base64.DEFAULT)
            } catch (_: Exception) { }
        }
        return null
    }

    private fun asciiUrl(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        if (bytes.any { byte ->
                val n = byte.toInt() and 0xFF
                n != 9 && n != 10 && n != 13 && (n < 32 || n > 126)
            }) return null
        return String(bytes, Charsets.US_ASCII)
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun sanitizeFilename(value: String): String =
        value.replace(Regex("""[/\\:*?"<>|#%]"""), "_").replace(Regex("""\s+"""), "_")
            .replace(Regex("""_+"""), "_").take(80).ifBlank { "Dola_Video" }
}
