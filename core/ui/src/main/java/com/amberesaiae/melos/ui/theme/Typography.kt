package com.amberesaiae.melos.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

/**
 * Roboto Flex Font Family
 * 
 * To use Roboto Flex, add the font files to your app/src/main/font/ directory
 * and reference them here. Alternatively, use Google Fonts integration.
 * 
 * For now, using default Roboto which is available on all Android devices.
 * To use Roboto Flex, download from Google Fonts and add:
 * - RobotoFlex-VariableFont_GRAD,XTRA,YTAS,YTDE,YTFI,YTLC,YTUC,opsz,slnt,wdth,wght.ttf
 */
val RobotoFlex = FontFamily.Default // Replace with actual Roboto Flex implementation

/**
 * Melos Typography Scale
 * 
 * Material 3 defines 15 text styles across 4 categories:
 * - Display: Large, prominent text for short pieces (e.g., large numbers)
 * - Headline: Section headers and prominent text
 * - Title: Medium-emphasis text for sections within content
 * - Body: Long-form content and paragraphs
 * - Label: Small text for UI elements like buttons and chips
 */
val Typography = Typography(
    // Display styles - Large, prominent text
    displayLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    
    // Headline styles - Section headers
    headlineLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    
    // Title styles - Medium-emphasis text
    titleLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    
    // Body styles - Long-form content
    bodyLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    
    // Label styles - UI elements
    labelLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Usage examples:
 * 
 * 1. Using predefined styles:
 *    Text(
 *        text = "Song Title",
 *        style = MaterialTheme.typography.headlineMedium
 *    )
 * 
 * 2. Customizing a style:
 *    Text(
 *        text = "Artist Name",
 *        style = MaterialTheme.typography.bodyLarge.copy(
 *            color = MaterialTheme.colorScheme.secondary
 *        )
 *    )
 * 
 * 3. Common use cases in Melos:
 *    - displayLarge: Album art overlay text, now playing time
 *    - headlineLarge: Screen titles, playlist names
 *    - headlineMedium: Section headers in settings
 *    - titleLarge: Song titles in now playing
 *    - titleMedium: Artist names, album names
 *    - bodyLarge: Lyrics, descriptions
 *    - bodyMedium: Secondary text, metadata
 *    - labelLarge: Button text, chip labels
 *    - labelSmall: Tag labels, duration badges
 */
