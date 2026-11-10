// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

/**
 * Recognition failure categories exposed by [SpeechRecognizerController].
 * Pure enum (no Android types) so the mapping/UI logic stays unit-testable.
 * [messageKey] is the string-resource NAME; UI maps it to R.string explicitly.
 */
enum class VoiceError(val messageKey: String) {
    NO_MATCH("voice_error_no_match"),
    TIMEOUT("voice_error_timeout"),
    NETWORK("voice_error_network"),
    PERMISSION("voice_error_permission"),
    BUSY("voice_error_busy"),
    AUDIO("voice_error_audio"),
    SERVER("voice_error_server"),
    CLIENT("voice_error_client"),
    LANGUAGE("voice_error_language"),
    UNKNOWN("voice_error_unknown");

    companion object {
        fun fromMessageKey(key: String): VoiceError? = entries.firstOrNull { it.messageKey == key }
    }
}
