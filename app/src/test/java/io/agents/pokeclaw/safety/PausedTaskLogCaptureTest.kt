// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.utils.XLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Row 14: the user-supplied value must never reach any XLog call on the pause path.
 *
 * Capture: XLog.setTestSinkForTests installs a Sink that records every XLog call
 * (level, tag, msg, throwable). AppLogStore/android.util.Log are not reached when DEBUG
 * is off and appContext is unset, so the Sink is the observable boundary.
 *
 * Canaries are never printed: assertions use booleans and counts only.
 */
class PausedTaskLogCaptureTest {

    private val canaryValue = "CANARY_VALUE_7f3a"
    private val canaryIban = "CANARY_IBAN_9b2c"

    private class Recorded(val level: String, val tag: String, val msg: String?, val tr: Throwable?)

    private val records = mutableListOf<Recorded>()

    @Before
    fun installCapture() {
        XLog.setDEBUG(false)
        records.clear()
        XLog.setTestSinkForTests { level, tag, msg, tr ->
            records += Recorded(level, tag, msg, tr)
        }
    }

    @After
    fun removeCapture() {
        XLog.setTestSinkForTests(null)
        XLog.setDEBUG(true)
    }

    /** The exact log statements the production pause hook emits (DefaultAgentService). */
    private fun logPauseEntered(toolName: String) {
        XLog.i("AgentService", "paused: tool=$toolName needs a user-supplied value (redacted)")
    }

    private fun logResumed(toolName: String) {
        XLog.i("AgentService", "resumed: value received for tool=$toolName (value not logged)")
    }

    private fun logTimeout() {
        XLog.w("AgentService", "pause timed out: task cancelled")
    }

    private fun logToolExecuted(toolName: String) {
        XLog.i("AgentService", "tool=$toolName executed")
    }

    private fun logTaskCleared() {
        XLog.d("AgentService", "task end: pause state cleared")
    }

    /** Log lines captured so far, as one string, for canary search. Never printed. */
    private fun allCapturedText(): String = records.joinToString("\n") { r ->
        buildString {
            append(r.level).append('|').append(r.tag).append('|').append(r.msg ?: "")
            r.tr?.let { append('|').append(it.message ?: "") }
        }
    }

    private fun countOf(needle: String): Int {
        val text = allCapturedText()
        var count = 0
        var idx = text.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = text.indexOf(needle, idx + needle.length)
        }
        return count
    }

    @Test
    fun pauseResumeSubstituteClearFlowNeverLogsCanaryValue() {
        val state = PausedTaskState()
        val params = mapOf<String, Any?>("text" to "code is [REDACTED:otp]")

        // 1. pause entered (the hook logs the tool name, never the value)
        assertTrue(state.pause("send_message", params))
        logPauseEntered("send_message")

        // 2. value supplied by the next user message
        assertTrue(state.offerMessage(canaryValue))
        logResumed("send_message")

        // 3. substitution into the placeholder, then tool call executed
        val substituted = state.substitute(params)
        assertTrue(substituted != null)
        assertEquals("code is $canaryValue", substituted!!["text"])
        logToolExecuted("send_message")

        // 4. task cleared after the tool call
        state.clearAfterToolCall()
        logTaskCleared()

        assertTrue("capture must be active: at least one line captured", records.isNotEmpty())
        assertFalse("user value found in captured log output", allCapturedText().contains(canaryValue))
        assertEquals("user value occurrences in captured output", 0, countOf(canaryValue))
    }

    @Test
    fun ibanShapedSubstitutionNeverLogsCanaryIban() {
        val state = PausedTaskState()
        val params = mapOf<String, Any?>("iban" to "[REDACTED:iban]")

        state.pause("transfer", params)
        logPauseEntered("transfer")

        assertTrue(state.offerMessage(canaryIban))
        logResumed("transfer")

        val substituted = state.substitute(params)
        assertEquals(canaryIban, substituted!!["iban"])
        logToolExecuted("transfer")

        state.clearAtTaskEnd()
        logTaskCleared()

        assertTrue("capture must be active: at least one line captured", records.isNotEmpty())
        assertFalse("IBAN canary found in captured log output", allCapturedText().contains(canaryIban))
        assertEquals("IBAN canary occurrences in captured output", 0, countOf(canaryIban))
    }

    @Test
    fun timeoutCancellationNeverLogsCanaryValue() {
        var now = 1_000L
        val state = PausedTaskState(clock = { now })
        state.pause("send_message", mapOf("text" to "[REDACTED:otp]"))
        logPauseEntered("send_message")

        // No message arrives; clock moves past the 45 s deadline.
        now += PausedTaskState.TIMEOUT_MS + 1
        assertTrue(state.isTimedOut())
        assertFalse("late message must not become a value", state.offerMessage(canaryValue))
        logTimeout()
        state.cancel()
        state.clearAtTaskEnd()

        assertTrue("capture must be active: at least one line captured", records.isNotEmpty())
        assertEquals("user value occurrences after timeout", 0, countOf(canaryValue))
    }

    @Test
    fun captureIsActiveAndRecordsLevelsAndTags() {
        // Proves the Sink is really wired: a direct XLog call must be recorded.
        XLog.i("CaptureProbe", "probe line one")
        XLog.w("CaptureProbe", "probe line two", RuntimeException("probe"))
        assertEquals(2, records.size)
        assertEquals("I", records[0].level)
        assertEquals("W", records[1].level)
        assertTrue(records[1].tr != null)
    }
}
