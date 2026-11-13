// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataRedactorTest {

    // Well-known Luhn-valid test PAN (Visa test number).
    private val luhnCard = "4111 1111 1111 1111"
    private val luhnCardCompact = "4111111111111111"
    // Well-known valid IBAN examples (mod-97 = 1).
    private val ibanGB = "GB82WEST12345698765432"
    private val ibanSA = "SA0380000000608010167519"

    @Test
    fun redactsLuhnValidCard() {
        val r = SensitiveDataRedactor.redact("card $luhnCard thanks")
        assertTrue(r.text.contains("[REDACTED:card]"))
        assertFalse(r.text.contains("4111"))
        assertEquals(1, r.counts[SensitiveDataRedactor.Kind.CARD])
    }

    @Test
    fun redactsCompactCard() {
        val r = SensitiveDataRedactor.redact(luhnCardCompact)
        assertEquals("[REDACTED:card]", r.text)
    }

    @Test
    fun doesNotRedactNonLuhnDigits() {
        // 16 digits, fails Luhn
        val r = SensitiveDataRedactor.redact("ref 1234567812345678")
        assertFalse(r.text.contains("[REDACTED:card]"))
        assertEquals(0, r.counts[SensitiveDataRedactor.Kind.CARD])
    }

    @Test
    fun luhnEdgeCases() {
        assertTrue(SensitiveDataRedactor.luhnValid("4111111111111111"))
        assertTrue(SensitiveDataRedactor.luhnValid("378282246310005")) // 15-digit Amex test
        assertFalse(SensitiveDataRedactor.luhnValid("4111111111111112"))
        assertFalse(SensitiveDataRedactor.luhnValid(""))
        assertFalse(SensitiveDataRedactor.luhnValid("41a1"))
    }

    @Test
    fun redactsValidIban() {
        val r = SensitiveDataRedactor.redact("pay to $ibanGB now")
        assertTrue(r.text.contains("[REDACTED:iban]"))
        assertFalse(r.text.contains("GB82"))
        assertEquals(1, r.counts[SensitiveDataRedactor.Kind.IBAN])
    }

    @Test
    fun redactsSaudiIban() {
        assertTrue(SensitiveDataRedactor.ibanValid(ibanSA))
        assertEquals("[REDACTED:iban]", SensitiveDataRedactor.redact(ibanSA).text)
    }

    @Test
    fun ibanMod97EdgeCases() {
        assertTrue(SensitiveDataRedactor.ibanValid(ibanGB))
        // Change one digit: checksum breaks.
        assertFalse(SensitiveDataRedactor.ibanValid("GB82WEST12345698765431"))
        assertFalse(SensitiveDataRedactor.ibanValid("GB82")) // too short
        assertFalse(SensitiveDataRedactor.ibanValid("1182WEST12345698765432")) // bad country
    }

    @Test
    fun redactsSaudiNationalId() {
        val r = SensitiveDataRedactor.redact("هويتي 1098765432 شكرا")
        assertTrue(r.text.contains("[REDACTED:national_id]"))
        assertEquals(1, r.counts[SensitiveDataRedactor.Kind.NATIONAL_ID])
    }

    @Test
    fun nationalIdMustStartWithOneOrTwo() {
        val r = SensitiveDataRedactor.redact("number 3098765432")
        assertEquals(0, r.counts[SensitiveDataRedactor.Kind.NATIONAL_ID])
    }

    @Test
    fun nationalIdMustBeTenDigits() {
        assertEquals(0, SensitiveDataRedactor.redact("12345678901").counts[SensitiveDataRedactor.Kind.NATIONAL_ID])
        assertEquals(0, SensitiveDataRedactor.redact("123456789").counts[SensitiveDataRedactor.Kind.NATIONAL_ID])
    }

    @Test
    fun redactsOtpNearEnglishKeyword() {
        val r = SensitiveDataRedactor.redact("Your verification code is 483921")
        assertTrue(r.text.contains("[REDACTED:otp]"))
        assertFalse(r.text.contains("483921"))
        assertEquals(1, r.counts[SensitiveDataRedactor.Kind.OTP])
    }

    @Test
    fun redactsOtpNearArabicKeyword() {
        val r = SensitiveDataRedactor.redact("رمز التحقق الخاص بك 7731")
        assertTrue(r.text.contains("[REDACTED:otp]"))
        assertFalse(r.text.contains("7731"))
    }

    @Test
    fun digitsWithoutKeywordAreNotOtp() {
        val r = SensitiveDataRedactor.redact("I have 4 apples and 2025 is the year")
        assertEquals(0, r.counts[SensitiveDataRedactor.Kind.OTP])
        assertEquals("I have 4 apples and 2025 is the year", r.text)
    }

    @Test
    fun otpDigitsOutsideWindowAreKept() {
        val filler = "x".repeat(80)
        val r = SensitiveDataRedactor.redact("otp $filler 552211")
        assertEquals(0, r.counts[SensitiveDataRedactor.Kind.OTP])
    }

    @Test
    fun emptyAndNullInputs() {
        assertEquals("", SensitiveDataRedactor.redact("").text)
        assertEquals("", SensitiveDataRedactor.redact(null).text)
        assertFalse(SensitiveDataRedactor.redact("hello").changed)
    }

    @Test
    fun multipleCategoriesInOneString() {
        val r = SensitiveDataRedactor.redact("card $luhnCard iban $ibanGB otp code 998877")
        assertEquals(1, r.counts[SensitiveDataRedactor.Kind.CARD])
        assertEquals(1, r.counts[SensitiveDataRedactor.Kind.IBAN])
        assertEquals(1, r.counts[SensitiveDataRedactor.Kind.OTP])
        assertEquals(
            setOf(SensitiveDataRedactor.Kind.CARD, SensitiveDataRedactor.Kind.IBAN, SensitiveDataRedactor.Kind.OTP),
            r.kinds,
        )
    }

    @Test
    fun redactedOutputContainsNoRawValues() {
        val input = "card $luhnCard iban $ibanGB"
        val out = SensitiveDataRedactor.redact(input).text
        assertFalse(out.contains("4111"))
        assertFalse(out.contains("GB82"))
    }
}
