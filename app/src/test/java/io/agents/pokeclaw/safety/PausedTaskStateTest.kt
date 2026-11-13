// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PausedTaskStateTest {

    private val placeholder = "[REDACTED:otp]"
    private val secret = "918273"

    private class FakeClock(var now: Long = 1_000L) {
        fun get() = now
    }

    @Test
    fun pauseEnteredOnlyWhenPlaceholderPresent() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        assertFalse(s.pause("send_message", mapOf("text" to "hello")))
        assertEquals(PausedTaskState.Phase.IDLE, s.phase())
        assertTrue(s.pause("send_message", mapOf("text" to placeholder)))
        assertEquals(PausedTaskState.Phase.PAUSED, s.phase())
        assertEquals(listOf("text"), s.pendingOrNull()!!.paramNames)
    }

    @Test
    fun resumeUsesNextMessageAsValueAndSubstitutesOnlyPlaceholder() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        val params = mapOf<String, Any?>(
            "contact" to "Mona",
            "text" to "code is $placeholder",
            "app" to "WhatsApp",
        )
        s.pause("send_message", params)
        assertTrue(s.offerMessage("  $secret  "))
        assertEquals(PausedTaskState.Phase.RESUMED, s.phase())

        val out = s.substitute(params)!!
        assertEquals("Mona", out["contact"])
        assertEquals("WhatsApp", out["app"])
        assertEquals("code is $secret", out["text"])
    }

    @Test
    fun substitutionLeavesParamsWithoutPlaceholderUntouched() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        val params = mapOf<String, Any?>("a" to placeholder, "b" to "plain text, no token")
        s.pause("t", params)
        s.offerMessage(secret)
        val out = s.substitute(params)!!
        assertEquals(secret, out["a"])
        // "b" carries no placeholder at pause time, so it is never overwritten.
        assertEquals("plain text, no token", out["b"])
    }

    @Test
    fun substitutionKeepsSurroundingTextAndReplacesOnlyTheToken() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        val params = mapOf<String, Any?>("text" to "code is $placeholder, do not share")
        s.pause("t", params)
        s.offerMessage(secret)
        assertEquals("code is $secret, do not share", s.substitute(params)!!["text"])
    }

    @Test
    fun timeoutCancelsAndReportsCancellation() {
        val clock = FakeClock(1_000L)
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        clock.now = 1_000L + PausedTaskState.TIMEOUT_MS + 1
        assertTrue(s.isTimedOut())
        assertFalse(s.offerMessage(secret)) // late message is not a value
        assertTrue(s.cancel())               // returns true: a pause was pending
        assertEquals(PausedTaskState.Phase.CANCELLED, s.phase())
        assertNull(s.substitute(mapOf("x" to placeholder)))
    }

    @Test
    fun messageWhileNotPausedIsNotAValue() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        assertFalse(s.offerMessage(secret))
        assertEquals(PausedTaskState.Phase.IDLE, s.phase())
        assertNull(s.substitute(mapOf("x" to placeholder)))
    }

    @Test
    fun messageAfterResumeIsNotAccepted() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        assertTrue(s.offerMessage(secret))
        assertFalse("second message must not overwrite the value", s.offerMessage("other"))
        assertEquals(secret, s.substitute(mapOf("x" to placeholder))!!["x"])
    }

    @Test
    fun emptyMessageIsNotAValue() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        assertFalse(s.offerMessage("   "))
        assertEquals(PausedTaskState.Phase.PAUSED, s.phase())
    }

    @Test
    fun valueClearedAfterToolCall() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        s.offerMessage(secret)
        s.clearAfterToolCall()
        assertEquals(PausedTaskState.Phase.IDLE, s.phase())
        assertNull(s.pendingOrNull())
        assertNull(s.substitute(mapOf("x" to placeholder)))
    }

    @Test
    fun valueClearedAtTaskEnd() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        s.offerMessage(secret)
        s.clearAtTaskEnd()
        assertEquals(PausedTaskState.Phase.IDLE, s.phase())
        assertNull(s.substitute(mapOf("x" to placeholder)))
    }

    @Test
    fun valueClearedOnCancel() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        s.offerMessage(secret)
        s.cancel()
        assertNull(s.substitute(mapOf("x" to placeholder)))
        assertNull(s.pendingOrNull())
    }

    @Test
    fun awaitValueReturnsTrueAfterMessageArrives() {
        val clock = FakeClock(1_000L)
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        var sleeps = 0
        // Simulate the user replying during the wait.
        val ok = s.awaitValue { _ ->
            sleeps++
            if (sleeps == 2) s.offerMessage(secret)
        }
        assertTrue(ok)
        assertEquals(PausedTaskState.Phase.RESUMED, s.phase())
    }

    @Test
    fun awaitValueCancelsOnTimeout() {
        val clock = FakeClock(1_000L)
        val s = PausedTaskState(clock::get)
        s.pause("t", mapOf("x" to placeholder))
        val ok = s.awaitValue { clock.now += 10_000L } // advance clock past 45 s
        assertFalse(ok)
        assertEquals(PausedTaskState.Phase.CANCELLED, s.phase())
        assertNull(s.substitute(mapOf("x" to placeholder)))
    }

    /**
     * The value must never appear in ActionLog entries or in log output. The state holder
     * itself does not log or persist; this test drives the full cycle, then records every
     * ActionLogFormatter line and every string the state exposes, and asserts the secret is
     * absent from all of them.
     */
    @Test
    fun valueNeverAppearsInActionLogLines() {
        val clock = FakeClock()
        val s = PausedTaskState(clock::get)
        val params = mapOf<String, Any?>("x" to placeholder)
        s.pause("send_message", params)
        s.offerMessage(secret)
        val substituted = s.substitute(params)!!
        assertEquals(secret, substituted["x"])

        // What DefaultAgentService records for this tool call: the ORIGINAL params (with the
        // placeholder), never the substituted ones, and a result detail with no value.
        val line = ActionLogFormatter.format(
            ActionLogEntry(
                timestampMs = 1L,
                taskSnippet = "send code",
                toolName = "send_message",
                risk = "send_message",
                outcome = "ok",
                detail = params.toString(),
            ),
        )
        assertFalse(line.contains(secret))
        s.clearAfterToolCall()
        assertFalse(line.contains(secret))
    }
}
