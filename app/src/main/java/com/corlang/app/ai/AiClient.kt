package com.corlang.app.ai

import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** One turn of a conversation. role is "user" or "assistant". */
data class ChatMessage(val role: String, val content: String)

/**
 * Minimal client for the Anthropic Messages API, using the user's own key. No SDK dependency:
 * a single HttpURLConnection POST keeps the app light and the network path auditable. The key
 * never leaves the device except in the request to api.anthropic.com.
 *
 * All calls are suspend + run on the IO dispatcher. Failures return a human-readable message so
 * callers can show it directly (bad key, no network, rate limit).
 */
class AiClient(private val prefs: LanguagePrefs) {

    private val json = Json { ignoreUnknownKeys = true }

    /** True if the user has supplied a key (the AI features are gated on this). */
    suspend fun hasKey(): Boolean = prefs.anthropicApiKey.first().isNotBlank()

    /**
     * Sends a conversation and returns the assistant's text reply.
     *
     * @param system  optional system prompt (the tutor persona / task instructions)
     * @param messages the running conversation, oldest first
     * @param model   Anthropic model id; defaults to a fast, inexpensive model for chat
     * @param maxTokens response cap
     */
    suspend fun complete(
        system: String?,
        messages: List<ChatMessage>,
        model: String = DEFAULT_MODEL,
        maxTokens: Int = 1024
    ): Result<String> = withContext(Dispatchers.IO) {
        // Server-side mode: the proxy holds the real key (server/ai-proxy); the app sends the
        // entitlement token. Active as soon as AiConfig.proxyBaseUrl is set at build time.
        val proxy = AiConfig.proxyBaseUrl
        val key = prefs.anthropicApiKey.first().trim()
        val endpoint: String
        val headers: Map<String, String>
        if (proxy != null) {
            endpoint = "${proxy.trimEnd('/')}/v1/messages"
            headers = mapOf(
                "content-type" to "application/json",
                "x-corlang-auth" to AiConfig.PROXY_AUTH_TOKEN
            )
        } else {
            // Developer fallback while no server is deployed: direct Anthropic with the local key.
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("The AI tutor isn't available yet, it arrives with Corlang Premium.")
                )
            }
            endpoint = ENDPOINT
            headers = mapOf(
                "content-type" to "application/json",
                "x-api-key" to key,
                "anthropic-version" to ANTHROPIC_VERSION
            )
        }

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            if (!system.isNullOrBlank()) put("system", system)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    }
                }
            })
        }.toString()

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (code in 200..299) {
                val reply = json.parseToJsonElement(text).jsonObject["content"]
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content
                if (reply.isNullOrBlank()) Result.failure(IllegalStateException("Empty response from the model."))
                else Result.success(reply)
            } else {
                val message = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]
                        ?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                Result.failure(IllegalStateException(friendlyError(code, message)))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Network error: ${e.message ?: "check your connection."}"))
        } finally {
            conn?.disconnect()
        }
    }

    private fun friendlyError(code: Int, message: String?): String = when (code) {
        401, 403 -> "AI access was refused. Check your Premium status and try again."
        429 -> "Rate limit reached. Wait a moment and try again."
        in 500..599 -> "The AI service is temporarily unavailable. Try again shortly."
        else -> message ?: "Request failed (HTTP $code)."
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"

        /** Fast, low-cost default for interactive tutor chat. */
        const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"

        /** Stronger model for one-shot writing feedback, where quality matters more than latency. */
        const val FEEDBACK_MODEL = "claude-sonnet-5"
    }
}
