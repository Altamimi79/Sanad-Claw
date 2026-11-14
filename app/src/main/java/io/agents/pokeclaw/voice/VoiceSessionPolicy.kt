// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

/**
 * Continuous-dictation decisions. Pure JVM, unit-tested.
 *
 * Some speech engines end a session after a short pause, and some answer
 * NO_MATCH before the user has said anything. Both cases must not throw the
 * transcript away: with text we deliver it, without text we retry silently a
 * couple of times instead of showing an error.
 */
object VoiceSessionPolicy {

    const val MAX_SEGMENTS = 10
    const val MAX_SILENT_RETRIES = 2
    const val SILENT_RETRY_WINDOW_MS = 20_000L

    enum class OnResult { CONTINUE, DELIVER }
    enum class OnError { DELIVER, RETRY, FAIL }

    /**
     * After the engine reports a final segment: keep the microphone open unless the
     * user asked to stop, the segment budget is exhausted, or the hard cap is reached.
     */
    fun decideOnResult(
        stoppedByUser: Boolean,
        segments: Int,
        elapsedMs: Long,
        maxDurationMs: Long,
    ): OnResult = if (stoppedByUser || segments >= MAX_SEGMENTS || elapsedMs >= maxDurationMs) {
        OnResult.DELIVER
    } else {
        OnResult.CONTINUE
    }

    /**
     * On an engine error: always surface what was already recognized; retry silently
     * for transient endings (no match, speech timeout) only while nothing was heard
     * yet and the session is still young.
     */
    fun decideOnError(
        error: VoiceError,
        hasTranscript: Boolean,
        silentRetries: Int,
        elapsedMs: Long,
    ): OnError {
        if (hasTranscript) return OnError.DELIVER
        val transient = error == VoiceError.NO_MATCH || error == VoiceError.TIMEOUT
        return if (transient && silentRetries < MAX_SILENT_RETRIES && elapsedMs < SILENT_RETRY_WINDOW_MS) {
            OnError.RETRY
        } else {
            OnError.FAIL
        }
    }
}
