package com.example.ev3pad

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFF5C400)
val OnAccent = Color(0xFF14171C)

@Immutable
class Pal(val bg: Color, val card: Color, val ink: Color, val mute: Color, val line: Color,
          val body: Color, val key: Color, val ok: Color, val bad: Color)

val LightPal = Pal(Color(0xFFEEF0F3), Color.White, Color(0xFF14171C), Color(0xFF6A7280), Color(0xFFD9DDE3),
    Color(0xFFD4D8DE), Color(0xFFAEB4BD), Color(0xFF1F9D57), Color(0xFFD93A30))
val DarkPal = Pal(Color(0xFF101319), Color(0xFF1A1F27), Color(0xFFEEF1F5), Color(0xFF8D96A5), Color(0xFF2B323D),
    Color(0xFF2A303A), Color(0xFF464E5B), Color(0xFF3DDC84), Color(0xFFFF6A5F))
val LocalPal = staticCompositionLocalOf { LightPal }

@Composable
fun EV3Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(primary = Accent, onPrimary = OnAccent, background = p.bg,
        surface = p.card, onSurface = p.ink, onBackground = p.ink, outline = p.line, onSurfaceVariant = p.mute)
    else lightColorScheme(primary = Accent, onPrimary = OnAccent, background = p.bg,
        surface = p.card, onSurface = p.ink, onBackground = p.ink, outline = p.line, onSurfaceVariant = p.mute)
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}
