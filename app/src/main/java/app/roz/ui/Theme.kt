package app.roz.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Each kind of item has its own colour, as in the mockups: a strong one for text and a soft fill. */
data class Palette(
    val ground: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val habit: Color,
    val habitSoft: Color,
    val chore: Color,
    val choreSoft: Color,
    val reminder: Color,
    val reminderSoft: Color,
    val birthday: Color,
    val birthdaySoft: Color,
    val birthdayStrong: Color,
    val late: Color,
    val lateLine: Color,
    val flame: Color,
)

private val Light = Palette(
    ground = Color(0xFFF4F5F0),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1C211F),
    muted = Color(0xFF5B635F),
    line = Color(0xFFE3E5DE),
    habit = Color(0xFF0E6E5C),
    habitSoft = Color(0xFFDCEFE9),
    chore = Color(0xFF3D4FA8),
    choreSoft = Color(0xFFE3E7F7),
    reminder = Color(0xFFB4501F),
    reminderSoft = Color(0xFFFBE9DF),
    birthday = Color(0xFF6E4A00),
    birthdaySoft = Color(0xFFFCEFD2),
    birthdayStrong = Color(0xFFE8A33D),
    late = Color(0xFFA3361A),
    lateLine = Color(0xFFF2C9B8),
    flame = Color(0xFF9A4A0A),
)

private val Dark = Palette(
    ground = Color(0xFF121513),
    card = Color(0xFF1D2220),
    ink = Color(0xFFECEFEA),
    muted = Color(0xFFA5ADA9),
    line = Color(0xFF323935),
    habit = Color(0xFF5FCCB2),
    habitSoft = Color(0xFF173B33),
    chore = Color(0xFF9DAAF0),
    choreSoft = Color(0xFF242B4D),
    reminder = Color(0xFFF2A07A),
    reminderSoft = Color(0xFF45271A),
    birthday = Color(0xFFF2CB7E),
    birthdaySoft = Color(0xFF3D3015),
    birthdayStrong = Color(0xFFE8A33D),
    late = Color(0xFFFF9C80),
    lateLine = Color(0xFF6B3426),
    flame = Color(0xFFF0A35E),
)

val LocalPalette = staticCompositionLocalOf { Light }

val palette: Palette
    @Composable get() = LocalPalette.current

@Composable
fun RozTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = p.habit, background = p.ground, surface = p.card, onBackground = p.ink, onSurface = p.ink)
    } else {
        lightColorScheme(primary = p.habit, background = p.ground, surface = p.card, onBackground = p.ink, onSurface = p.ink)
    }
    val base = Typography()
    val typography = base.copy(
        headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        bodySmall = TextStyle(fontSize = 12.sp),
        labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
