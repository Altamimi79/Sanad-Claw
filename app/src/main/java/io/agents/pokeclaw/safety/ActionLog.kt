// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Local-only, on-device action log ring buffer (no network, no cloud).
 */
object ActionLog {

    private const val TAG = "ActionLog"
    private const val KEY_LOG = "KEY_SAFETY_ACTION_LOG"
    private const val MAX_ENTRIES = 200

    fun record(
        taskSnippet: String,
        toolName: String,
        risk: String,
        outcome: String,
        detail: String?,
    ) {
        if (!SafetySettings.actionLogEnabled()) return
        try {
            val entry = ActionLogEntry(
                timestampMs = System.currentTimeMillis(),
                taskSnippet = taskSnippet,
                toolName = toolName,
                risk = risk,
                outcome = outcome,
                detail = detail.orEmpty(),
            )
            val lines = readLines().toMutableList()
            lines.add(ActionLogFormatter.format(entry))
            while (lines.size > MAX_ENTRIES) lines.removeAt(0)
            KVUtils.putString(KEY_LOG, lines.joinToString("\n"))
            XLog.d(TAG, "recorded action tool=$toolName risk=$risk outcome=$outcome")
        } catch (e: Exception) {
            XLog.w(TAG, "failed to record action: ${e.message}")
        }
    }

    fun readLines(): List<String> {
        val raw = KVUtils.getString(KEY_LOG, "")
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    fun readEntries(): List<ActionLogEntry> =
        readLines().mapNotNull { ActionLogFormatter.parse(it) }

    fun clear() {
        KVUtils.remove(KEY_LOG)
        XLog.i(TAG, "action log cleared")
    }
}
