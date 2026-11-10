// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

/**
 * Language policy for the Sanad fork (phase 2):
 * understand Gulf (Khaleeji) Arabic dialects and always answer in written Arabic
 * unless the user explicitly requests another language.
 *
 * Pure logic (no Android deps) so it is unit-testable; PromptUtils wires it into
 * every chat/task system prompt.
 */
object LanguagePolicy {

    const val HEADER = "Language policy:"

    const val POLICY_TEXT =
        "The user may write in any Arabic dialect, including Gulf/Khaleeji colloquial Arabic " +
            "(Saudi, Kuwaiti, Emirati, Bahraini, Qatari, Omani). Understand it fully. " +
            "You MUST reply in Modern Standard Arabic (written fus-Ha), even when the user writes in a dialect. " +
            "Never answer an Arabic question in English. " +
            "Use another language only if the user explicitly asks for that language or clearly expects a reply in it. " +
            "Keep tool names, code, file paths, URLs, app package names and model identifiers untranslated."

    /** Appends the policy to a base system prompt. */
    fun apply(basePrompt: String): String =
        "$basePrompt\n\n$HEADER\n$POLICY_TEXT"

    /**
     * Full composition used by PromptUtils:
     * base prompt -> language policy -> user's persistent global instructions last,
     * so a user instruction can deliberately override the default language policy.
     */
    fun compose(basePrompt: String, globalPrompt: String, separator: String, prefixHeader: String): String {
        val withPolicy = apply(basePrompt)
        if (globalPrompt.isBlank()) return withPolicy
        return "$withPolicy$separator$prefixHeader\n$globalPrompt"
    }
}
