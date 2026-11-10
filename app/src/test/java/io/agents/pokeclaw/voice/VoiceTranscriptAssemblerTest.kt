// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTranscriptAssemblerTest {

    @Test
    fun startsEmpty() {
        val parser = VoiceTextNormalizer.normalize("")
        val assembler = VoiceTranscriptAssembler()
        assertFalse(assembler.hasText())
        assertEquals("", assembler.current())
        assertEquals("", parser)
    }

    @Test
    fun partialsReplaceEachOther() {
        val assembler = VoiceTranscriptAssembler()
        assembler.onPartial("مرحبا")
        assembler.onPartial("مرحبا بالعالم")
        assertEquals("مرحبا بالعالم", assembler.current())
    }

    @Test
    fun finalReplacesPendingPartial() {
        val assembler = VoiceTranscriptAssembler()
        assembler.onPartial("مرحبا بال")
        assembler.onFinal("مرحبا بالعالم")
        assertEquals("مرحبا بالعالم", assembler.current())
    }

    @Test
    fun multipleFinalsAreJoinedWithSingleSpace() {
        val assembler = VoiceTranscriptAssembler()
        assembler.onFinal("السلام")
        assembler.onFinal("  عليكم  ")
        assertEquals("السلام عليكم", assembler.current())
    }

    @Test
    fun blankInputsAreIgnored() {
        val assembler = VoiceTranscriptAssembler()
        assembler.onPartial("   ")
        assembler.onFinal(null)
        assertFalse(assembler.hasText())
    }

    @Test
    fun resetClearsEverything() {
        val assembler = VoiceTranscriptAssembler()
        assembler.onFinal("مرحبا")
        assembler.reset()
        assertFalse(assembler.hasText())
        assertEquals("", assembler.current())
    }

    @Test
    fun transcriptIsNormalized() {
        val assembler = VoiceTranscriptAssembler()
        assembler.onFinal("  كيف   حالك؟ \n")
        assertEquals("كيف حالك؟", assembler.current())
        assertTrue(assembler.hasText())
    }
}
