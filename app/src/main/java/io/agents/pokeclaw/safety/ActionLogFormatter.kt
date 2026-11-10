// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Redaction + line format for the local action log. Pure JVM, unit-tested.
 * The log is stored on-device only and never uploaded.
 */
data class ActionLogEntry(
    val timestampMs: Long,
    val taskSnippet: String,
    val toolName: String,
    val risk: String,
    val outcome: String,
    val detail: String,
)

object ActionLogFormatter {

    const val MAX_DETAIL = 180
    const val MAX_TASK = 80
    private const val SEPARATOR = " | "

    private val SECRET_PATTERNS = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{6,}"""),
        Regex("""(?i)(api[_ -]?key|token|password|passcode|secret)\s*[:=]\s*\S+"""),
        Regex("""(?i)bearer\s+[A-Za-z0-9._\-]+"""),
        Regex("""\b\d{12,19}\b"""), // card-like numbers
    )

    fun sanitize(value: String?, max: Int = MAX_DETAIL): String {
        var text = value?.replace('\n', ' ')?.trim().orEmpty()
        if (text.isEmpty()) return ""
        SECRET_PATTERNS.forEach { text = it.replace(text, "***") }
        return if (text.length <= max) text else text.substring(0, max) + "…"
    }

    fun format(entry: ActionLogEntry): String = listOf(
        entry.timestampMs.toString(),
        sanitize(entry.taskSnippet, MAX_TASK),
        entry.toolName,
        entry.risk,
        entry.outcome,
        sanitize(entry.detail),
    ).joinToString(SEPARATOR)

    fun parse(line: String): ActionLogEntry? {
        val parts = line.split(SEPARATOR, limit = 6)
        if (parts.size != 6) return null
        val ts = parts[0].toLongOrNull() ?: return null
        return ActionLogEntry(
            timestampMs = ts,
            taskSnippet = parts[1],
            toolName = parts[2],
            risk = parts[3],
            outcome = parts[4],
            detail = parts[5],
        )
    }
}
