package trashpanda.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Themes — DESIGN §3.1: one token set, three themes. Risk colors are identical
 * everywhere on purpose; they always mean the same thing (green = auto-check,
 * amber = you tick, red = look only). Screens read Tokens.* and automatically
 * follow whatever palette is active.
 */
enum class ThemeName(val label: String) {
    DARK_GLASS("glass"),
    LIGHT_MILK("milk"),
    TERMINAL_NEON("neon"),
}

data class Palette(
    val bg: Color,
    val surface: Color,
    /** Inset rows/cards that sit on bg (list rows, stat cards, receipt inner). */
    val cardInset: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val primary: Color,
    val primaryOn: Color,
    val donutTrack: Color,
    val donutUsed: Color,
    val riskSafe: Color,
    val riskReview: Color,
    val riskDanger: Color,
    val focus: Color,
    val scanline: Boolean,
    val mono: Boolean,
)

// Dark glass — the shipped default (DESIGN §3.1 column 1).
private val paletteDark = Palette(
    bg = Color(0xFF0B0F14),
    surface = Color(0xFF111827),
    cardInset = Color(0xFF0B0F14),
    border = Color(0xFF1F2937),
    text = Color(0xFFE5E7EB),
    textDim = Color(0xFF9CA3AF),
    primary = Color(0xFF34D399),
    primaryOn = Color(0xFF06281C),
    donutTrack = Color(0xFF151D2B),
    donutUsed = Color(0xFF3B4A61),
    riskSafe = Color(0xFF22C55E),
    riskReview = Color(0xFFF59E0B),
    riskDanger = Color(0xFFEF4444),
    focus = Color(0xFF818CF8),
    scanline = false,
    mono = false,
)

// Light milk — the warm paper look (DESIGN §3.1 column 2).
private val paletteMilk = Palette(
    bg = Color(0xFFFAFAF7),
    surface = Color(0xFFFFFFFF),
    cardInset = Color(0xFFF1EEE7),
    border = Color(0xFFE7E5E0),
    text = Color(0xFF1B1B18),
    textDim = Color(0xFF78716C),
    primary = Color(0xFF0D9488),
    primaryOn = Color(0xFFFFFFFF),
    donutTrack = Color(0xFFEDEAE3),
    donutUsed = Color(0xFFC7C2B8),
    riskSafe = Color(0xFF22C55E),
    riskReview = Color(0xFFF59E0B),
    riskDanger = Color(0xFFEF4444),
    focus = Color(0xFF4F46E5),
    scanline = false,
    mono = false,
)

// Terminal neon — green phosphor on black, everything monospace, CRT scanlines.
private val paletteNeon = Palette(
    bg = Color(0xFF0A100A),
    surface = Color(0xFF0D140D),
    cardInset = Color(0xFF0C130C),
    border = Color(0xFF1E3320),
    text = Color(0xFFCAF7C4),
    textDim = Color(0xFF7EA57C),
    primary = Color(0xFF4AF626),
    primaryOn = Color(0xFF0A100A),
    donutTrack = Color(0xFF101A10),
    donutUsed = Color(0xFF1E3320),
    riskSafe = Color(0xFF22C55E),
    riskReview = Color(0xFFF59E0B),
    riskDanger = Color(0xFFEF4444),
    focus = Color(0xFF8AFF80),
    scanline = true,
    mono = true,
)

private val LocalPalette = staticCompositionLocalOf { paletteDark }

/**
 * Design tokens. Reads are composable so every call site follows the active theme
 * without any threading changes. Usage stays exactly like v0.1: `Tokens.bg`, …
 */
object Tokens {
    val bg: Color @Composable get() = LocalPalette.current.bg
    val surface: Color @Composable get() = LocalPalette.current.surface
    val cardInset: Color @Composable get() = LocalPalette.current.cardInset
    val border: Color @Composable get() = LocalPalette.current.border
    val text: Color @Composable get() = LocalPalette.current.text
    val textDim: Color @Composable get() = LocalPalette.current.textDim
    val primary: Color @Composable get() = LocalPalette.current.primary
    val primaryOn: Color @Composable get() = LocalPalette.current.primaryOn
    val donutTrack: Color @Composable get() = LocalPalette.current.donutTrack
    val donutUsed: Color @Composable get() = LocalPalette.current.donutUsed
    val riskSafe: Color @Composable get() = LocalPalette.current.riskSafe
    val riskReview: Color @Composable get() = LocalPalette.current.riskReview
    val riskDanger: Color @Composable get() = LocalPalette.current.riskDanger
    val focus: Color @Composable get() = LocalPalette.current.focus
    /** CRT scanlines overlay — neon only (DESIGN §3.1). */
    val scanline: Boolean @Composable get() = LocalPalette.current.scanline
    /** Render all type in the terminal mono family — neon only. */
    val mono: Boolean @Composable get() = LocalPalette.current.mono
}

@Composable
fun TrashPandaTheme(name: ThemeName, content: @Composable () -> Unit) {
    val palette = when (name) {
        ThemeName.DARK_GLASS -> paletteDark
        ThemeName.LIGHT_MILK -> paletteMilk
        ThemeName.TERMINAL_NEON -> paletteNeon
    }
    val scheme = when (name) {
        ThemeName.LIGHT_MILK -> lightColorScheme(
            background = palette.bg,
            surface = palette.surface,
            primary = palette.primary,
            onPrimary = palette.primaryOn,
            secondary = palette.focus,
            error = palette.riskDanger,
            onBackground = palette.text,
            onSurface = palette.text,
        )
        else -> darkColorScheme(
            background = palette.bg,
            surface = palette.surface,
            primary = palette.primary,
            onPrimary = palette.primaryOn,
            secondary = palette.focus,
            error = palette.riskDanger,
            onBackground = palette.text,
            onSurface = palette.text,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = if (palette.mono) monoTypography() else Typography(),
    ) {
        CompositionLocalProvider(LocalPalette provides palette, content = content)
    }
}

/** Terminal neon: every material text style gets the mono family (JetBrains-Mono role). */
private fun monoTypography(): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = FontFamily.Monospace),
        displayMedium = t.displayMedium.copy(fontFamily = FontFamily.Monospace),
        displaySmall = t.displaySmall.copy(fontFamily = FontFamily.Monospace),
        headlineLarge = t.headlineLarge.copy(fontFamily = FontFamily.Monospace),
        headlineMedium = t.headlineMedium.copy(fontFamily = FontFamily.Monospace),
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Monospace),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Monospace),
        titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Monospace),
        titleSmall = t.titleSmall.copy(fontFamily = FontFamily.Monospace),
        bodyLarge = t.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        bodyMedium = t.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        bodySmall = t.bodySmall.copy(fontFamily = FontFamily.Monospace),
        labelLarge = t.labelLarge.copy(fontFamily = FontFamily.Monospace),
        labelMedium = t.labelMedium.copy(fontFamily = FontFamily.Monospace),
        labelSmall = t.labelSmall.copy(fontFamily = FontFamily.Monospace),
    )
}
