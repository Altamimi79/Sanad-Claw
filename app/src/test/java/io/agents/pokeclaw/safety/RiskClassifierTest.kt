// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskClassifierTest {

    @Test
    fun messagingCallingAndFilesRequireConfirmation() {
        assertEquals(RiskKind.SEND_MESSAGE, RiskClassifier.assess("send_message", mapOf("contact" to "Mom", "message" to "hi")).kind)
        assertEquals(RiskKind.CALL, RiskClassifier.assess("make_call", mapOf("contact" to "Mom")).kind)
        assertEquals(RiskKind.SEND_FILE, RiskClassifier.assess("send_file", mapOf("path" to "/sdcard/a.pdf")).kind)
        assertTrue(RiskClassifier.assess("send_message", emptyMap()).requiresConfirmation)
    }

    @Test
    fun paymentTextInAnyToolIsRisky() {
        val english = RiskClassifier.assess("input_text", mapOf("text" to "transfer 500 to Ali"))
        assertEquals(RiskKind.PAYMENT, english.kind)
        assertTrue(english.requiresConfirmation)
        val arabic = RiskClassifier.assess("input_text", mapOf("text" to "دفع الفاتورة"))
        assertEquals(RiskKind.PAYMENT, arabic.kind)
    }

    @Test
    fun deleteTextIsRisky() {
        assertEquals(RiskKind.DELETE, RiskClassifier.assess("tap", mapOf("text" to "Delete all")).kind)
        assertEquals(RiskKind.DELETE, RiskClassifier.assess("tap_node", mapOf("text" to "حذف المحادثة")).kind)
    }

    @Test
    fun storeInstallIsRisky() {
        assertEquals(RiskKind.INSTALL, RiskClassifier.assess("search_app_in_store", mapOf("query" to "WhatsApp")).kind)
        assertEquals(RiskKind.INSTALL, RiskClassifier.assess("open_app", mapOf("query" to "install Telegram")).kind)
    }

    @Test
    fun publishingIsRiskyOnlyForInputTools() {
        assertEquals(RiskKind.PUBLISH, RiskClassifier.assess("input_text", mapOf("text" to "post this now")).kind)
        assertFalse(RiskClassifier.assess("get_screen_info", mapOf("text" to "post this now")).risky)
    }

    @Test
    fun normalActionsAreNotRisky() {
        assertFalse(RiskClassifier.assess("get_screen_info", emptyMap()).risky)
        assertFalse(RiskClassifier.assess("swipe", mapOf("direction" to "up")).risky)
        assertFalse(RiskClassifier.assess("wait", mapOf("ms" to 500)).risky)
    }

    @Test
    fun finishSummaryIsNeverRisky() {
        val summary = "The page mentions transfer 500 dollars and delete all photos."
        assertFalse(RiskClassifier.assess("finish", mapOf("summary" to summary)).risky)
    }

    @Test
    fun labelForIsStable() {
        assertEquals("send_message", RiskClassifier.labelFor(RiskKind.SEND_MESSAGE))
        assertEquals("payment", RiskClassifier.labelFor(RiskKind.PAYMENT))
        assertEquals("none", RiskClassifier.labelFor(null))
    }
}
