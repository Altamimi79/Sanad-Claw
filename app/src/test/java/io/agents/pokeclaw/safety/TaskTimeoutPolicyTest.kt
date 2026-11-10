// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTimeoutPolicyTest {

    @Test
    fun timeoutConvertsAndClamps() {
        assertEquals(180_000L, TaskTimeoutPolicy.timeoutMs(180))
        assertEquals(30_000L, TaskTimeoutPolicy.timeoutMs(1))
        assertEquals(3_600_000L, TaskTimeoutPolicy.timeoutMs(99_999))
    }

    @Test
    fun timeoutDetection() {
        assertFalse(TaskTimeoutPolicy.isTimedOut(1_000, 180_999, 180_000))
        assertTrue(TaskTimeoutPolicy.isTimedOut(1_000, 181_000, 180_000))
        assertEquals(0L, TaskTimeoutPolicy.remainingMs(0, 999_999, 180_000))
        assertEquals(90_000L, TaskTimeoutPolicy.remainingMs(0, 90_000, 180_000))
    }

    @Test
    fun presetCycling() {
        assertEquals(180, TaskTimeoutPolicy.nextPreset(120))
        assertEquals(300, TaskTimeoutPolicy.nextPreset(180))
        assertEquals(600, TaskTimeoutPolicy.nextPreset(300))
        assertEquals(120, TaskTimeoutPolicy.nextPreset(600))
        assertEquals(TaskTimeoutPolicy.DEFAULT_TIMEOUT_SEC, TaskTimeoutPolicy.nextPreset(999))
    }
}
