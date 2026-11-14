// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.routine

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure model for a scheduled routine (roadmap item 8).
 *
 * A routine stores a prompt, a daily time, and the weekdays it runs on. [nextRunAfter]
 * computes the next fire time from an explicit "now", so the logic is testable without the
 * system clock. Execution is NOT done here: the caller must still route the prompt through
 * the normal safety gate and confirmation path.
 */
data class ScheduledRoutine(
    val id: String,
    val name: String,
    val prompt: String,
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "routine id must not be blank" }
        require(prompt.isNotBlank()) { "routine prompt must not be blank" }
        require(days.isNotEmpty()) { "routine must run on at least one day" }
    }

    /**
     * The next fire time strictly after [now] in [zone], or null when disabled.
     * Searches up to 8 days ahead, which always finds a match for a non-empty [days] set.
     */
    fun nextRunAfter(now: ZonedDateTime, zone: ZoneId = now.zone): ZonedDateTime? {
        if (!enabled) return null
        val local = now.withZoneSameInstant(zone).toLocalDateTime()
        for (offset in 0..7L) {
            val date = local.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(local)) {
                return candidate.atZone(zone)
            }
        }
        return null
    }

    companion object {
        /** Parses "HH:mm" (24-hour). Returns null when malformed or out of range. */
        fun parseTime(text: String): LocalTime? {
            val m = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").find(text.trim()) ?: return null
            return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }
}
