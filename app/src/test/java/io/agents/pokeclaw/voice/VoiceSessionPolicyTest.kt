// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionPolicyTest {

    @Test
    fun keepsListeningAfterSegmentUntilUserStops() {
        assertEquals(
            VoiceSessionPolicy.OnResult.CONTINUE,
            VoiceSessionPolicy.decideOnResult(stoppedByUser = false, segments = 1, elapsedMs = 2_000, maxDurationMs = 120_000),
        )
        assertEquals(
            VoiceSessionPolicy.OnResult.DELIVER,
            VoiceSessionPolicy.decideOnResult(stoppedByUser = true, segments = 1, elapsedMs = 2_000, maxDurationMs = 120_000),
        )
    }

    @Test
    fun deliversWhenSegmentBudgetOrDurationIsExhausted() {
        assertEquals(
            VoiceSessionPolicy.OnResult.DELIVER,
            VoiceSessionPolicy.decideOnResult(false, VoiceSessionPolicy.MAX_SEGMENTS, 5_000, 120_000),
        )
        assertEquals(
            VoiceSessionPolicy.OnResult.DELIVER,
            VoiceSessionPolicy.decideOnResult(false, 1, 120_000, 120_000),
        )
    }

    @Test
    fun deliversRecognizedTextInsteadOfFailing() {
        assertEquals(
            VoiceSessionPolicy.OnError.DELIVER,
            VoiceSessionPolicy.decideOnError(VoiceError.NO_MATCH, hasTranscript = true, silentRetries = 5, elapsedMs = 30_000),
        )
        assertEquals(
            VoiceSessionPolicy.OnError.DELIVER,
            VoiceSessionPolicy.decideOnError(VoiceError.NETWORK, hasTranscript = true, silentRetries = 0, elapsedMs = 1_000),
        )
    }

    @Test
    fun retriesTransientSilenceBeforeSpeech() {
        assertEquals(
            VoiceSessionPolicy.OnError.RETRY,
            VoiceSessionPolicy.decideOnError(VoiceError.NO_MATCH, hasTranscript = false, silentRetries = 0, elapsedMs = 800),
        )
        assertEquals(
            VoiceSessionPolicy.OnError.RETRY,
            VoiceSessionPolicy.decideOnError(VoiceError.TIMEOUT, hasTranscript = false, silentRetries = 1, elapsedMs = 3_000),
        )
        assertEquals(
            VoiceSessionPolicy.OnError.FAIL,
            VoiceSessionPolicy.decideOnError(VoiceError.NO_MATCH, hasTranscript = false, silentRetries = 2, elapsedMs = 3_000),
        )
        assertEquals(
            VoiceSessionPolicy.OnError.FAIL,
            VoiceSessionPolicy.decideOnError(VoiceError.TIMEOUT, hasTranscript = false, silentRetries = 0, elapsedMs = 21_000),
        )
    }

    @Test
    fun neverRetriesHardErrors() {
        listOf(VoiceError.PERMISSION, VoiceError.BUSY, VoiceError.AUDIO, VoiceError.LANGUAGE).forEach { error ->
            assertEquals(
                VoiceSessionPolicy.OnError.FAIL,
                VoiceSessionPolicy.decideOnError(error, hasTranscript = false, silentRetries = 0, elapsedMs = 500),
            )
        }
    }
}
