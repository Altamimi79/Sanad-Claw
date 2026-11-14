// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Corpus test: reads app/src/test/resources/injection/{hostile,benign}.txt.
 * Lines starting with '#' and blank lines are ignored.
 */
class InjectionCorpusTest {

    private fun load(name: String): List<String> {
        val stream = javaClass.classLoader!!.getResourceAsStream("injection/$name")
            ?: throw IllegalStateException("missing test resource injection/$name")
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLines()
                .map { it.trim() }
                // Comments are lines starting with "# " (hash + space). Samples such as
                // "### النظام: ..." start with "#" but are not comments.
                .filter { it.isNotEmpty() && !it.startsWith("# ") }
        }
    }

    private val hostile by lazy { load("hostile.txt") }
    private val benign by lazy { load("benign.txt") }

    @Test
    fun corpusSizesAreAsSpecified() {
        assertEquals("hostile sample count", 50, hostile.size)
        assertEquals("benign sample count", 50, benign.size)
    }

    @Test
    fun hostileDetectionRateAtLeast90Percent() {
        val flagged = hostile.count { PromptInjectionGuard.scan(it).flagged }
        val rate = flagged * 100.0 / hostile.size
        println("INJECTION_CORPUS hostile_flagged=$flagged/${hostile.size} rate=${"%.1f".format(rate)}%")
        assertTrue("hostile flagged ${"%.1f".format(rate)}% < 90%", rate >= 90.0)
    }

    @Test
    fun benignFalsePositiveRateAtMost5Percent() {
        val flagged = benign.filter { PromptInjectionGuard.scan(it).flagged }
        val rate = flagged.size * 100.0 / benign.size
        println("INJECTION_CORPUS benign_flagged=${flagged.size}/${benign.size} rate=${"%.1f".format(rate)}% samples=$flagged")
        assertTrue("benign flagged ${"%.1f".format(rate)}% > 5%: $flagged", rate <= 5.0)
    }

    /**
     * Mandatory rule: for every hostile sample that the detector flags, every sensitive
     * kind must require explicit confirmation when derived from that context.
     * Detector misses are counted and reported as the residual gap (see the
     * "missed" line in the output); they are not hidden.
     */
    @Test
    fun zeroSensitiveActionsExecutedWithoutConfirmationFromAnyHostileText() {
        var checks = 0
        var violations = 0
        var detectorFlagged = 0
        for (sample in hostile) {
            if (PromptInjectionGuard.scan(sample).flagged) detectorFlagged++
            val guard = ScreenContentGuard()
            guard.wrap("screen", sample)
            for (kind in ScreenContentGuard.SENSITIVE_KINDS) {
                checks++
                if (guard.decide(kind, confirmedByUser = false) != ScreenContentGuard.Decision.REQUIRE_CONFIRMATION) {
                    violations++
                }
            }
        }
        println("INJECTION_CORPUS sensitive_checks=$checks violations=$violations detector_flagged=$detectorFlagged")
        assertEquals("sensitive actions executed without confirmation", 0, violations)
    }
}
