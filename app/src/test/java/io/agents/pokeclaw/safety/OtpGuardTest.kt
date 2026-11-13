// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpGuardTest {

    @Test
    fun hidesEnglishOtpNotification() {
        assertTrue(OtpGuard.isOtpBearing("Your OTP is 829311. Do not share it."))
        assertEquals(OtpGuard.HIDDEN_PLACEHOLDER, OtpGuard.filter("Verification code: 4471"))
    }

    @Test
    fun hidesArabicOtpNotification() {
        assertTrue(OtpGuard.isOtpBearing("رمز التحقق الخاص بك هو 556677"))
        assertEquals(OtpGuard.HIDDEN_PLACEHOLDER, OtpGuard.filter("كود التأكيد 90210"))
    }

    @Test
    fun keywordWithoutCodeIsNotHidden() {
        assertFalse(OtpGuard.isOtpBearing("Your verification code was sent, check the app"))
    }

    @Test
    fun codeWithoutKeywordIsNotHidden() {
        assertFalse(OtpGuard.isOtpBearing("Order 123456 has shipped"))
        assertEquals("Order 123456 has shipped", OtpGuard.filter("Order 123456 has shipped"))
    }

    @Test
    fun ordinaryMessagesPass() {
        assertEquals("Are we still on for lunch?", OtpGuard.filter("Are we still on for lunch?"))
        assertEquals("", OtpGuard.filter(""))
        assertEquals("", OtpGuard.filter(null))
    }

    @Test
    fun filterNeverReturnsTheCode() {
        val out = OtpGuard.filter("Your one-time password is 335577, expires in 5 min")
        assertFalse(out.contains("335577"))
    }
}
