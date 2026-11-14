// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

/**
 * User-facing model labels.
 *
 * Request payloads always keep the raw server id; only the UI text is prettified.
 * Unknown ids fall through unchanged so a new model never renders as an empty chip.
 */
object ModelDisplayNames {

    private val KNOWN = mapOf(
        "qwen3.8-flash-next-iq3_s" to "Qwen 3.8 Flash-Next",
    )

    fun forModelId(modelId: String): String {
        if (modelId.isBlank()) return modelId
        KNOWN[modelId]?.let { return it }
        val tail = modelId.substringAfterLast('/')
        KNOWN[tail]?.let { return it }
        return modelId
    }
}
