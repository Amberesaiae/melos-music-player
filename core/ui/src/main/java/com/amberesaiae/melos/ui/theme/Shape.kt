package com.amberesaiae.melos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Melos Shape Scale
 * 
 * Material 3 defines 5 corner sizes for consistent rounding across the UI.
 * These shapes are used throughout the Melos Music Player for:
 * - Buttons and cards
 * - Bottom sheets and dialogs
 * - Navigation bars and app bars
 * - Image containers and album art
 * - Chips and badges
 */

// Shape scale definitions
val RoundedExtraSmall = 4.dp
val RoundedSmall = 8.dp
val RoundedMedium = 12.dp
val RoundedLarge = 16.dp
val RoundedExtraLarge = 28.dp

/**
 * Material 3 Shapes object
 * 
 * Each shape slot maps to specific UI components:
 * - extraSmall: Small chips, badges, icon buttons
 * - small: Buttons, text fields, cards
 * - medium: Extended FABs, dialogs, bottom sheets
 * - large: Large components, side sheets
 * - extraLarge: Special large components
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(RoundedExtraSmall),
    small = RoundedCornerShape(RoundedSmall),
    medium = RoundedCornerShape(RoundedMedium),
    large = RoundedCornerShape(RoundedLarge),
    extraLarge = RoundedCornerShape(RoundedExtraLarge)
)

/**
 * Usage examples in Melos Music Player:
 * 
 * 1. Using predefined shapes:
 *    Card(
 *        shape = MaterialTheme.shapes.medium,
 *        modifier = Modifier.padding(8.dp)
 *    ) {
 *        // Card content
 *    }
 * 
 * 2. Custom shape for specific component:
 *    Button(
 *        shape = RoundedCornerShape(50), // Pill shape
 *        onClick = { /* action */ }
 *    ) {
 *        Text("Play")
 *    }
 * 
 * 3. Common Melos use cases:
 *    - extraSmall: Duration badges, track number chips
 *    - small: Primary/secondary buttons, search fields
 *    - medium: Song cards, album cards, playlist cards
 *    - large: Bottom sheet containers, player cards
 *    - extraLarge: Now playing screen background, modal sheets
 * 
 * 4. Combining with color scheme:
 *    Surface(
 *        shape = MaterialTheme.shapes.large,
 *        color = MaterialTheme.colorScheme.surfaceVariant,
 *        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
 *    ) {
 *        // Surface content
 *    }
 */
