// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

/**
 * Text cleanup for speech-recognition results. Pure JVM, unit-tested.
 */
object VoiceTextNormalizer {

    const val DEFAULT_MAX_LENGTH: Int = 2_000

    /** Zero-width / bidi control characters that recognizers sometimes emit. */
    private val INVISIBLES = Regex("[\u200B-\u200F\u202A-\u202E\u2066-\u2069\uFEFF]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Trim, drop invisible bidi marks, and collapse all whitespace runs (including
     * newlines) into a single space so the transcript drops cleanly into the composer.
     */
    fun normalize(raw: String?, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (raw.isNullOrEmpty()) return ""
        val cleaned = INVISIBLES.replace(raw, "")
            .replace(WHITESPACE, " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        return if (cleaned.length <= maxLength) cleaned else cleaned.substring(0, maxLength).trimEnd()
    }
}
