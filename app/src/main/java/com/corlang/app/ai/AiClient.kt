package com.corlang.app.ai

import kotlinx.coroutines.Dispatchers
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
 * Minimal client for the Corlang AI proxy (server/ai-proxy), which holds the real Anthropic key
 * server-side — no key ever lives on the device. No SDK dependency: a single HttpURLConnection
 * POST keeps the app light and the network path auditable.
 *
 * While AiConfig.proxyBaseUrl is null (proxy not deployed yet), every call fails with the
 * "arrives with Premium" message — the AI features are dark, by design.
 *
 * All calls are suspend + run on the IO dispatcher. Failures return a human-readable message so
 * callers can show it directly (no access, no network, rate limit).
 */
class AiClient {

    private val json = Json { ignoreUnknownKeys = true }

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
        maxTokens: Int = 1024,
        // Sonnet 5 defaults to ADAPTIVE thinking when the field is omitted — ~75% of output
        // tokens went to invisible reasoning on a trivial reply. Disable it for high-volume
        // interactive chat (verified: no variety-quality loss). Sonnet 5 accepts "disabled";
        // never send this to a Fable-family model (it 400s there).
        disableThinking: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        // Server-side only: the proxy holds the key (server/ai-proxy); the app sends the
        // entitlement token. Dark until AiConfig.proxyBaseUrl is set at build time.
        val proxy = AiConfig.proxyBaseUrl
            ?: return@withContext Result.failure(
                IllegalStateException("The AI tutor isn't available yet, it arrives with Corlang Premium.")
            )
        val endpoint = "${proxy.trimEnd('/')}/v1/messages"
        val headers = mapOf(
            "content-type" to "application/json",
            "x-corlang-auth" to AiConfig.PROXY_AUTH_TOKEN
        )

        // NOTE: no `temperature` — the claude-sonnet-5 family REJECTS the parameter as
        // deprecated (verified against the live API 2026-07-17); default sampling it is.
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            if (disableThinking) put("thinking", buildJsonObject { put("type", "disabled") })
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
        // Coroutine cancellation cannot interrupt blocking HttpURLConnection I/O on its own:
        // leaving the screen mid-request would keep the IO thread blocked (and the call billed)
        // for up to the full timeout. disconnect() from the cancellation path aborts the
        // socket, which unblocks the read with an exception immediately.
        val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        val cancelHook = job?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) conn?.disconnect()
        }
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
                val root = json.parseToJsonElement(text).jsonObject
                // First TEXT block, not content[0]: the model may emit a thinking block first.
                val reply = root["content"]
                    ?.jsonArray
                    ?.firstNotNullOfOrNull { el ->
                        el.jsonObject.takeIf { it["type"]?.jsonPrimitive?.content == "text" }
                            ?.get("text")?.jsonPrimitive?.content
                    }
                // stop_reason matters: with thinking enabled, max_tokens can be spent entirely
                // on invisible reasoning (no text block at all) or cut the visible reply
                // mid-sentence. Never present a truncated correction as if it were complete.
                val hitCap = root["stop_reason"]?.jsonPrimitive?.content == "max_tokens"
                when {
                    reply.isNullOrBlank() && hitCap -> Result.failure(
                        IllegalStateException("The reply ran too long, please try again with a shorter message.")
                    )
                    reply.isNullOrBlank() -> Result.failure(IllegalStateException("Empty response from the model."))
                    hitCap -> Result.success("$reply\n\n(Reply was cut short — ask me to continue.)")
                    else -> Result.success(reply)
                }
            } else {
                val message = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]
                        ?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                Result.failure(IllegalStateException(friendlyError(code, message)))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // never swallow cancellation as a "network error"
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Network error: ${e.message ?: "check your connection."}"))
        } finally {
            cancelHook?.dispose()
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
        /** Fast, low-cost default for interactive tutor chat. */
        const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"

        /** Stronger model for one-shot writing feedback, where quality matters more than latency. */
        const val FEEDBACK_MODEL = "claude-sonnet-5"
    }
}
