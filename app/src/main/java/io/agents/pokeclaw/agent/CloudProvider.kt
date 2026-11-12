// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

/**
 * Cloud LLM provider and model definitions.
 * Used by LlmConfigActivity to render the provider tabs + model cards.
 */

data class CloudModel(
    val id: String,
    val displayName: String,
    val inputPricePerM: Double,
    val outputPricePerM: Double,
    val tier: ModelTier,
    val contextSize: Int,
    val recommended: Boolean = false
)

enum class ModelTier(val stars: String, val label: String) {
    LITE("\u2606", "Lite"),       // ☆
    FAST("\u2605", "Fast"),       // ★
    SMART("\u2605\u2605", "Smart"),     // ★★
    PRO("\u2605\u2605\u2605", "Pro")    // ★★★
}

enum class CloudProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val models: List<CloudModel>,
    // Base URL is editable for every provider; this flag marks providers that also
    // need the free-text model-name field because they have no preset model cards.
    val showBaseUrl: Boolean = false
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        // Sanad fork: no hardcoded catalogue — the live list comes from GET /models
        // as soon as the user pastes a key (same flow for every provider).
        models = emptyList()
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        models = emptyList()
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        models = emptyList(),
        showBaseUrl = true
    ),
    GOOGLE(
        displayName = "Google",
        // OpenAI-compatible compatibility layer; /models works with a Bearer key.
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        models = emptyList()
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        // Live list comes from GET /models once the user pastes a key.
        models = emptyList()
    ),
    OPENCODE(
        displayName = "OpenCode",
        defaultBaseUrl = "https://opencode.ai/zen/v1",
        // OpenCode Zen gateway; live list comes from GET /models once a key is set.
        models = emptyList()
    ),
    CUSTOM(
        displayName = "Custom",
        defaultBaseUrl = "",
        models = emptyList(),
        showBaseUrl = true
    );

    companion object {
        /**
         * Find provider by name (case-insensitive).
         * Returns OPENAI as default.
         */
        fun fromName(name: String): CloudProvider {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: OPENAI
        }

        /**
         * Find the provider that contains a given model ID.
         */
        fun findProviderForModel(modelId: String): CloudProvider? {
            return entries.find { provider ->
                provider.models.any { it.id == modelId }
            }
        }
    }
}
