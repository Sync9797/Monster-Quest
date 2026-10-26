package com.monsterquest.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon

private val Dim = Color(0xFF3A4446)
private val DimBright = Color(0xFF5E6B6D)

/**
 * Battery saver: a pure black screen at minimum brightness (OLED pixels are
 * off) that keeps the screen awake so walking and spawns keep being tracked.
 * Press and hold anywhere to unlock.
 */
@Composable
fun BatterySaverOverlay(vm: GameViewModel) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        window?.let {
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.attributes = it.attributes.apply { screenBrightness = 0.01f }
            WindowCompat.getInsetsController(it, it.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.attributes = it.attributes.apply {
                    screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    // Back doesn't leave the saver screen by accident; press and hold to unlock.
    BackHandler {}
    val pulse by rememberInfiniteTransition(label = "saver").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse",
    )
    val s = vm.state
    val nearby = vm.spawnsNear(System.currentTimeMillis()).count {
        (vm.distanceTo(it.lat, it.lon) ?: Double.MAX_VALUE) <= com.monsterquest.app.ALERT_RANGE_M
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { vm.batterySaver = false })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GIcon(GameIcon.MOON, Modifier.size(40.dp).alpha(pulse), DimBright)
            Spacer(Modifier.height(12.dp))
            Text("Battery saver", color = DimBright, fontSize = 18.sp)
            Text("%,d steps today".format(s.stepsToday), color = Dim, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                if (nearby == 1) "1 monster nearby" else "$nearby monsters nearby",
                color = Dim,
                fontSize = 14.sp,
            )
            vm.lastSpawnAlert?.let {
                Text(it, color = DimBright, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp))
            }
        }
        Text(
            "Press and hold anywhere to unlock",
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        )
    }
}
