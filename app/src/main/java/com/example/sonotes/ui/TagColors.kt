package com.example.sonotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

val tagPresetColors = listOf(
    0,
    Color(0xFFD32F2F).toArgb(),
    Color(0xFFF57C00).toArgb(),
    Color(0xFF388E3C).toArgb(),
    Color(0xFF1976D2).toArgb(),
    Color(0xFF7B1FA2).toArgb()
)

fun tagColorToComposeColor(colorArgb: Int, defaultColor: Color): Color {
    return if (colorArgb != 0) Color(colorArgb) else defaultColor
}
