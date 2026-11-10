// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlocklistTest {

    @Test
    fun defaultsCoverBankingAndPasswordApps() {
        assertTrue(AppBlocklist.DEFAULT_PATTERNS.any { it.contains("bank") })
        assertTrue(AppBlocklist.DEFAULT_PATTERNS.any { it.contains("password") })
        assertTrue(AppBlocklist.DEFAULT_PATTERNS.any { it.contains("بنك") })
    }

    @Test
    fun packageNameMatchesAreBlocked() {
        val patterns = AppBlocklist.effectivePatterns("")
        assertTrue(AppBlocklist.isBlocked(patterns, "com.bank.xyz"))
        assertTrue(AppBlocklist.isBlocked(patterns, "com.bitwarden.passwordmanager"))
        assertFalse(AppBlocklist.isBlocked(patterns, "com.whatsapp"))
        assertFalse(AppBlocklist.isBlocked(patterns, "com.google.android.youtube"))
    }

    @Test
    fun appLabelMatchesAreBlocked() {
        val patterns = AppBlocklist.effectivePatterns("")
        assertTrue(AppBlocklist.isBlocked(patterns, "com.example.app", appLabel = "البنك الأهلي"))
        assertTrue(AppBlocklist.isBlocked(patterns, "com.unknown", appLabel = "My Password Vault"))
        assertFalse(AppBlocklist.isBlocked(patterns, "com.unknown", appLabel = "Chat App"))
    }

    @Test
    fun passwordScreenFallbackNeedsTwoMarkers() {
        val patterns = listOf("nothing-matches")
        assertTrue(
            AppBlocklist.isBlocked(
                patterns, "com.unlisted",
                screenText = "Current password\nNew password",
            )
        )
        assertFalse(
            AppBlocklist.isBlocked(
                patterns, "com.unlisted",
                screenText = "This article explains password hygiene",
            )
        )
    }

    @Test
    fun parseAndStorageRoundTrip() {
        val parsed = AppBlocklist.parse("bank, wallet; password\ncustom")
        assertEquals(listOf("bank", "wallet", "password", "custom"), parsed)
        val stored = AppBlocklist.toStorage(listOf("bank", "wallet", "bank"))
        assertEquals("bank, wallet", stored)
        assertTrue(AppBlocklist.parse(stored).containsAll(listOf("bank", "wallet")))
    }

    @Test
    fun userProvidedPatternsReplaceDefaults() {
        assertEquals(listOf("mybank"), AppBlocklist.effectivePatterns("mybank"))
        assertEquals(AppBlocklist.DEFAULT_PATTERNS, AppBlocklist.effectivePatterns("  "))
    }
}
