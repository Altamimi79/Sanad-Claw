// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.routine

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Persists routines as JSON in KV storage. Holds only the routine definition (prompt, time,
 * weekdays, enabled flag): no API keys or secrets. Malformed entries are skipped with a log
 * line instead of crashing the app.
 */
object RoutineStore {

    private const val TAG = "RoutineStore"
    private const val KEY_ROUTINES = "KEY_SCHEDULED_ROUTINES_JSON"
    private val gson = Gson()

    /** Plain data shape kept in JSON. Converted to [ScheduledRoutine] on load. */
    data class Stored(
        val id: String,
        val name: String,
        val prompt: String,
        val time: String,
        val days: List<String>,
        val enabled: Boolean,
    )

    fun loadAll(): List<ScheduledRoutine> {
        val json = KVUtils.getString(KEY_ROUTINES, "") ?: ""
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<Stored>>() {}.type
        val stored: List<Stored> = try {
            gson.fromJson<List<Stored>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to parse stored routines; ignoring", e)
            emptyList()
        }
        return stored.mapNotNull { toRoutine(it) }
    }

    fun saveAll(routines: List<ScheduledRoutine>) {
        val stored = routines.map {
            Stored(
                id = it.id,
                name = it.name,
                prompt = it.prompt,
                time = it.time.toString(),
                days = it.days.map { d -> d.name },
                enabled = it.enabled,
            )
        }
        KVUtils.putString(KEY_ROUTINES, gson.toJson(stored))
    }

    /** Converts a stored entry; returns null (and logs) if any field is invalid. */
    internal fun toRoutine(s: Stored): ScheduledRoutine? {
        val time = ScheduledRoutine.parseTime(s.time)
        val days = s.days.mapNotNull { name ->
            runCatching { DayOfWeek.valueOf(name) }.getOrNull()
        }.toSet()
        if (time == null || days.isEmpty() || s.prompt.isBlank() || s.id.isBlank()) {
            XLog.w(TAG, "Skipping invalid routine id=${s.id}")
            return null
        }
        return ScheduledRoutine(s.id, s.name, s.prompt, time, days, s.enabled)
    }

    internal fun timeOf(text: String): LocalTime? = ScheduledRoutine.parseTime(text)
}
