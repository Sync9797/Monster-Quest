package com.monsterquest.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFF5C542)
val HpGreen = Color(0xFF4ADE80)
val HpYellow = Color(0xFFFACC15)
val HpRed = Color(0xFFF87171)
val MpBlue = Color(0xFF60A5FA)
val XpPurple = Color(0xFFA78BFA)
val BossRed = Color(0xFFEF4444)
val SilverColor = Color(0xFFC0C7D1)

private val Scheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Color(0xFF1A1300),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF15121F),
    onBackground = Color(0xFFECE6F5),
    surface = Color(0xFF1E1A2B),
    onSurface = Color(0xFFECE6F5),
    surfaceVariant = Color(0xFF2B2540),
    onSurfaceVariant = Color(0xFFC9BFDF),
    error = HpRed,
)

@Composable
fun MonsterQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
