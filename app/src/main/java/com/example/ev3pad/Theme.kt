package com.example.ev3pad

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
class Pal(
    val bg: Color, val bg2: Color, val card: Color, val ink: Color, val mute: Color, val line: Color,
    val body: Color, val key: Color, val ok: Color, val bad: Color, val acc: Color, val acc2: Color, val onAcc: Color
) {
    fun grad(): Brush = Brush.horizontalGradient(listOf(acc, acc2))
}

val DarkPal = Pal(
    bg = Color(0xFF070D14), bg2 = Color(0xFF0C1622), card = Color(0xFF0E1822), ink = Color(0xFFEAF2F8), mute = Color(0xFF7F93A6),
    line = Color(0xFF1F2B38), body = Color(0xFF14202D), key = Color(0xFF243446), ok = Color(0xFF3DDC97), bad = Color(0xFFFF6B6B),
    acc = Color(0xFF19D3C5), acc2 = Color(0xFF8B7CFF), onAcc = Color(0xFF04201E)
)
val LightPal = Pal(
    bg = Color(0xFFEEF3F7), bg2 = Color(0xFFE6EDF3), card = Color(0xFFFFFFFF), ink = Color(0xFF0B1822), mute = Color(0xFF5A6D7E),
    line = Color(0xFFD0D9E1), body = Color(0xFFDBE4EC), key = Color(0xFFC5D2DE), ok = Color(0xFF1F9D57), bad = Color(0xFFD93A30),
    acc = Color(0xFF0AA89C), acc2 = Color(0xFF6A5BE0), onAcc = Color(0xFFFFFFFF)
)
val LocalPal = staticCompositionLocalOf { DarkPal }

@Composable
fun EV3Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(
        primary = p.acc, onPrimary = p.onAcc, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink,
        outline = p.line, onSurfaceVariant = p.mute, surfaceContainer = p.bg2, surfaceContainerHigh = p.bg2
    ) else lightColorScheme(
        primary = p.acc, onPrimary = p.onAcc, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink,
        outline = p.line, onSurfaceVariant = p.mute, surfaceContainer = p.bg2, surfaceContainerHigh = p.bg2
    )
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}
