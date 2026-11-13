// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package io.agents.pokeclaw.ui.chat

/**
 * Pure model for user text appearance: font family and text-size scale.
 *
 * Kept free of Android and Compose so the mapping from a stored preference to a concrete
 * scale and family can be unit-tested. Unknown stored values fall back to the default
 * instead of failing, so a corrupt preference can never break the UI.
 */
object TextAppearance {

    enum class Family(val key: String) {
        /** Bundled Noto Naskh Arabic (default). */
        NASKH("naskh"),
        /** The phone's own sans-serif font. */
        SYSTEM_SANS("sans"),
        /** Fixed-width font. */
        MONOSPACE("mono");

        companion object {
            fun fromKey(key: String?): Family = values().firstOrNull { it.key == key } ?: NASKH
        }
    }

    enum class Size(val key: String, val scale: Float) {
        NORMAL("normal", 1.0f),
        SLIGHTLY_LARGER("slightly_larger", 1.15f),
        LARGER("larger", 1.3f),
        LARGEST("largest", 1.5f);

        companion object {
            fun fromKey(key: String?): Size = values().firstOrNull { it.key == key } ?: NORMAL
        }
    }

    /** Lowest and highest scale the UI will ever apply, regardless of the chosen size. */
    const val MIN_SCALE = 1.0f
    const val MAX_SCALE = 1.5f

    /** Clamps a scale into the supported range. NaN and infinities map to the minimum. */
    fun clampScale(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return MIN_SCALE
        return value.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** The effective multiplier for a stored size key. */
    fun scaleFor(sizeKey: String?): Float = clampScale(Size.fromKey(sizeKey).scale)
}
