// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

/**
 * Detects instruction-like text inside untrusted content (screen, notifications,
 * messages, web pages). Pure JVM, no Android dependency, unit-tested.
 *
 * Detection is advisory. The enforcement that matters is [ScreenContentGuard] +
 * the sensitive-action block in [SafetyGate]: a flagged context forces explicit
 * user confirmation before any sensitive action.
 */
object PromptInjectionGuard {

    data class Suspicion(val flagged: Boolean, val reasons: List<String>)

    private data class Rule(val reason: String, val pattern: Regex)

    private val rules: List<Rule> = listOf(
        Rule("override_en", Regex("""(?i)\b(ignore|disregard|forget|override|bypass)\b[^.\n]{0,40}\b(previous|prior|above|earlier|all|your|the)\b[^.\n]{0,30}\b(instructions?|rules?|prompts?|directions?|guidelines?)\b""")),
        Rule("override_ar", Regex("""(تجاهل|تجاهلي|تجاهلوا|انس|انسَ|انسي|امسح|أهمل|اهمل)[^.\n]{0,40}(التعليمات|القواعد|الأوامر|الاوامر|الإرشادات|الارشادات|ما قيل|كل ما|السابقة|السابق)""")),
        Rule("role_en", Regex("""(?i)\byou are now\b|\bact as\b|\bpretend (to be|you are)\b|\bnew role\b""")),
        Rule("role_ar", Regex("""[أاإ]نت\s+ال[آاأ]ن|تصرف كأنك|تظاهر أنك""")),
        Rule("fake_system_tag", Regex("""(?i)<\s*/?\s*(system|assistant|developer|instructions?)\s*>|\[\s*(system|admin|developer)\s*\]|^\s*(system|assistant)\s*:""", RegexOption.MULTILINE)),
        Rule("fake_system_ar", Regex("""#{2,4}\s*\(?\s*(ال)?(نظام|مطور|إدارة|ادارة)(?![\p{L}])|\[\s*(ال)?(نظام|مطور)\s*\]""")),
        Rule("hidden_marker", Regex("""<!--[\s\S]*?-->|[\u200B-\u200F\u202A-\u202E\u2066-\u2069\uFEFF]""")),
        Rule("exfil_en", Regex("""(?i)\b(send|transfer|forward|share)\b[^.\n]{0,40}\b(money|password|otp|code|card|iban|contacts?)\b""")),
        Rule("exfil_ar", Regex("""(أرسل|ارسل|حوّل|حول|أرسلي|ارسلي)[^.\n]{0,40}(المال|فلوس|كلمة المرور|رمز|الرقم السري|البطاقة|جهات الاتصال)""")),
    )

    /** Scans text and returns which override/role/tag rules matched. */
    fun scan(text: String?): Suspicion {
        if (text.isNullOrBlank()) return Suspicion(false, emptyList())
        val normalized = normalize(text)
        val hits = rules.filter { it.pattern.containsMatchIn(normalized) }.map { it.reason }
        return Suspicion(hits.isNotEmpty(), hits)
    }

    /** Removes Arabic diacritics and tatweel so patterns match obfuscated spellings. */
    internal fun normalize(text: String): String = text
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace("\u0640", "")
        .replace('\u00A0', ' ')
}
