// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownRenderTest {

    private fun plainText(markdown: String): String =
        markdownToSegments(markdown).joinToString("") { it.text }

    @Test
    fun plainTextPassesThrough() {
        assertEquals("hello world", plainText("hello world"))
        assertEquals("مرحبا بالعالم", plainText("مرحبا بالعالم"))
    }

    @Test
    fun boldProducesBoldSegment() {
        val segments = parseInlineMarkdown("a **bold** b")
        assertEquals("a ", segments[0].text)
        assertEquals("bold", segments[1].text)
        assertEquals(FontWeight.Bold, segments[1].style.fontWeight)
        assertEquals(" b", segments[2].text)
    }

    @Test
    fun underscoreBoldProducesBoldSegment() {
        val segments = parseInlineMarkdown("__bold__")
        assertEquals("bold", segments[0].text)
        assertEquals(FontWeight.Bold, segments[0].style.fontWeight)
    }

    @Test
    fun italicStarProducesItalicSegment() {
        val segments = parseInlineMarkdown("*italic*")
        assertEquals("italic", segments[0].text)
        assertEquals(FontStyle.Italic, segments[0].style.fontStyle)
    }

    @Test
    fun boldItalicProducesCombinedSegment() {
        val segments = parseInlineMarkdown("***both***")
        assertEquals("both", segments[0].text)
        assertEquals(FontWeight.Bold, segments[0].style.fontWeight)
        assertEquals(FontStyle.Italic, segments[0].style.fontStyle)
    }

    @Test
    fun snakeCaseIsNotItalicized() {
        val segments = parseInlineMarkdown("snake_case_name")
        assertEquals(1, segments.size)
        assertEquals("snake_case_name", segments[0].text)
        assertNull(segments[0].style.fontStyle)
    }

    @Test
    fun codeSpanIsMonospaceAndNotEmphasized() {
        val segments = parseInlineMarkdown("use `a_b_c` now")
        assertEquals("use ", segments[0].text)
        assertEquals("a_b_c", segments[1].text)
        assertNull(segments[1].style.fontStyle)
        assertEquals(" now", segments[2].text)
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertEquals("a * b", plainText("a * b"))
        assertEquals("**unclosed", plainText("**unclosed"))
    }

    @Test
    fun linkKeepsTextAndAnnotatesUrl() {
        val segments = parseInlineMarkdown("see [docs](https://example.com) here")
        assertEquals("docs", segments[1].text)
        assertEquals("https://example.com", segments[1].annotation)
        assertEquals(" here", segments[2].text)
    }

    @Test
    fun fencedCodeBlockBecomesSingleMonospaceSegment() {
        val segments = markdownToSegments("before\n```kotlin\nval x = 1\n**not bold**\n```\nafter")
        assertEquals("before", segments[0].text)
        val code = segments.first { it.text.contains("val x = 1") }
        assertEquals("val x = 1\n**not bold**", code.text)
        assertEquals("after", segments.last().text)
    }

    @Test
    fun headingIsFlattenedToBold() {
        val segments = markdownToSegments("## Title")
        assertEquals("Title", segments[0].text)
        assertEquals(FontWeight.Bold, segments[0].style.fontWeight)
    }

    @Test
    fun bulletMarkersNormalizeToUnicodeBullet() {
        assertEquals("• item", plainText("- item"))
        assertEquals("• item", plainText("* item"))
        assertEquals("1. item", plainText("1. item"))
    }

    @Test
    fun blockquoteMarkersAreStripped() {
        assertEquals("quoted text", plainText("> quoted text"))
    }

    @Test
    fun multilinePreservesNewlines() {
        val segments = markdownToSegments("line1\nline2")
        assertEquals("line1\nline2", segments.joinToString("") { it.text })
    }

    @Test
    fun arabicBoldRendersBoldSegment() {
        val segments = parseInlineMarkdown("**عربي**")
        assertEquals("عربي", segments[0].text)
        assertEquals(FontWeight.Bold, segments[0].style.fontWeight)
    }
}
