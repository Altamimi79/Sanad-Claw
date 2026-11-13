// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogExportTest {

    private fun entry(
        ts: Long,
        task: String,
        detail: String,
        reason: ActionReason = ActionReason.COMPLETED,
        result: ActionResult? = ActionResult.SUCCESS,
    ) = ActionLogReadEntry(ts, task, "tool_$ts", "none", reason, result, detail)

    @Test
    fun exportContainsEntriesInOrder() {
        val out = ActionLogFormatter.buildExport(
            listOf(
                entry(1L, "first task", "one"),
                entry(2L, "second task", "two"),
                entry(3L, "third task", "three"),
            ),
        )
        val lines = out.split("\n")
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("first task"))
        assertTrue(lines[1].contains("second task"))
        assertTrue(lines[2].contains("third task"))
        assertTrue(lines[0].startsWith("v2 | 1 | "))
    }

    @Test
    fun exportRedactsCardIbanAndOtpValues() {
        val card = "4111111111111111"
        val iban = "GB82WEST12345698765432"
        val otp = "483921"
        val out = ActionLogFormatter.buildExport(
            listOf(
                entry(
                    1L,
                    "pay CANARY_CARD_$card",
                    "card $card, IBAN $iban, Your verification code is $otp",
                ),
            ),
        )
        assertFalse(out.contains(card))
        assertFalse(out.contains("GB82WEST"))
        assertFalse(out.contains(otp))
        assertTrue(out.contains("[REDACTED:card]") || out.contains("***"))
    }

    @Test
    fun exportNeverContainsUnredactedDetail() {
        val canary = "CANARY_EXPORT_93ac"
        val out = ActionLogFormatter.buildExport(
            listOf(entry(1L, "t", "token=$canary and bearer abc.def.ghi")),
        )
        assertFalse(out.contains(canary))
        assertFalse(out.contains("abc.def.ghi"))
    }

    @Test
    fun exportOfEmptyLogIsValidEmptyDocument() {
        assertEquals("", ActionLogFormatter.buildExport(emptyList()))
    }

    @Test
    fun exportIsIdempotent() {
        val entries = listOf(
            entry(1L, "a", "x", ActionReason.TIMEOUT, ActionResult.FAILED),
            entry(2L, "b", "y", ActionReason.EMERGENCY_STOP, ActionResult.CANCELLED),
        )
        assertEquals(
            ActionLogFormatter.buildExport(entries),
            ActionLogFormatter.buildExport(entries),
        )
    }

    @Test
    fun labelsLocalizeWithLanguageKey() {
        assertEquals("Timed out", ActionLogLabels.reasonLabel(ActionReason.TIMEOUT, "en"))
        assertEquals("انتهت المهلة", ActionLogLabels.reasonLabel(ActionReason.TIMEOUT, "ar"))
        assertEquals("Success", ActionLogLabels.resultLabel(ActionResult.SUCCESS, "en"))
        assertEquals("نجح", ActionLogLabels.resultLabel(ActionResult.SUCCESS, "ar"))
        assertEquals("Unknown", ActionLogLabels.resultLabel(null, "en"))
        assertEquals("غير معروف", ActionLogLabels.resultLabel(null, "ar"))
    }
}
