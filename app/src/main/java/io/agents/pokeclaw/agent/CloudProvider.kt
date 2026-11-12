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
        models = listOf(
            CloudModel("gpt-4o-mini", "GPT-4o Mini", 0.15, 0.60, ModelTier.FAST, 128_000, recommended = true),
            CloudModel("gpt-4o", "GPT-4o", 2.50, 10.00, ModelTier.SMART, 128_000),
            CloudModel("gpt-4.1", "GPT-4.1", 2.00, 8.00, ModelTier.PRO, 1_000_000),
            CloudModel("gpt-4.1-mini", "GPT-4.1 Mini", 0.40, 1.60, ModelTier.FAST, 1_000_000),
            CloudModel("gpt-4.1-nano", "GPT-4.1 Nano", 0.10, 0.40, ModelTier.LITE, 1_000_000),
        )
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        models = listOf(
            CloudModel("claude-sonnet-4-6", "Claude Sonnet 4.6", 3.00, 15.00, ModelTier.PRO, 200_000),
            CloudModel("claude-haiku-4-5", "Claude Haiku 4.5", 0.80, 4.00, ModelTier.FAST, 200_000, recommended = true),
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        models = listOf(
            CloudModel("deepseek-chat", "DeepSeek Chat", 0.27, 1.10, ModelTier.FAST, 163_840, recommended = true),
            CloudModel("deepseek-reasoner", "DeepSeek Reasoner", 0.80, 2.40, ModelTier.SMART, 163_840),
        ),
        showBaseUrl = true
    ),
    GOOGLE(
        displayName = "Google",
        // OpenAI-compatible compatibility layer; /models works with a Bearer key.
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        models = listOf(
            CloudModel("gemini-2.5-flash", "Gemini 2.5 Flash", 0.15, 0.60, ModelTier.FAST, 1_000_000, recommended = true),
            CloudModel("gemini-2.5-pro", "Gemini 2.5 Pro", 1.25, 10.00, ModelTier.PRO, 1_000_000),
        )
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
