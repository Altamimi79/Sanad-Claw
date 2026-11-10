// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.agents.pokeclaw.R

/**
 * Arabic-friendly Compose typography for the Sanad fork.
 * Noto Naskh Arabic (OFL) renders Arabic legibly and falls back cleanly for Latin text.
 */
val PokeclawFontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.noto_naskh_arabic, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.noto_naskh_arabic, FontWeight.Bold),
)

val PokeclawTypography: Typography
    get() = Typography(defaultFontFamily = PokeclawFontFamily)
