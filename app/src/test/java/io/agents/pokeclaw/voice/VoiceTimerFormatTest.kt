// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTimerFormatTest {

    @Test
    fun formatsSecondsAsMmSs() {
        assertEquals("00:00", VoiceTimerFormat.format(0))
        assertEquals("00:05", VoiceTimerFormat.format(5))
        assertEquals("01:05", VoiceTimerFormat.format(65))
        assertEquals("10:00", VoiceTimerFormat.format(600))
    }

    @Test
    fun negativeClampsToZero() {
        assertEquals("00:00", VoiceTimerFormat.format(-7))
    }

    @Test
    fun elapsedSecondsForTimestamps() {
        assertEquals(0, VoiceTimerFormat.elapsedSeconds(1_000L, 1_500L))
        assertEquals(1, VoiceTimerFormat.elapsedSeconds(1_000L, 2_999L))
        assertEquals(2, VoiceTimerFormat.elapsedSeconds(1_000L, 3_000L))
        assertEquals(0, VoiceTimerFormat.elapsedSeconds(5_000L, 4_000L))
    }
}
