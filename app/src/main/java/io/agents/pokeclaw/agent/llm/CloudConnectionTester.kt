// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import io.agents.pokeclaw.agent.CloudProvider
import io.agents.pokeclaw.agent.LlmProvider
import io.agents.pokeclaw.agent.langchain.http.OkHttpClientBuilderAdapter
import io.agents.pokeclaw.utils.XLog
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration

/**
 * Real "test connection" probe for cloud providers (phase 2 provider settings).
 * Sends one minimal chat request against the configured endpoint/model.
 * Works for Anthropic (Claude) and any OpenAI-compatible provider
 * (OpenAI, DeepSeek, OpenRouter, Groq, Together, Ollama, custom base URLs).
 *
 * Never includes the API key in results or logs.
 */
object CloudConnectionTester {

    private const val TAG = "CloudConnectionTester"

    enum class Status { OK, UNAUTHORIZED, MODEL_NOT_FOUND, TIMEOUT, NETWORK, GENERIC }

    data class Result(val status: Status, val detail: String)

    /**
     * Normalize a user-typed base URL for langchain4j (expects the API root,
     * e.g. https://api.deepseek.com/v1 — it appends /chat/completions itself).
     * Pure + unit-testable.
     */
    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        while (url.endsWith("/")) url = url.dropLast(1)
        for (suffix in listOf("/chat/completions", "/completions", "/responses")) {
            if (url.endsWith(suffix)) {
                url = url.dropLast(suffix.length)
                break
            }
        }
        while (url.endsWith("/")) url = url.dropLast(1)
        return url
    }

    fun test(
        providerName: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): Result {
        if (apiKey.isBlank()) return Result(Status.UNAUTHORIZED, "missing api key")
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        if (normalizedBaseUrl.isEmpty()) return Result(Status.GENERIC, "missing base url")

        return try {
            val model = buildProbeModel(providerName, normalizedBaseUrl, apiKey, modelId)
            val response = model.chat(UserMessage.from("ping"))
            val text = response.aiMessage()?.text() ?: ""
            XLog.i(TAG, "test: provider=$providerName baseUrl=$normalizedBaseUrl model=$modelId ok (replyLen=${text.length})")
            Result(Status.OK, "")
        } catch (e: Exception) {
            val chain = generateSequence<Throwable>(e) { it.cause }.joinToString(" | ") {
                (it.message ?: it.javaClass.simpleName).take(200)
            }
            val sanitized = sanitize(chain)
            XLog.w(TAG, "test: provider=$providerName baseUrl=$normalizedBaseUrl model=$modelId failed: $sanitized")
            Result(classify(e, sanitized), sanitized)
        }
    }

    private fun buildProbeModel(
        providerName: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): ChatModel {
        val provider = CloudProvider.fromName(providerName)
        val agentProvider = when (provider) {
            CloudProvider.ANTHROPIC -> LlmProvider.ANTHROPIC
            else -> LlmProvider.OPENAI
        }
        return when (agentProvider) {
            LlmProvider.ANTHROPIC -> AnthropicChatModel.builder()
                .httpClientBuilder(OkHttpClientBuilderAdapter())
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelId)
                .temperature(0.0)
                .maxTokens(16)
                .timeout(Duration.ofSeconds(25))
                .build()

            else -> OpenAiChatModel.builder()
                .httpClientBuilder(OkHttpClientBuilderAdapter())
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelId)
                .temperature(0.0)
                .maxTokens(16)
                .timeout(Duration.ofSeconds(25))
                .build()
        }
    }

    private fun classify(error: Exception, chain: String): Status {
        val lower = chain.lowercase()
        return when {
            lower.contains("401") || lower.contains("unauthorized") ||
                lower.contains("invalid api key") || lower.contains("incorrect api key") ||
                lower.contains("authentication") -> Status.UNAUTHORIZED

            lower.contains("404") || lower.contains("not found") && lower.contains("model") ||
                lower.contains("model_not_found") || lower.contains("does not exist") -> Status.MODEL_NOT_FOUND

            error is SocketTimeoutException || lower.contains("timeout") || lower.contains("timed out") -> Status.TIMEOUT

            error is IOException || lower.contains("failed to connect") ||
                lower.contains("unable to resolve host") || lower.contains("connection refused") -> Status.NETWORK

            else -> Status.GENERIC
        }
    }

    private fun sanitize(chain: String): String {
        return chain
            .replace(Regex("sk-[A-Za-z0-9_\\-]{6,}"), "sk-***)")
            .take(240)
    }
}
