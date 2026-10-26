package com.monsterquest.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.core.Bestiary
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Moves

@Composable
fun StarterScreen(onChoose: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⚔️ Monster Quest", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Gold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Complete real-life quests to grow stronger. Tame monsters, conquer shrines, and become a legend.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text("Choose your first companion", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                GameEngine.starters.map { Bestiary.get(it) }.forEach { sp ->
                    GameCard(Modifier.padding(vertical = 6.dp).clickable { onChoose(sp.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sp.emoji, fontSize = 44.sp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(sp.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "${sp.element.emoji} ${sp.element.label} • Moves: " +
                                        Moves.autoLoadout(sp, 3).joinToString { it.name },
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "Strong vs ${sp.element.beats?.label} • Weak to ${sp.element.weakTo?.label}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Every monster rolls a random personality and hidden potential, so no two are alike.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

