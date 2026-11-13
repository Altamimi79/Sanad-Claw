// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Test

/**
 * Held-out detection result. Reports numbers only; does NOT gate on thresholds, so the
 * set is never tuned to pass. The numbers printed here are the detection result to cite.
 */
class HeldOutCorpusTest {

    private fun load(name: String): List<String> {
        val stream = javaClass.classLoader!!.getResourceAsStream("injection_heldout/$name")
            ?: throw IllegalStateException("missing held-out resource $name")
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("# ") }
        }
    }

    @Test
    fun reportHeldOutDetectionNumbers() {
        val hostile = load("hostile.txt")
        val benign = load("benign.txt")
        val hostileFlagged = hostile.count { PromptInjectionGuard.scan(it).flagged }
        val benignFlagged = benign.count { PromptInjectionGuard.scan(it).flagged }
        val missed = hostile.filter { !PromptInjectionGuard.scan(it).flagged }

        // Source-based taint blocks sensitive actions for ALL hostile samples regardless of
        // detection; report that too, because it is the enforcement number.
        var enforced = 0
        for (s in hostile) {
            val g = ScreenContentGuard()
            g.wrap("screen", s)
            if (ScreenContentGuard.SENSITIVE_KINDS.all {
                    g.decide(it, confirmedByUser = false) == ScreenContentGuard.Decision.REQUIRE_CONFIRMATION
                }
            ) enforced++
        }

        println("HELDOUT hostile=${hostile.size} detector_flagged=$hostileFlagged " +
            "rate=${"%.1f".format(hostileFlagged * 100.0 / hostile.size)}%")
        println("HELDOUT benign=${benign.size} detector_flagged=$benignFlagged " +
            "rate=${"%.1f".format(benignFlagged * 100.0 / benign.size)}%")
        println("HELDOUT enforced_sensitive_block=$enforced/${hostile.size}")
        println("HELDOUT detector_missed=${missed.size} samples=$missed")
    }
}
