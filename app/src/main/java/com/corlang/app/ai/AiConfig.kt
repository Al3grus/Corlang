package com.corlang.app.ai

import com.corlang.app.BuildConfig

/**
 * Deployment switches for the AI tutor, injected at build time from gitignored
 * local.properties (`corlang.proxyBaseUrl`, `corlang.proxyAuthToken`) — the repo is public,
 * so neither value is ever committed. Absent = AI features stay dark ("arrives with Premium").
 */
object AiConfig {

    /**
     * Base URL of the Corlang AI proxy (see server/ai-proxy). While null, the AI features are
     * simply unavailable — there is no on-device key path. Set via local.properties, e.g.
     * corlang.proxyBaseUrl=https://ai.corlang.app — no client code changes needed.
     */
    val proxyBaseUrl: String? = BuildConfig.CORLANG_PROXY_BASE_URL.ifBlank { null }

    /**
     * Auth presented to the proxy (the worker's APP_AUTH_TOKEN). Pre-billing this is a shared
     * app token checked by the Worker; once Play Billing ships it becomes the verified purchase
     * token per user (docs/server-ai.md has the wiring checklist).
     */
    val PROXY_AUTH_TOKEN: String = BuildConfig.CORLANG_PROXY_AUTH_TOKEN
}
