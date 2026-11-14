package com.dxdepartment.screenmonitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Live = Color(0xFF22C55E)
val Warn = Color(0xFFF59E0B)
val Bad = Color(0xFFEF4444)
val Muted = Color(0xFF94A3B8)

private val Colors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF00121F),
    secondary = Color(0xFF818CF8),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceContainer = Color(0xFF162033),
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerHighest = Color(0xFF243247),
    outline = Color(0xFF334155),
    error = Bad,
)

@Composable
fun DxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
