/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.equalizer.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Custom vertical slider component for equalizer bands.
 * 
 * @param value Current value of the slider
 * @param valueRange Range of values the slider can take
 * @param onValueChange Callback when value changes
 * @param modifier Compose modifier
 * @param colors Slider colors
 * @param interactionSource Interaction source for animations
 */
@Composable
fun VerticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // Rotate the slider -90 degrees to make it vertical
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .rotate(-90f)
            .fillMaxWidth()
    )
}

/**
 * Preview composable.
 */
@Preview
@Composable
private fun VerticalSliderPreview() {
    Surface {
        VerticalSlider(
            value = 0f,
            valueRange = -1200f..1200f,
            onValueChange = {},
            modifier = Modifier
                .height(150.dp)
                .width(40.dp)
        )
    }
}
