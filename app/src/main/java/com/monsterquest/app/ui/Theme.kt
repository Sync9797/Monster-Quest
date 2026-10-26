package com.monsterquest.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Palette from Questbound: deep teal panels, warm gold, soft mint.
val Gold = Color(0xFFEBC678)
val Mint = Color(0xFF9CD2BD)
val HpGreen = Color(0xFF8FD4AE)
val HpYellow = Color(0xFFE9CB83)
val HpRed = Color(0xFFF2A198)
val MpBlue = Color(0xFF9CBFE4)
val XpPurple = Color(0xFFC3A6F0)
val BossRed = Color(0xFFE88A7E)
val SilverColor = Color(0xFFC0C7D1)
val Line = Color(0xFF2A4042)

/** Button/accent color per attack type. */
fun typeColor(e: com.monsterquest.core.Element): Color = when (e) {
    com.monsterquest.core.Element.NEUTRAL -> Color(0xFF4E5E5B)
    com.monsterquest.core.Element.FIRE -> Color(0xFF9A4A33)
    com.monsterquest.core.Element.ELECTRIC -> Color(0xFF8C7426)
    com.monsterquest.core.Element.FLIGHT -> Color(0xFF3F6684)
    com.monsterquest.core.Element.WATER -> Color(0xFF2E5C8A)
    com.monsterquest.core.Element.GAS -> Color(0xFF5B7334)
    com.monsterquest.core.Element.CORRUPT -> Color(0xFF5B4580)
}

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF263027),
    secondary = Mint,
    onSecondary = Color(0xFF14292A),
    tertiary = Mint,
    background = Color(0xFF0D171A),
    onBackground = Color(0xFFEEEADE),
    surface = Color(0xFF142327),
    onSurface = Color(0xFFEEEADE),
    surfaceVariant = Color(0xFF1B2E32),
    onSurfaceVariant = Color(0xFFA0B2AE),
    outline = Line,
    secondaryContainer = Color(0xFF25413D),
    onSecondaryContainer = Color(0xFFBCE5D1),
    error = HpRed,
)

/** Serif headings like Questbound; the rest stays in the system sans-serif. */
private val Type = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif),
        titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
    )
}

@Composable
fun MonsterQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
