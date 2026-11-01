package com.santimattius.basic.skeleton.runtime.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Compose dimension and color constants for [RuntimeValidationScreen] (keeps detekt's `MagicNumber` clean). */
object RuntimeDimens {
    val SCREEN_PADDING: Dp = 16.dp
    val SPACER: Dp = 8.dp
    val ROW_PADDING: Dp = 6.dp
    val STATUS_WIDTH: Dp = 56.dp
    val PASS_COLOR: Color = Color(0xFF2E7D32)
    val FAIL_COLOR: Color = Color(0xFFC62828)
    val RUNNING_COLOR: Color = Color(0xFFEF6C00)
    val PENDING_COLOR: Color = Color(0xFF757575)
}
