// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogV2Test {

    @Test
    fun v2LineRoundTripsAllFields() {
        val entry = ActionLogEntryV2(
            timestampMs = 1_700_000_000_000L,
            taskSnippet = "send hi to Mom",
            toolName = "send_message",
            risk = "send_message",
            reason = ActionReason.SAFETY_DENIED,
            result = ActionResult.DENIED,
            detail = "user said no",
        )
        val line = ActionLogFormatter.formatV2(entry)
        assertTrue(line.startsWith("v2 | 1700000000000 | "))
        assertEquals(8, line.split(" | ").size)
        assertEquals(entry, ActionLogFormatter.parseV2(line))
    }

    @Test
    fun v1LineParsesAsUnknownLegacyWithoutCrashing() {
        val v1 = "1700000000000 | task | get_time | none | ok | done"
        val parsed = ActionLogFormatter.parse(v1)
        assertEquals(1_700_000_000_000L, parsed?.timestampMs)
        assertEquals("ok", parsed?.outcome)
        assertEquals(ActionResult.SUCCESS, ActionLog.legacyResultOrNull("ok"))
        assertNull(ActionLog.legacyResultOrNull("something-new"))
        // Not crashing on a v1 line with an unknown outcome word.
        assertEquals("weird", ActionLogFormatter.parse("1 | t | x | none | weird | d")?.outcome)
    }

    @Test
    fun mixedV1AndV2LinesReadTogetherInOrder() {
        val v1 = "1000 | t1 | tool_a | none | ok | first"
        val v2 = ActionLogFormatter.formatV2(
            ActionLogEntryV2(
                timestampMs = 2000L,
                taskSnippet = "t2",
                toolName = "tool_b",
                risk = "none",
                reason = ActionReason.COMPLETED,
                result = ActionResult.SUCCESS,
                detail = "second",
            ),
        )
        val v1b = "3000 | t3 | tool_c | none | error | third"
        val lines = listOf(v1, v2, v1b)
        val entries = lines.map { line ->
            if (line.startsWith("v2 | ")) {
                val v = ActionLogFormatter.parseV2(line)!!
                ActionLogReadEntry(v.timestampMs, v.taskSnippet, v.toolName, v.risk, v.reason, v.result, v.detail)
            } else {
                val p = ActionLogFormatter.parse(line)!!
                ActionLogReadEntry(
                    p.timestampMs, p.taskSnippet, p.toolName, p.risk,
                    ActionReason.UNKNOWN_LEGACY, ActionLog.legacyResultOrNull(p.outcome), p.detail,
                )
            }
        }
        assertEquals(listOf(1000L, 2000L, 3000L), entries.map { it.timestampMs })
        assertEquals(ActionReason.UNKNOWN_LEGACY, entries[0].reason)
        assertEquals(ActionReason.COMPLETED, entries[1].reason)
        assertEquals(ActionResult.SUCCESS, entries[1].result)
        // "error" maps to FAILED via the legacy mapping; the v1 line keeps its own outcome word.
        assertEquals(ActionResult.FAILED, entries[2].result)
    }

    @Test
    fun reasonAndResultAreKeysNotFreeText() {
        // A line whose reason or result key is outside the fixed enums is rejected, not stored.
        val badReason = "v2 | 1 | t | tool | none | I_MADE_THIS_UP | SUCCESS | d"
        val badResult = "v2 | 1 | t | tool | none | COMPLETED | good_job | d"
        assertNull(ActionLogFormatter.parseV2(badReason))
        assertNull(ActionLogFormatter.parseV2(badResult))
        // formatV2 only writes enum names, so free text cannot reach the stored key fields.
        val line = ActionLogFormatter.formatV2(
            ActionLogEntryV2(1L, "t", "tool", "none", ActionReason.TIMEOUT, ActionResult.CANCELLED, "x"),
        )
        assertTrue(line.contains(" | TIMEOUT | CANCELLED | "))
        assertFalse(line.contains("made up"))
    }

    @Test
    fun noRawSecretReachesStoredLine() {
        // Canary: must never appear in a stored line, even when passed as task or detail.
        val canary = "CANARY_LOG_8e4b"
        val line = ActionLogFormatter.formatV2(
            ActionLogEntryV2(
                timestampMs = 1L,
                taskSnippet = "password=$canary",
                toolName = "send_message",
                risk = "send_message",
                reason = ActionReason.USER_REQUESTED,
                result = ActionResult.SUCCESS,
                detail = "token: $canary and card 4111111111111111",
            ),
        )
        assertFalse(line.contains(canary))
        assertFalse(line.contains("4111111111111111"))
    }
}
