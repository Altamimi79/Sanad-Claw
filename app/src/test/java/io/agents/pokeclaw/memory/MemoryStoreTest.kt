// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreTest {

    @Test
    fun normalizeText_trimsAndCollapsesWhitespace() {
        assertEquals("likes black coffee", MemoryStore.normalizeText("  likes   black\n coffee  "))
    }

    @Test
    fun normalizeText_blankIsRejected() {
        assertNull(MemoryStore.normalizeText("   \n\t "))
    }

    @Test
    fun normalizeText_capsLength() {
        val long = "a".repeat(MemoryStore.MAX_TEXT_LENGTH + 40)
        assertEquals(MemoryStore.MAX_TEXT_LENGTH, MemoryStore.normalizeText(long)?.length)
    }

    @Test
    fun parse_validJson_returnsEntries() {
        val json = """[{"id":"1","text":"My car is white","createdAt":10}]"""
        val entries = MemoryStore.parse(json)
        assertEquals(1, entries.size)
        assertEquals("My car is white", entries[0].text)
        assertEquals(10L, entries[0].createdAt)
    }

    @Test
    fun parse_malformedJson_returnsEmptyList() {
        assertTrue(MemoryStore.parse("{not json").isEmpty())
    }

    @Test
    fun toEntry_skipsBlankIdOrText() {
        assertNull(MemoryStore.toEntry(MemoryStore.Stored(id = "", text = "x", createdAt = 1)))
        assertNull(MemoryStore.toEntry(MemoryStore.Stored(id = "1", text = "   ", createdAt = 1)))
    }

    @Test
    fun parse_nullFields_areSkippedWithoutCrash() {
        val json = """[{"id":null,"text":"x","createdAt":1},{"id":"2","text":null,"createdAt":2}]"""
        assertTrue(MemoryStore.parse(json).isEmpty())
    }

    @Test
    fun parse_missingCreatedAt_defaultsToZero() {
        val json = """[{"id":"1","text":"hello"}]"""
        assertEquals(0L, MemoryStore.parse(json).first().createdAt)
    }

    @Test
    fun parse_legacyMinifiedKeys_areStillReadable() {
        val json = """[{"a":"1","b":"My name is Ahmed","c":7}]"""
        val entries = MemoryStore.parse(json)
        assertEquals(1, entries.size)
        assertEquals("My name is Ahmed", entries[0].text)
    }

    @Test
    fun formatPrompt_emptyForNoEntries() {
        assertEquals("", MemoryStore.formatPrompt(emptyList()))
    }

    @Test
    fun formatPrompt_oneBulletPerEntry() {
        val entries = listOf(
            MemoryStore.Entry("1", "Prefers Arabic", 1),
            MemoryStore.Entry("2", "Lives in Riyadh", 2),
        )
        assertEquals("- Prefers Arabic\n- Lives in Riyadh", MemoryStore.formatPrompt(entries))
    }
}
