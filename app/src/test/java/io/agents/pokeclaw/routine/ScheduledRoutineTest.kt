// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduledRoutineTest {

    private val zone = ZoneId.of("Asia/Dubai")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    private fun routine(
        time: String = "08:30",
        days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
        enabled: Boolean = true,
    ) = ScheduledRoutine(
        id = "r1",
        name = "morning",
        prompt = "read today's calendar",
        time = LocalTime.parse(time),
        days = days,
        enabled = enabled,
    )

    @Test
    fun nextRun_sameDay_whenTimeNotYetReached() {
        val next = routine("08:30").nextRunAfter(at(2026, 11, 13, 7, 0))
        assertEquals(at(2026, 11, 13, 8, 30), next)
    }

    @Test
    fun nextRun_rollsToTomorrow_whenTimeAlreadyPassed() {
        val next = routine("08:30").nextRunAfter(at(2026, 11, 13, 9, 0))
        assertEquals(at(2026, 11, 14, 8, 30), next)
    }

    @Test
    fun nextRun_isStrictlyAfterNow() {
        val next = routine("08:30").nextRunAfter(at(2026, 11, 13, 8, 30))
        assertEquals(at(2026, 11, 14, 8, 30), next)
    }

    @Test
    fun nextRun_skipsUnselectedDays() {
        // 2026-11-13 is a Friday; only Monday is selected -> next Monday is 2026-11-16.
        val next = routine(days = setOf(DayOfWeek.MONDAY)).nextRunAfter(at(2026, 11, 13, 9, 0))
        assertEquals(at(2026, 11, 16, 8, 30), next)
    }

    @Test
    fun nextRun_disabled_returnsNull() {
        assertNull(routine(enabled = false).nextRunAfter(at(2026, 11, 13, 7, 0)))
    }

    @Test
    fun nextRun_convertsAcrossZones() {
        val utcNow = ZonedDateTime.of(2026, 11, 13, 4, 0, 0, 0, ZoneId.of("UTC")) // 08:00 Dubai
        val next = routine("08:30").nextRunAfter(utcNow, zone)
        assertEquals(at(2026, 11, 13, 8, 30), next)
    }

    @Test
    fun parseTime_acceptsValid_rejectsInvalid() {
        assertEquals(LocalTime.of(8, 5), ScheduledRoutine.parseTime("08:05"))
        assertEquals(LocalTime.of(23, 59), ScheduledRoutine.parseTime(" 23:59 "))
        assertNull(ScheduledRoutine.parseTime("24:00"))
        assertNull(ScheduledRoutine.parseTime("8:5"))
        assertNull(ScheduledRoutine.parseTime("12:60"))
        assertNull(ScheduledRoutine.parseTime("abc"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyDays() {
        routine(days = emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankPrompt() {
        ScheduledRoutine("r1", "n", "   ", LocalTime.NOON, setOf(DayOfWeek.MONDAY))
    }
}
