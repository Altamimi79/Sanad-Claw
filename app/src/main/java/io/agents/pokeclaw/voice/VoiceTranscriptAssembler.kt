// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

/**
 * Merges partial + final speech-recognition hypotheses.
 *
 * Android's SpeechRecognizer reports each partial as the full transcript so far,
 * so partials REPLACE each other; finals replace the pending partial. Multiple
 * final segments (rare, some engines) are joined with a single space.
 */
class VoiceTranscriptAssembler(private val maxLength: Int = VoiceTextNormalizer.DEFAULT_MAX_LENGTH) {

    private var pendingPartial: String = ""
    private val finals = mutableListOf<String>()

    fun onPartial(text: String?) {
        val normalized = VoiceTextNormalizer.normalize(text, maxLength)
        if (normalized.isNotEmpty()) pendingPartial = normalized
    }

    fun onFinal(text: String?) {
        val normalized = VoiceTextNormalizer.normalize(text, maxLength)
        if (normalized.isNotEmpty()) finals.add(normalized)
        pendingPartial = ""
    }

    /** Best transcript so far: finalized segments, else the latest partial. */
    fun current(): String {
        if (finals.isNotEmpty()) {
            return VoiceTextNormalizer.normalize(finals.joinToString(" "), maxLength)
        }
        return pendingPartial
    }

    fun hasText(): Boolean = current().isNotEmpty()

    fun reset() {
        pendingPartial = ""
        finals.clear()
    }
}
