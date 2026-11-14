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

/** Why an action was taken or stopped. Fixed keys only, never free text. */
enum class ActionReason {
    USER_REQUESTED,
    SAFETY_CONFIRMED,
    SAFETY_DENIED,
    BLOCKED_BY_APP,
    BLOCKED_BY_TAINT,
    TIMEOUT,
    EMERGENCY_STOP,
    COMPLETED,
    UNKNOWN_LEGACY,
}

/** Result of an action. Fixed keys only, never free text. */
enum class ActionResult {
    SUCCESS,
    FAILED,
    DENIED,
    CANCELLED,
    PENDING,
}

/** A v2 entry: the v1 fields plus a reason key and a result key. */
data class ActionLogEntryV2(
    val timestampMs: Long,
    val taskSnippet: String,
    val toolName: String,
    val risk: String,
    val reason: ActionReason,
    val result: ActionResult,
    val detail: String,
)

object ActionLogFormatter {

    const val MAX_DETAIL = 180
    const val MAX_TASK = 80
    const val V2_TAG = "v2"
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
        // Sensitive-data redaction first (card, IBAN, national ID, OTP) so the log never holds them.
        text = SensitiveDataRedactor.redact(text).text
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

    /** Pipes in free fields would shift the split positions; replace them. */
    private fun field(value: String): String = value.replace('|', '/').replace('\n', ' ')

    /**
     * Plain-text export: one entry per line, same order as stored. Every field goes through
     * [sanitize] again (SensitiveDataRedactor + secret patterns) before it is written, so the
     * export never contains a value that was not already redacted. Deterministic: same input,
     * same output. Empty input gives an empty document (no lines).
     */
    fun buildExport(entries: List<ActionLogReadEntry>): String = entries.joinToString("\n") { e ->
        listOf(
            V2_TAG,
            e.timestampMs.toString(),
            field(sanitize(e.taskSnippet, MAX_TASK)),
            field(sanitize(e.toolName, MAX_TASK)),
            field(sanitize(e.risk, MAX_TASK)),
            e.reason.name,
            (e.result ?: ActionResult.PENDING).name,
            sanitize(e.detail),
        ).joinToString(SEPARATOR)
    }

    /**
     * Writes a v2 line:
     * `v2 | <ts> | <task> | <tool> | <risk> | <reason_key> | <result_key> | <redacted detail>`
     */
    fun formatV2(entry: ActionLogEntryV2): String = listOf(
        V2_TAG,
        entry.timestampMs.toString(),
        field(sanitize(entry.taskSnippet, MAX_TASK)),
        field(entry.toolName),
        field(entry.risk),
        entry.reason.name,
        entry.result.name,
        sanitize(entry.detail),
    ).joinToString(SEPARATOR)

    /** Parses a v2 line. Returns null for malformed lines or keys outside the enums. */
    fun parseV2(line: String): ActionLogEntryV2? {
        val parts = line.split(SEPARATOR, limit = 8)
        if (parts.size != 8 || parts[0] != V2_TAG) return null
        val ts = parts[1].toLongOrNull() ?: return null
        val reason = ActionReason.entries.firstOrNull { it.name == parts[5] } ?: return null
        val result = ActionResult.entries.firstOrNull { it.name == parts[6] } ?: return null
        return ActionLogEntryV2(
            timestampMs = ts,
            taskSnippet = parts[2],
            toolName = parts[3],
            risk = parts[4],
            reason = reason,
            result = result,
            detail = parts[7],
        )
    }
}
