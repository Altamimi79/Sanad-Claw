// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import io.agents.pokeclaw.R

/**
 * Arabic-friendly Compose typography for the Sanad fork.
 * Noto Naskh Arabic (OFL) renders Arabic legibly and falls back cleanly for Latin text.
 */
val PokeclawFontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.noto_naskh_arabic, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.noto_naskh_arabic, FontWeight.Bold),
)

/** Compose family for a stored [TextAppearance.Family]. */
fun composeFamilyFor(family: TextAppearance.Family): FontFamily = when (family) {
    TextAppearance.Family.NASKH -> PokeclawFontFamily
    TextAppearance.Family.SYSTEM_SANS -> FontFamily.SansSerif
    TextAppearance.Family.MONOSPACE -> FontFamily.Monospace
}

private fun TextStyle.withFamily(family: FontFamily): TextStyle = copy(fontFamily = family)

/**
 * Typography for the chosen family. Defaults to the Naskh family, so callers that do not
 * pass a family get the same typography as before this setting existed.
 */
fun pokeclawTypography(family: TextAppearance.Family = TextAppearance.Family.NASKH): Typography {
    val f = composeFamilyFor(family)
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.withFamily(f),
        displayMedium = base.displayMedium.withFamily(f),
        displaySmall = base.displaySmall.withFamily(f),
        headlineLarge = base.headlineLarge.withFamily(f),
        headlineMedium = base.headlineMedium.withFamily(f),
        headlineSmall = base.headlineSmall.withFamily(f),
        titleLarge = base.titleLarge.withFamily(f),
        titleMedium = base.titleMedium.withFamily(f),
        titleSmall = base.titleSmall.withFamily(f),
        bodyLarge = base.bodyLarge.withFamily(f),
        bodyMedium = base.bodyMedium.withFamily(f),
        bodySmall = base.bodySmall.withFamily(f),
        labelLarge = base.labelLarge.withFamily(f),
        labelMedium = base.labelMedium.withFamily(f),
        labelSmall = base.labelSmall.withFamily(f),
    )
}

/** Kept for existing callers: the default family typography. */
val PokeclawTypography: Typography
    get() = pokeclawTypography()
