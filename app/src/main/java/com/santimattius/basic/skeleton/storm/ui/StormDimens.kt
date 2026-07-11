package com.santimattius.basic.skeleton.storm.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compose dimension and color constants for [StormScreen], kept out of the composable to
 * satisfy detekt's `MagicNumber` rule.
 */
object StormDimens {
    val BOX_SIZE: Dp = 120.dp
    val CORNER_RADIUS: Dp = 16.dp
    val SCREEN_PADDING: Dp = 24.dp
    val SPACER_LARGE: Dp = 24.dp
    val SPACER_SMALL: Dp = 8.dp
    val ROW_PADDING: Dp = 4.dp
    val BOX_COLOR: Color = Color(0xFF6C5CE7)
}
