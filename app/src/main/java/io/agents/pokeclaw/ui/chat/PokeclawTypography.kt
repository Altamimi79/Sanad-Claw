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

private fun TextStyle.withSanadFont(): TextStyle = copy(fontFamily = PokeclawFontFamily)

val PokeclawTypography: Typography
    get() {
        val base = Typography()
        return base.copy(
            displayLarge = base.displayLarge.withSanadFont(),
            displayMedium = base.displayMedium.withSanadFont(),
            displaySmall = base.displaySmall.withSanadFont(),
            headlineLarge = base.headlineLarge.withSanadFont(),
            headlineMedium = base.headlineMedium.withSanadFont(),
            headlineSmall = base.headlineSmall.withSanadFont(),
            titleLarge = base.titleLarge.withSanadFont(),
            titleMedium = base.titleMedium.withSanadFont(),
            titleSmall = base.titleSmall.withSanadFont(),
            bodyLarge = base.bodyLarge.withSanadFont(),
            bodyMedium = base.bodyMedium.withSanadFont(),
            bodySmall = base.bodySmall.withSanadFont(),
            labelLarge = base.labelLarge.withSanadFont(),
            labelMedium = base.labelMedium.withSanadFont(),
            labelSmall = base.labelSmall.withSanadFont(),
        )
    }
