// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Local-only, on-device action log ring buffer (no network, no cloud).
 * Writes v2 lines; reads v2 and legacy v1 lines in stored order.
 */
/** Read view of one stored line. Legacy v1 lines have reason UNKNOWN_LEGACY and a nullable result. */
data class ActionLogReadEntry(
    val timestampMs: Long,
    val taskSnippet: String,
    val toolName: String,
    val risk: String,
    val reason: ActionReason,
    val result: ActionResult?,
    val detail: String,
)

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
        reason: ActionReason = ActionReason.UNKNOWN_LEGACY,
        result: ActionResult = ActionResult.PENDING,
    ) {
        if (!SafetySettings.actionLogEnabled()) return
        try {
            val entry = ActionLogEntryV2(
                timestampMs = System.currentTimeMillis(),
                taskSnippet = taskSnippet,
                toolName = toolName,
                risk = risk,
                reason = reason,
                result = result,
                detail = detail.orEmpty(),
            )
            val lines = readLines().toMutableList()
            lines.add(ActionLogFormatter.formatV2(entry))
            while (lines.size > MAX_ENTRIES) lines.removeAt(0)
            KVUtils.putString(KEY_LOG, lines.joinToString("\n"))
            XLog.d(TAG, "recorded action tool=$toolName risk=$risk reason=${reason.name} result=${result.name}")
        } catch (e: Exception) {
            XLog.w(TAG, "failed to record action: ${e.message}")
        }
    }

    fun readLines(): List<String> {
        val raw = KVUtils.getString(KEY_LOG, "")
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    /**
     * Reads every stored line in order. A "v2" line is parsed as v2. Any other line is a
     * legacy v1 line: it keeps its fields, reason = UNKNOWN_LEGACY, and no result is invented.
     */
    fun readEntriesMixed(): List<ActionLogReadEntry> = readLines().mapNotNull { line ->
        if (line.startsWith("${ActionLogFormatter.V2_TAG} | ")) {
            ActionLogFormatter.parseV2(line)?.let { v2 ->
                ActionLogReadEntry(
                    timestampMs = v2.timestampMs,
                    taskSnippet = v2.taskSnippet,
                    toolName = v2.toolName,
                    risk = v2.risk,
                    reason = v2.reason,
                    result = v2.result,
                    detail = v2.detail,
                )
            }
        } else {
            ActionLogFormatter.parse(line)?.let { v1 ->
                ActionLogReadEntry(
                    timestampMs = v1.timestampMs,
                    taskSnippet = v1.taskSnippet,
                    toolName = v1.toolName,
                    risk = v1.risk,
                    reason = ActionReason.UNKNOWN_LEGACY,
                    result = legacyResultOrNull(v1.outcome),
                    detail = v1.detail,
                )
            }
        }
    }

    /** Result is set only for the fixed legacy outcome words; anything else stays unknown. */
    fun legacyResultOrNull(outcome: String): ActionResult? = when (outcome) {
        "ok" -> ActionResult.SUCCESS
        "error" -> ActionResult.FAILED
        "blocked" -> ActionResult.DENIED
        "cancelled", "stopped" -> ActionResult.CANCELLED
        else -> null
    }

    /** Compatibility reader used by the viewer: mixed history in stored order. */
    fun readEntries(): List<ActionLogReadEntry> = readEntriesMixed()

    fun clear() {
        KVUtils.remove(KEY_LOG)
        XLog.i(TAG, "action log cleared")
    }
}
