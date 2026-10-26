package com.monsterquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.R

/** The splash's own colors: the same dark teal and gold as the app icon, whatever the theme. */
private val SplashBg = Color(0xFF14292A)
private val SplashGold = Color(0xFFEBC678)
private val SplashText = Color(0xFFCFC6B4)

private val loadingLines = listOf(
    "Sharpening swords…",
    "Inking quills…",
    "Waking your monsters…",
    "Checking the quest board…",
)

/**
 * The loading screen when the game starts: the sword-and-quill crest, the
 * title and a loading bar, then it fades into the game. Tap to skip.
 */
@Composable
fun BootSplash(heroName: String, durationMs: Int = 1_600, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        onDone()
    }
    // The crest breathes gently while it loads.
    val pulse by rememberInfiniteTransition(label = "crest").animateFloat(
        0.97f, 1.03f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val line = loadingLines[(progress.value * loadingLines.size).toInt().coerceIn(0, loadingLines.size - 1)]
    Box(
        Modifier
            .fillMaxSize()
            .background(SplashBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(220.dp).scale(pulse),
            )
            Text(
                "Monster Quest",
                color = SplashGold,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (heroName.isBlank() || heroName == "Hero") "Your quests await" else "Welcome back, $heroName",
                color = SplashText,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            // Loading bar: a gold fill in a thin gold frame.
            Box(
                Modifier
                    .widthIn(max = 240.dp)
                    .fillMaxWidth(0.6f)
                    .height(8.dp)
                    .background(SplashGold.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.value)
                        .height(8.dp)
                        .background(SplashGold, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(line, color = SplashText.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.alpha(0.9f).padding(horizontal = 16.dp))
        }
    }
}
