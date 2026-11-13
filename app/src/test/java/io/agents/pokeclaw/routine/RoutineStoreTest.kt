// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class RoutineStoreTest {

    private fun stored(
        id: String = "r1",
        prompt: String = "check calendar",
        time: String = "08:30",
        days: List<String> = listOf("MONDAY", "FRIDAY"),
        enabled: Boolean = true,
    ) = RoutineStore.Stored(id, "morning", prompt, time, days, enabled)

    @Test
    fun validEntry_convertsToRoutine() {
        val r = RoutineStore.toRoutine(stored())
        assertEquals("r1", r?.id)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), r?.days)
        assertEquals(8, r?.time?.hour)
        assertEquals(30, r?.time?.minute)
    }

    @Test
    fun invalidTime_isSkipped() {
        assertNull(RoutineStore.toRoutine(stored(time = "25:99")))
    }

    @Test
    fun unknownDayNames_areDroppedAndEmptySetSkipsRoutine() {
        assertNull(RoutineStore.toRoutine(stored(days = listOf("FUNDAY", "NOPE"))))
    }

    @Test
    fun partiallyValidDays_keepKnownOnes() {
        val r = RoutineStore.toRoutine(stored(days = listOf("SUNDAY", "BOGUS")))
        assertEquals(setOf(DayOfWeek.SUNDAY), r?.days)
    }

    @Test
    fun blankPromptOrId_isSkipped() {
        assertNull(RoutineStore.toRoutine(stored(prompt = "   ")))
        assertNull(RoutineStore.toRoutine(stored(id = "")))
    }

    @Test
    fun disabledEntry_keepsEnabledFalse() {
        val r = RoutineStore.toRoutine(stored(enabled = false))
        assertEquals(false, r?.enabled)
    }
}
