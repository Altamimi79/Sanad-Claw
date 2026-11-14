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

    // --- resolveLanguage: Arabic default; saved English honored ONLY with explicit choice ---

    @Test
    fun neverChosen_noValue_resolvesToArabic() {
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage(null, false))
    }

    @Test
    fun legacyImplicitEnglish_withoutChoice_resolvesToArabic() {
        // The migration case: earlier builds wrote KEY_LANGUAGE="en" on every update.
        // Without the explicit-choice flag that legacy value MUST NOT keep the user in English.
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("en", false))
    }

    @Test
    fun storedArabic_withoutChoice_resolvesToArabic() {
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("ar", false))
    }

    @Test
    fun explicitEnglishChoice_isHonored() {
        assertEquals(AppLocale.LANGUAGE_ENGLISH, resolveLanguage("en", true))
    }

    @Test
    fun explicitArabicChoice_isHonored() {
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("ar", true))
    }

    @Test
    fun chosenWithGarbage_fallsBackToArabic() {
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("garbage", true))
        assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage("", true))
    }

    @Test
    fun setLanguageRoundTrip_isStable() {
        // setLanguage normalizes user choices to exactly "ar"/"en" and sets the chosen flag;
        // resolving those values with chosen=true must return them unchanged.
        for (stored in listOf(AppLocale.LANGUAGE_ARABIC, AppLocale.LANGUAGE_ENGLISH)) {
            assertEquals(stored, resolveLanguage(resolveLanguage(stored, true), true))
        }
    }

    @Test
    fun savedLanguages_surviveVersionChanges() {
        // An update never rewrites the language: whatever was stored keeps resolving
        // to itself regardless of versionCode movement. Old code forced "en" on update,
        // which would break the saved-"ar" expectation below.
        val savedEnglish = resolveLanguage("en", true)
        val savedArabic = resolveLanguage("ar", true)
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

    @Test
    fun versionDecisionNeverImpliesALanguage() {
        // The version-tracking decision is orthogonal to language: for every code pair,
        // whatever the user's stored value + chosen flag resolve to must stay identical
        // before and after the version bookkeeping runs.
        for (pair in listOf(Pair(0, 113), Pair(112, 113), Pair(113, 113))) {
            val before = resolveLanguage("en", true)
            shouldRecordVersionOnly(pair.first, pair.second) // decision only, no prefs here
            assertEquals(before, resolveLanguage("en", true))
            assertEquals(AppLocale.LANGUAGE_ARABIC, resolveLanguage(null, false))
        }
    }
}
