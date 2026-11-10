// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.agents.pokeclaw.voice.VoiceTimerFormat

/**
 * Recording / processing state strip shown above the chat composer while dictating.
 * Pulsing mic ring + live timer + partial transcript + cancel.
 */
@Composable
fun VoiceRecordingBar(
    processing: Boolean,
    elapsedSeconds: Int,
    rmsLevel: Float,
    statusListening: String,
    statusProcessing: String,
    cancelLabel: String,
    partialText: String,
    accent: Color,
    textColor: Color,
    mutedColor: Color,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voicePulseScale",
    )
    val level = rmsAsFraction(rmsLevel)
    val amplitudeScale by animateFloatAsState(
        targetValue = if (processing) 1f else (1f + 0.25f * level),
        label = "voiceAmplitude",
    )
    val scale = if (processing) 1f else pulseScale * amplitudeScale

    Surface(
        color = accent.copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (processing) statusProcessing else statusListening,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (partialText.isNotBlank()) {
                    Text(
                        text = partialText,
                        color = mutedColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = VoiceTimerFormat.format(elapsedSeconds),
                color = mutedColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onCancel) {
                Text(text = cancelLabel, color = accent, fontSize = 12.sp)
            }
        }
    }
}

/** SpeechRecognizer rmsdB is roughly -2..10; map to 0..1. */
internal fun rmsAsFraction(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
