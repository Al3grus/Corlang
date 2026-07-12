package com.corlang.app.ai

/**
 * Deployment switches for the AI tutor. The client is fully built for server-side AI; these
 * constants are the only thing left to flip when the infrastructure accounts exist.
 */
object AiConfig {

    /**
     * Base URL of the Corlang AI proxy (see server/ai-proxy). While null, AiClient falls back
     * to calling Anthropic directly with the developer API key stored on-device (the pre-launch
     * testing path). Set to the deployed Worker URL, e.g. "https://ai.corlang.app", to switch
     * every install to server-side AI, no client code changes needed.
     */
    val proxyBaseUrl: String? = null

    /**
     * Auth presented to the proxy. Pre-billing this is a shared app token checked by the
     * Worker; once Play Billing ships it becomes the verified purchase token per user
     * (docs/server-ai.md has the wiring checklist).
     */
    const val PROXY_AUTH_TOKEN = "corlang-dev"
}
