// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Decision 2 (pause and ask). Pure JVM, no Android dependency.
 *
 * A task pauses before a tool call whose parameters contain a redaction placeholder.
 * The next user chat message within [TIMEOUT_MS] is the value; it is substituted only
 * into that placeholder parameter. The value lives in memory only: it is cleared after
 * the tool call, at task end, and on cancellation. It is never persisted and never logged.
 *
 * Thread-safety: all state transitions are synchronized. The agent worker calls
 * [pause] and [awaitValue]; the chat UI thread calls [offerMessage].
 */
class PausedTaskState(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        /** Process-wide instance used by the agent worker and the chat input handler. */
        @JvmStatic
        val shared: PausedTaskState = PausedTaskState()

        const val TIMEOUT_MS = 45_000L
        const val PLACEHOLDER_PREFIX = "[REDACTED:"
        private val PLACEHOLDER_TOKEN = Regex("""\[REDACTED:[a-z_]+\]""")

        /** True when any string value in [params] contains a redaction placeholder. */
        fun hasPlaceholder(params: Map<String, Any?>): Boolean =
            params.values.any { v -> v is String && v.contains(PLACEHOLDER_PREFIX) }

        /** Parameter names whose values contain a placeholder. */
        fun placeholderParams(params: Map<String, Any?>): List<String> =
            params.filter { (_, v) -> v is String && v.contains(PLACEHOLDER_PREFIX) }.keys.toList()
    }

    enum class Phase { IDLE, PAUSED, RESUMED, CANCELLED }

    data class Pending(val toolName: String, val paramNames: List<String>, val deadlineMs: Long)

    private var phase: Phase = Phase.IDLE
    private var pending: Pending? = null
    private var value: String? = null

    /** Pauses only when a placeholder is present. Returns false (no pause) otherwise. */
    @Synchronized
    fun pause(toolName: String, params: Map<String, Any?>): Boolean {
        if (!hasPlaceholder(params)) return false
        check(phase != Phase.PAUSED) { "already paused" }
        pending = Pending(toolName, placeholderParams(params), clock() + TIMEOUT_MS)
        value = null
        phase = Phase.PAUSED
        return true
    }

    @Synchronized
    fun phase(): Phase = phase

    @Synchronized
    fun pendingOrNull(): Pending? = pending

    /**
     * Called for every user chat message. Only a message that arrives while PAUSED and
     * before the deadline is accepted as the value. Anything else returns false and is
     * NOT treated as a value.
     */
    @Synchronized
    fun offerMessage(text: String): Boolean {
        if (phase != Phase.PAUSED) return false
        val p = pending ?: return false
        if (clock() > p.deadlineMs) return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        value = trimmed
        phase = Phase.RESUMED
        return true
    }

    /**
     * Returns a copy of [params] with the value substituted ONLY into placeholder
     * parameters. Null when not resumed. Does not clear state; call [clearAfterToolCall].
     */
    @Synchronized
    fun substitute(params: Map<String, Any?>): Map<String, Any?>? {
        if (phase != Phase.RESUMED) return null
        val v = value ?: return null
        val names = pending?.paramNames?.toSet() ?: return null
        return params.mapValues { (name, raw) ->
            if (name in names && raw is String) replacePlaceholderTokens(raw, v) else raw
        }
    }

    /** Replaces only [REDACTED:...] tokens, keeping any surrounding text. */
    private fun replacePlaceholderTokens(text: String, replacement: String): String =
        PLACEHOLDER_TOKEN.replace(text, java.util.regex.Matcher.quoteReplacement(replacement))

    /** Clears the value and pending state after the tool call. */
    @Synchronized
    fun clearAfterToolCall() {
        value = null
        pending = null
        phase = Phase.IDLE
    }

    /** Timeout or explicit cancel: wipe everything. Returns true when a pause was pending. */
    @Synchronized
    fun cancel(): Boolean {
        val wasPaused = phase == Phase.PAUSED
        value = null
        pending = null
        phase = Phase.CANCELLED
        return wasPaused
    }

    /** True when the pause deadline has passed while still waiting for a value. */
    @Synchronized
    fun isTimedOut(): Boolean {
        val p = pending ?: return false
        return phase == Phase.PAUSED && clock() > p.deadlineMs
    }

    /** Task end: clear everything, regardless of phase. */
    @Synchronized
    fun clearAtTaskEnd() {
        value = null
        pending = null
        phase = Phase.IDLE
    }

    /**
     * Blocks the worker thread until a value arrives or the deadline passes. Returns true
     * when RESUMED. On timeout the state is CANCELLED and the caller must cancel the task.
     * Poll interval is short so a test clock or a real clock both work.
     */
    fun awaitValue(sleepMs: (Long) -> Unit = { Thread.sleep(it) }): Boolean {
        while (true) {
            synchronized(this) {
                if (phase == Phase.RESUMED) return true
                if (phase != Phase.PAUSED) return false
                if (clock() > (pending?.deadlineMs ?: 0L)) {
                    cancel()
                    return false
                }
            }
            sleepMs(50L)
        }
    }
}
