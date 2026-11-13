// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the text that would be placed in the cloud request body.
 *
 * Production path for tool output: DefaultAgentService wraps the result with
 * ScreenContentGuard.wrap, then applies SensitiveDataRedactor.redact, then adds it as a
 * ToolExecutionResultMessage. The HTTP transport is not exercised here: the request
 * body is built from these message strings by LangChain4j, so asserting on the strings
 * covers every field that can carry screen content.
 */
class CloudPayloadTest {

    private val card = "4111 1111 1111 1111"
    private val iban = "GB82WEST12345698765432"
    private val nationalId = "1098765432"
    private val otp = "483921"

    /** Same two steps DefaultAgentService applies to each tool result before the message is built. */
    private fun outgoingToolResult(rawScreen: String): String {
        val guard = ScreenContentGuard()
        val wrapped = guard.wrap("tool_get_screen_info", rawScreen).text
        return SensitiveDataRedactor.redact(wrapped).text
    }

    @Test
    fun outgoingToolResultContainsNoCardIbanNationalIdOrOtp() {
        val raw = "Card $card, IBAN $iban, id $nationalId, your verification code is $otp"
        val payload = outgoingToolResult(raw)

        // Exact assertions on every sensitive value.
        assertFalse("card number leaked", payload.contains("4111"))
        assertFalse("card number leaked (compact)", payload.contains("4111111111111111"))
        assertFalse("IBAN leaked", payload.contains("GB82WEST"))
        assertFalse("national ID leaked", payload.contains(nationalId))
        assertFalse("OTP leaked", payload.contains(otp))

        // Placeholders are present, so the model knows something was removed.
        assertTrue(payload.contains("[REDACTED:card]"))
        assertTrue(payload.contains("[REDACTED:iban]"))
        assertTrue(payload.contains("[REDACTED:national_id]"))
        assertTrue(payload.contains("[REDACTED:otp]"))
    }

    @Test
    fun payloadFieldsCheckedAreTheToolResultText() {
        // Fields carried in a tool result message: the text body (the only string field
        // LangChain4j serializes for ToolExecutionResultMessage). Tool name and id are
        // fixed identifiers and are not screen content.
        val payload = outgoingToolResult("Notification: code is $otp")
        assertFalse(payload.contains(otp))
    }

    @Test
    fun benignScreenTextPassesThroughUnchanged() {
        val payload = outgoingToolResult("Battery is at 73%")
        assertTrue(payload.contains("Battery is at 73%"))
    }
}
