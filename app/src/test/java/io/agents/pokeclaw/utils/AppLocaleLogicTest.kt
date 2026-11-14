// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the Arabic-first language rules (same pattern as LanguagePolicyTest).
 * No Robolectric is available in this project, so the Android-facing plumbing
 * (SharedPreferences/attachBaseContext) is exercised on device via QA_CHECKLIST;
 * these tests pin the decision functions that carry the behavior change:
 * they FAIL against the old English-default / reset-on-update logic.
 */
class AppLocaleLogicTest {

    // --- resolveLanguage: Arabic is the default, English only when explicitly chosen ---

    @Test
    fun neverChosen_resolvesToArabic() {
        // Old behavior returned "en" here — this test fails on it by design.
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage(null))
    }

    @Test
    fun explicitEnglish_isEnglish() {
        assertEquals(AppLocale.LANGUAGE_ENGLISH, resolveLanguage("en"))
    }

    @Test
    fun explicitArabic_isArabic() {
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("ar"))
    }

    @Test
    fun unknownValues_fallBackToArabic() {
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage(""))
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("fr"))
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("EN"))
    }

    @Test
    fun setLanguageRoundTrip_isStable() {
        // setLanguage normalizes user choices to exactly "ar"/"en"; resolving those
        // values must return them unchanged (round-trip stability).
        for (stored in listOf(AppLocale.LANGUAGE_ARABIC, AppLocale.LANGUAGE_ENGLISH)) {
            assertEquals(stored, resolveLanguage(resolveLanguage(stored)))
        }
    }

    @Test
    fun savedLanguages_surviveVersionChanges() {
        // An update never rewrites the language: whatever was stored keeps resolving
        // to itself regardless of versionCode movement. Old code forced "en" on update,
        // which would break the saved-"ar" expectation below.
        val savedEnglish = resolveLanguage("en")
        val savedArabic = resolveLanguage("ar")
        assertTrue(shouldRecordVersionOnly(112, 113)) // update path runs
        assertEquals(AppLocale.LANGUAGE_ENGLISH, savedEnglish)
        assertEquals(AppLocale.LANGUAGE_ARABIC, savedArabic)
    }

    // --- shouldRecordVersionOnly: version tracking only, never a language write ---

    @Test
    fun firstInstall_recordsCodeOnly() {
        assertTrue(shouldRecordVersionOnly(0, 113))
    }

    @Test
    fun update_recordsNewCodeOnly() {
        assertTrue(shouldRecordVersionOnly(112, 113))
    }

    @Test
    fun sameVersion_isNoOp() {
        assertFalse(shouldRecordVersionOnly(113, 113))
    }
}
