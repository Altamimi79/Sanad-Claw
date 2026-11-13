// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogFormatterTest {

    @Test
    fun masksApiKeysTokensPasswordsAndCardNumbers() {
        assertEquals(
            "key=***",
            ActionLogFormatter.sanitize("key=sk-abcdef123456"),
        )
        assertFalse(ActionLogFormatter.sanitize("token=abcdef123456").contains("abcdef123456"))
        assertFalse(ActionLogFormatter.sanitize("password: hunter2").contains("hunter2"))
        assertFalse(ActionLogFormatter.sanitize("Authorization: Bearer abc.def.ghi").contains("abc.def.ghi"))
        assertFalse(ActionLogFormatter.sanitize("card 4111111111111111").contains("4111111111111111"))
    }

    @Test
    fun actionLogNeverReceivesOtpOrIbanOrNationalIdValues() {
        val detail = "Your verification code is 483921, IBAN GB82WEST12345698765432, id 1098765432"
        val entry = ActionLogEntry(
            timestampMs = 1L,
            taskSnippet = "task",
            toolName = "get_notifications",
            risk = "none",
            outcome = "ok",
            detail = detail,
        )
        val line = ActionLogFormatter.format(entry)
        assertFalse(line.contains("483921"))
        assertFalse(line.contains("GB82WEST"))
        assertFalse(line.contains("1098765432"))
        assertTrue(line.contains("[REDACTED:otp]"))
    }

    @Test
    fun truncatesLongDetails() {
        val long = "x".repeat(400)
        val sanitized = ActionLogFormatter.sanitize(long, max = 20)
        assertEquals(20 + 1, sanitized.length)
        assertTrue(sanitized.endsWith("…"))
    }

    @Test
    fun formatAndParseRoundTrip() {
        val entry = ActionLogEntry(
            timestampMs = 1_700_000_000_000L,
            taskSnippet = "send hi to Mom",
            toolName = "send_message",
            risk = "send_message",
            outcome = "blocked",
            detail = "denied | by user",
        )
        val line = ActionLogFormatter.format(entry)
        val parsed = ActionLogFormatter.parse(line)
        assertEquals(entry.timestampMs, parsed?.timestampMs)
        assertEquals(entry.toolName, parsed?.toolName)
        assertEquals(entry.risk, parsed?.risk)
        assertEquals(entry.outcome, parsed?.outcome)
        assertEquals("denied | by user", parsed?.detail)
    }

    @Test
    fun malformedLinesAreIgnored() {
        assertNull(ActionLogFormatter.parse("not-a-log-line"))
        assertNull(ActionLogFormatter.parse("123 | only | four"))
    }
}
