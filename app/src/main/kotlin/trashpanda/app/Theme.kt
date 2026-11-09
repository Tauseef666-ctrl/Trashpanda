package trashpanda.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme tokens straight from DESIGN §3.1. Risk triplet is identical across themes —
 * the colors mean the same thing everywhere.
 */
object Tokens {
    val bg = Color(0xFF0B0F14)
    val surface = Color(0xFF111827)
    val border = Color(0xFF1F2937)
    val text = Color(0xFFE5E7EB)
    val textDim = Color(0xFF9CA3AF)
    val primary = Color(0xFF34D399)
    val riskSafe = Color(0xFF22C55E)
    val riskReview = Color(0xFFF59E0B)
    val riskDanger = Color(0xFFEF4444)
    val focus = Color(0xFF818CF8)
}

@Composable
fun TrashPandaTheme(content: @Composable () -> Unit) {
    // v0.1 ships Dark glass by default; Light milk + Terminal neon land in P3.
    val scheme = darkColorScheme(
        background = Tokens.bg,
        surface = Tokens.surface,
        primary = Tokens.primary,
        onPrimary = Color(0xFF06281C),
        secondary = Tokens.focus,
        error = Tokens.riskDanger,
    )
    isSystemInDarkTheme() // noted; dark glass is the default regardless (design contract)
    MaterialTheme(colorScheme = scheme, content = content)
}
