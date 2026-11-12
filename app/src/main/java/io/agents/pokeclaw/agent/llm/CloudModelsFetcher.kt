// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import io.agents.pokeclaw.utils.XLog

/**
 * Lists model ids from an OpenAI-compatible endpoint: GET {baseUrl}/models with a Bearer key.
 * Shared by the provider settings fetch button and the chat model switcher. Never logs the key.
 */
object CloudModelsFetcher {

    private const val TAG = "CloudModelsFetcher"

    fun fetch(baseUrl: String, apiKey: String, providerName: String? = null): Pair<List<String>, String?> {
        return try {
            val url = java.net.URL(baseUrl.trimEnd('/') + "/models")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (apiKey.isNotEmpty()) {
                    // Anthropic uses x-api-key + version header; OpenAI-compatible gateways use Bearer.
                    if (providerName.equals("ANTHROPIC", ignoreCase = true)) {
                        setRequestProperty("x-api-key", apiKey)
                        setRequestProperty("anthropic-version", "2023-06-01")
                    } else {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                }
            }
            val code = conn.responseCode
            XLog.i(TAG, "fetch: GET ${url.host}${url.path} -> HTTP $code")
            if (code !in 200..299) {
                conn.disconnect()
                return emptyList<String>() to "HTTP $code"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = org.json.JSONObject(body)
            // OpenAI-compatible: {"data":[{"id":"..."}]}; Google-native: {"models":[{"name":"models/..."}]}
            val data = json.optJSONArray("data")
            val ids = if (data != null) {
                (0 until data.length())
                    .map { data.getJSONObject(it).optString("id") }
                    .filter { it.isNotBlank() }
            } else {
                val models = json.optJSONArray("models") ?: return emptyList<String>() to null
                (0 until models.length())
                    .map { models.getJSONObject(it).optString("name").removePrefix("models/") }
                    .filter { it.isNotBlank() }
            }
            ids.sorted() to null
        } catch (e: Exception) {
            XLog.e(TAG, "fetch failed: ${e.javaClass.simpleName}")
            emptyList<String>() to (e.message ?: e.javaClass.simpleName)
        }
    }
}
