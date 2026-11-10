// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextNormalizerTest {

    @Test
    fun nullAndBlankBecomeEmpty() {
        assertEquals("", VoiceTextNormalizer.normalize(null))
        assertEquals("", VoiceTextNormalizer.normalize(""))
        assertEquals("", VoiceTextNormalizer.normalize("   \n\t "))
    }

    @Test
    fun collapsesWhitespaceAndTrims() {
        assertEquals("مرحبا بالعالم", VoiceTextNormalizer.normalize("  مرحبا   بالعالم \n"))
        assertEquals("hello world", VoiceTextNormalizer.normalize("hello\t\tworld"))
    }

    @Test
    fun stripsInvisibleBidiMarks() {
        assertEquals("مرحبا", VoiceTextNormalizer.normalize("مر\u200Fح\u200Eبا\uFEFF"))
    }

    @Test
    fun keepsArabicPunctuation() {
        assertEquals("كيف حالك؟ بخير، شكرًا.", VoiceTextNormalizer.normalize("كيف حالك؟ بخير، شكرًا."))
    }

    @Test
    fun truncatesToMaxLengthWithoutTrailingSpace() {
        // "مرحبا بالعالم" cut at 6 chars keeps the trailing space, which trimEnd removes.
        assertEquals("مرحبا", VoiceTextNormalizer.normalize("مرحبا بالعالم", maxLength = 6))
        assertEquals("مرحبا ب", VoiceTextNormalizer.normalize("مرحبا بالعالم", maxLength = 7))
    }
}
