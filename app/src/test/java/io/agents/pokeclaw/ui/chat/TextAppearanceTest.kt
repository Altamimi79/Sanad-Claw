// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.ui.chat

import io.agents.pokeclaw.ui.chat.TextAppearance.Family
import io.agents.pokeclaw.ui.chat.TextAppearance.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class TextAppearanceTest {

    @Test
    fun family_knownKeysResolve() {
        assertEquals(Family.NASKH, Family.fromKey("naskh"))
        assertEquals(Family.SYSTEM_SANS, Family.fromKey("sans"))
        assertEquals(Family.MONOSPACE, Family.fromKey("mono"))
    }

    @Test
    fun family_unknownOrNullFallsBackToDefault() {
        assertEquals(Family.NASKH, Family.fromKey(null))
        assertEquals(Family.NASKH, Family.fromKey(""))
        assertEquals(Family.NASKH, Family.fromKey("comic-sans"))
    }

    @Test
    fun size_knownKeysResolve() {
        assertEquals(Size.NORMAL, Size.fromKey("normal"))
        assertEquals(Size.SLIGHTLY_LARGER, Size.fromKey("slightly_larger"))
        assertEquals(Size.LARGER, Size.fromKey("larger"))
        assertEquals(Size.LARGEST, Size.fromKey("largest"))
    }

    @Test
    fun size_unknownOrNullFallsBackToNormal() {
        assertEquals(Size.NORMAL, Size.fromKey(null))
        assertEquals(Size.NORMAL, Size.fromKey("huge"))
    }

    @Test
    fun sizeSteps_increaseMonotonically() {
        val scales = Size.values().map { it.scale }
        for (i in 1 until scales.size) {
            assert(scales[i] > scales[i - 1]) { "step $i not larger than previous" }
        }
    }

    @Test
    fun scaleFor_neverExceedsMaximum() {
        Size.values().forEach { size ->
            assert(TextAppearance.scaleFor(size.key) <= TextAppearance.MAX_SCALE)
        }
    }

    @Test
    fun clampScale_boundsAndSpecialValues() {
        assertEquals(TextAppearance.MIN_SCALE, TextAppearance.clampScale(0.2f), 0.0001f)
        assertEquals(TextAppearance.MAX_SCALE, TextAppearance.clampScale(9f), 0.0001f)
        assertEquals(1.2f, TextAppearance.clampScale(1.2f), 0.0001f)
        assertEquals(TextAppearance.MIN_SCALE, TextAppearance.clampScale(Float.NaN), 0.0001f)
        assertEquals(TextAppearance.MIN_SCALE, TextAppearance.clampScale(Float.POSITIVE_INFINITY), 0.0001f)
    }

    @Test
    fun scaleFor_normalIsExactlyOne() {
        assertEquals(1.0f, TextAppearance.scaleFor("normal"), 0.0001f)
        assertEquals(1.0f, TextAppearance.scaleFor(null), 0.0001f)
    }
}
