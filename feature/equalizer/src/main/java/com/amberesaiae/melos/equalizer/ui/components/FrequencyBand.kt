/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.equalizer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single frequency band component with vertical slider and labels.
 * 
 * @param frequency Center frequency in Hz
 * @param level Current gain level in dB * 100 (-1200 to 1200)
 * @param onLevelChanged Callback when level changes
 * @param modifier Compose modifier
 */
@Composable
fun FrequencyBand(
    frequency: Int,
    level: Int,
    onLevelChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val frequencyLabel = FrequencyBandUi.formatFrequency(frequency)
    val levelDb = level / 100.0f
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // dB value label
        Text(
            text = if (levelDb > 0) "+$levelDb" else "$levelDb",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 10.sp
        )
        
        // Vertical slider
        VerticalSlider(
            value = level.toFloat(),
            valueRange = FrequencyBandUi.MIN_LEVEL.toFloat()..FrequencyBandUi.MAX_LEVEL.toFloat(),
            onValueChange = { newLevel ->
                onLevelChanged(newLevel.toInt())
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        
        // Frequency label (rotated vertical)
        Box(
            modifier = Modifier
                .height(80.dp)
                .width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = frequencyLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.rotate(-90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 9.sp
            )
        }
    }
}

/**
 * Preview composable.
 */
@Preview
@Composable
private fun FrequencyBandPreview() {
    Surface {
        FrequencyBand(
            frequency = 1000,
            level = 0,
            onLevelChanged = {},
            modifier = Modifier
                .width(60.dp)
                .height(200.dp)
        )
    }
}
