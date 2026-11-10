// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePolicyTest {

    @Test
    fun apply_appendsGulfArabicPolicy() {
        val prompt = LanguagePolicy.apply("You are a helpful assistant.")
        assertTrue(prompt.startsWith("You are a helpful assistant."))
        assertTrue(prompt.contains(LanguagePolicy.HEADER))
        assertTrue(prompt.contains("Gulf/Khaleeji"))
        assertTrue(prompt.contains("Modern Standard Arabic"))
    }

    @Test
    fun compose_withoutGlobalPrompt_equalsApply() {
        assertEquals(
            LanguagePolicy.apply("base"),
            LanguagePolicy.compose("base", "  ", "\n\n---\n\n", "User's persistent global instructions:"),
        )
    }

    @Test
    fun compose_placesUserGlobalInstructionsLastSoTheyCanOverridePolicy() {
        val result = LanguagePolicy.compose(
            basePrompt = "base",
            globalPrompt = "Always reply in French.",
            separator = "\n\n---\n\n",
            prefixHeader = "User's persistent global instructions:",
        )
        val policyIndex = result.indexOf(LanguagePolicy.HEADER)
        val globalIndex = result.indexOf("User's persistent global instructions:")
        assertTrue(policyIndex >= 0)
        assertTrue(globalIndex > policyIndex)
        assertTrue(result.endsWith("Always reply in French."))
    }

    @Test
    fun policy_keepsToolNamesUntranslated() {
        assertTrue(LanguagePolicy.POLICY_TEXT.contains("Keep tool names"))
    }
}
