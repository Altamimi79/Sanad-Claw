// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Minimal, dependency-free Markdown renderer for chat bubbles (phase 2 chat upgrade).
 * Supports: fenced code blocks, inline code, bold, italic, bold+italic, links,
 * headings (flattened to bold), blockquotes, bullet/numbered lists.
 * Unsupported constructs (tables, images) degrade to readable plain text.
 */

private const val ANNOTATION_LINK = "link"

internal data class MdSegment(
    val text: String,
    val style: SpanStyle = SpanStyle(),
    val annotation: String? = null,
)

private val HEADING_PATTERN = Regex("""^\s*#{1,6}\s+""")
private val BULLET_PATTERN = Regex("""^\s*[*+-]\s+""")
private val HR_PATTERN = Regex("""^\s*([-*_]\s*){3,}$""")
private val LINK_PATTERN = Regex("""\[([^]\n]+)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
private val BOLD_ITALIC_PATTERN = Regex("""(\*\*\*|___)(.+?)\1""", RegexOption.DOT_MATCHES_ALL)
private val BOLD_PATTERN = Regex("""(\*\*|__)(.+?)\1""", RegexOption.DOT_MATCHES_ALL)
private val ITALIC_STAR_PATTERN = Regex("""\*([^*\n]+)\*""")
private val ITALIC_UNDERSCORE_PATTERN = Regex("""(?<!\w)_([^_\n]+)_(?!\w)""")

/**
 * Pure inline parser (unit-testable). Code spans are extracted first and never re-parsed.
 */
internal fun parseInlineMarkdown(text: String): List<MdSegment> {
    if (text.isEmpty()) return emptyList()

    data class Plain(val text: String, val isCode: Boolean)

    val plains = mutableListOf<Plain>()
    var i = 0
    val sb = StringBuilder()
    while (i < text.length) {
        if (text[i] == '`') {
            val close = text.indexOf('`', i + 1)
            if (close > i) {
                if (sb.isNotEmpty()) {
                    plains.add(Plain(sb.toString(), isCode = false))
                    sb.setLength(0)
                }
                plains.add(Plain(text.substring(i + 1, close), isCode = true))
                i = close + 1
                continue
            }
        }
        sb.append(text[i])
        i++
    }
    if (sb.isNotEmpty()) plains.add(Plain(sb.toString(), isCode = false))

    val segments = mutableListOf<MdSegment>()
    for (plain in plains) {
        if (plain.isCode) {
            segments.add(MdSegment(plain.text, SpanStyle(fontFamily = FontFamily.Monospace)))
        } else {
            segments.addAll(parseEmphasis(plain.text))
        }
    }
    return segments
}

private fun parseEmphasis(text: String): List<MdSegment> {
    if (text.isEmpty()) return emptyList()

    fun wrap(range: IntRange, markerLen: Int, style: SpanStyle): List<MdSegment>? {
        val inner = text.substring(range.first + markerLen, range.last - markerLen + 1)
        if (inner.isEmpty()) return null
        val prefix = text.take(range.first)
        val suffixStart = range.last + 1
        val out = mutableListOf<MdSegment>()
        if (prefix.isNotEmpty()) out.addAll(parseEmphasis(prefix))
        for (seg in parseEmphasis(inner)) {
            out.add(MdSegment(seg.text, seg.style.merge(style), seg.annotation))
        }
        if (suffixStart < text.length) out.addAll(parseEmphasis(text.substring(suffixStart)))
        return out
    }

    BOLD_ITALIC_PATTERN.find(text)?.let { m ->
        wrap(m.range, 3, SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))?.let { return it }
    }
    BOLD_PATTERN.find(text)?.let { m ->
        wrap(m.range, 2, SpanStyle(fontWeight = FontWeight.Bold))?.let { return it }
    }
    ITALIC_STAR_PATTERN.find(text)?.let { m ->
        wrap(m.range, 1, SpanStyle(fontStyle = FontStyle.Italic))?.let { return it }
    }
    ITALIC_UNDERSCORE_PATTERN.find(text)?.let { m ->
        wrap(m.range, 1, SpanStyle(fontStyle = FontStyle.Italic))?.let { return it }
    }
    LINK_PATTERN.find(text)?.let { m ->
        val linkText = m.groupValues[1]
        val url = m.groupValues[2]
        val prefix = text.take(m.range.first)
        val suffixStart = m.range.last + 1
        val out = mutableListOf<MdSegment>()
        if (prefix.isNotEmpty()) out.addAll(parseEmphasis(prefix))
        out.add(
            MdSegment(
                linkText,
                SpanStyle(textDecoration = TextDecoration.Underline),
                annotation = url,
            )
        )
        if (suffixStart < text.length) out.addAll(parseEmphasis(text.substring(suffixStart)))
        return out
    }
    return listOf(MdSegment(text))
}

/**
 * Block-level Markdown parsing to styled segments. Pure JVM, unit-testable.
 */
internal fun markdownToSegments(markdown: String): List<MdSegment> {
    val out = mutableListOf<MdSegment>()
    val lines = markdown.split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            val fenceLang = line.trimStart().removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing fence
            var codeText = code.toString().removeSuffix("\n")
            if (codeText.isEmpty() && fenceLang.isNotEmpty()) codeText = fenceLang
            if (codeText.isNotEmpty()) {
                out.add(MdSegment(codeText, SpanStyle(fontFamily = FontFamily.Monospace)))
                out.add(MdSegment("\n"))
            }
            continue
        }

        var working = line
        var headingStyle = SpanStyle()
        val headingMatch = HEADING_PATTERN.find(working)
        if (headingMatch != null) {
            working = working.removeRange(headingMatch.range)
            headingStyle = headingStyle.copy(fontWeight = FontWeight.Bold)
        }
        while (working.trimStart().startsWith("> ")) working = working.trimStart().removePrefix("> ")
        if (working.trim() == ">") working = ""

        val bulletMatch = BULLET_PATTERN.find(working)
        if (bulletMatch != null) {
            working = "•" + working.substring(bulletMatch.range.last)
        }

        if (HR_PATTERN.matches(working)) {
            out.add(MdSegment("──────────\n"))
            continue
        }

        for (seg in parseInlineMarkdown(working)) {
            out.add(MdSegment(seg.text, seg.style.merge(headingStyle), seg.annotation))
        }
        out.add(MdSegment("\n"))
        i++
    }
    while (out.isNotEmpty() && out.last().text == "\n") out.removeAt(out.lastIndex)
    return out
}

/**
 * Markdown → AnnotatedString for Compose Text rendering.
 */
fun markdownToAnnotatedString(markdown: String, baseColor: Color = Color.Unspecified): AnnotatedString {
    val segments = markdownToSegments(markdown)
    return buildAnnotatedString {
        for (seg in segments) {
            if (seg.annotation != null) pushStringAnnotation(ANNOTATION_LINK, seg.annotation)
            withStyle(seg.style.copy(color = baseColor)) { append(seg.text) }
            if (seg.annotation != null) pop()
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val annotated = buildAnnotatedString {
        for (seg in markdownToSegments(markdown)) {
            val annotation = seg.annotation
            if (annotation != null) {
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = ANNOTATION_LINK,
                        linkInteractionListener = { uriHandler.openUri(annotation) },
                    )
                )
                withStyle(seg.style.copy(color = color)) { append(seg.text) }
                pop()
            } else {
                withStyle(seg.style.copy(color = color)) { append(seg.text) }
            }
        }
    }
    BasicText(text = annotated, style = style, modifier = modifier)
}
