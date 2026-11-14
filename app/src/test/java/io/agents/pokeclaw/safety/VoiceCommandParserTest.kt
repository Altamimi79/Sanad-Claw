// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import io.agents.pokeclaw.safety.VoiceCommandParser.Command
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun stopArabicIsRecognized() {
        assertEquals(Command.STOP, VoiceCommandParser.parse("توقف سند"))
    }

    @Test
    fun stopReversedArabicIsRecognized() {
        assertEquals(Command.STOP, VoiceCommandParser.parse("سند توقف"))
    }

    @Test
    fun stopEnglishIsRecognized() {
        assertEquals(Command.STOP, VoiceCommandParser.parse("stop"))
    }

    @Test
    fun confirmArabicIsRecognized() {
        assertEquals(Command.CONFIRM, VoiceCommandParser.parse("نعم سند"))
    }

    @Test
    fun confirmEnglishIsRecognized() {
        assertEquals(Command.CONFIRM, VoiceCommandParser.parse("confirm"))
    }

    @Test
    fun diacriticsAndPunctuationIgnored() {
        assertEquals(Command.STOP, VoiceCommandParser.parse("تَوَقّف، سند"))
        assertEquals(Command.CONFIRM, VoiceCommandParser.parse("  Confirm!  "))
    }

    @Test
    fun unrelatedSpeechReturnsNone() {
        assertEquals(Command.NONE, VoiceCommandParser.parse("افتح الإعدادات"))
    }

    @Test
    fun substringOfCommandInLongSentenceReturnsNone() {
        assertEquals(Command.NONE, VoiceCommandParser.parse("لا تتوقف سند عن العمل"))
    }

    @Test
    fun emptyAndNullReturnNone() {
        assertEquals(Command.NONE, VoiceCommandParser.parse(null))
        assertEquals(Command.NONE, VoiceCommandParser.parse(""))
        assertEquals(Command.NONE, VoiceCommandParser.parse("   "))
    }
}
