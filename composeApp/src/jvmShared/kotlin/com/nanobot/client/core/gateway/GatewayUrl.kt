package com.nanobot.client.core.gateway

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface UrlResult {
    data class Valid(val url: String) : UrlResult
    data class Invalid(val reason: String) : UrlResult
}

fun normalizeBaseUrl(raw: String): UrlResult {
    val value = raw.trim().trimEnd('/')
    if (value.isEmpty()) return UrlResult.Invalid("empty")
    if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
        return UrlResult.Invalid("scheme")
    }
    val parsed = value.toHttpUrlOrNull() ?: return UrlResult.Invalid("invalid")
    if (parsed.host.isBlank()) return UrlResult.Invalid("host")
    if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return UrlResult.Invalid("credentials")
    if (parsed.query != null || parsed.fragment != null) return UrlResult.Invalid("query")
    return UrlResult.Valid(parsed.toString().trimEnd('/'))
}

fun buildWebSocketUrl(baseUrl: String, bootstrap: com.nanobot.client.core.model.BootstrapResponse): String {
    val explicit = bootstrap.wsUrl?.takeIf { it.startsWith("ws://", true) || it.startsWith("wss://", true) }
    val httpUrl = if (explicit != null) {
        val secure = explicit.startsWith("wss://", true)
        explicit.replaceFirst(if (secure) "wss" else "ws", if (secure) "https" else "http", ignoreCase = true)
    } else {
        val base = baseUrl.toHttpUrlOrNull() ?: error("invalid base url")
        base.newBuilder()
            .encodedPath(if (bootstrap.wsPath.startsWith('/')) bootstrap.wsPath else "/${bootstrap.wsPath}")
            .build()
            .toString()
    }
    val tokenized = httpUrl.toHttpUrlOrNull()!!.newBuilder()
        .addQueryParameter("token", bootstrap.token)
        .build()
        .toString()
    return when {
        tokenized.startsWith("https://", ignoreCase = true) ->
            "wss://" + tokenized.removePrefix("https://")
        tokenized.startsWith("http://", ignoreCase = true) ->
            "ws://" + tokenized.removePrefix("http://")
        else -> tokenized
    }
}
