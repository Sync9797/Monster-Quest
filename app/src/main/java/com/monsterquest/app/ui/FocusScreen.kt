package com.monsterquest.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.FOCUS_DURATIONS
import com.monsterquest.core.FocusSession
import com.monsterquest.core.GameEngine
import com.monsterquest.core.LootBoxes
import com.monsterquest.core.MAX_LOOT_BOXES
import kotlinx.coroutines.delay

/** Card on the Quests tab for starting a phone-free session. */
@Composable
fun FocusCard(vm: GameViewModel) {
    var minutes by remember { mutableIntStateOf(30) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start either way; without notifications the session still works, it's just silent.
        vm.startFocus(minutes)
    }
    GameCard(border = MpBlue) {
        Text("📵 Phone-free challenge", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            "Start a timer and lock your phone. Keep it locked until time's up to earn rewards.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Two rows of three so the chips fit on the narrowest screens.
        FOCUS_DURATIONS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    SmallButton("$m min", { minutes = m }, Modifier.weight(1f), outlined = m != minutes)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Win a loot box: " + LootBoxes.odds(minutes).entries.joinToString("  ") { (r, pct) -> "${r.emoji} $pct%" },
            fontSize = 12.sp,
            color = HpGreen,
        )
        Text(
            "Boxes unlock by walking: 📦 1 mi • 🎁 3 mi • 💎 5 mi • 👑 10 mi. Level 4 👑 boxes (boss inside!) need 60+ min.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Unlock early: −${GameEngine.focusPenalty(vm.state)} gold, −1 hunt token",
            fontSize = 12.sp,
            color = HpRed,
        )
        Spacer(Modifier.height(8.dp))
        val full = vm.state.lootBoxes.size >= MAX_LOOT_BOXES
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.startFocus(minutes)
            },
            enabled = !full,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (full) "Box storage full ($MAX_LOOT_BOXES). Open one in the Bag" else "Start $minutes-minute session") }
    }
}

/** Shown while a session runs: a countdown and a reminder to lock the phone. */
@Composable
fun FocusOverlay(session: FocusSession, onGiveUp: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val left = (session.endMs - now).coerceAtLeast(0)
    val graceLeft = ((session.startMs + GameEngine.FOCUS_GRACE_MS - now) / 1000).coerceAtLeast(0)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📵", fontSize = 64.sp)
                Text("Phone-free session", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
                Spacer(Modifier.height(12.dp))
                val h = left / 3_600_000
                val m = (left / 60_000) % 60
                val sec = (left / 1000) % 60
                Text(
                    if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (graceLeft > 0) "Lock your phone now! ($graceLeft s)"
                    else "Unlocking your phone before the timer ends fails the session.",
                    textAlign = TextAlign.Center,
                    color = if (graceLeft > 0) HpYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onGiveUp) { Text("Give up (−gold, −1 hunt token)") }
            }
        }
    }
}
