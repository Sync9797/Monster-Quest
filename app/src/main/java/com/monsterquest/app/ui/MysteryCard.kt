package com.monsterquest.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.core.Mystery

/** This week's mystery monster on Home: a silhouette and clues until you tame one. */
@Composable
fun MysteryCard(vm: GameViewModel) {
    val s = vm.state
    val sp = Mystery.species(s) ?: return
    // Solved and claimed: it moves to the Mysteries list at the bottom of Home.
    if (s.mysteryClaimed) return
    val solved = s.mysteryFound
    GameCard(Modifier.padding(top = 8.dp), border = if (solved && !s.mysteryClaimed) Gold else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonsterArt(sp, Modifier.size(72.dp), silhouette = !solved)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (solved) "Mystery of the week: ${sp.name}!" else "Mystery of the week: who's that?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Gold,
                )
                if (!solved) {
                    Mystery.hints(sp).forEach { Text("• $it", fontSize = 12.sp) }
                    Text(
                        "It turns up ${Mystery.SPAWN_BOOST.toInt()}x as often this week. Tame one for ${Mystery.GOLD} gold, ${Mystery.CANDY} candy and a ${Mystery.ITEM.label}.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (solved && !s.mysteryClaimed) Button(onClick = vm::claimMystery) { Text("Claim") }
        }
    }
}

/** Every mystery you've solved, folded away at the bottom of Home. */
@Composable
fun MysteriesFold(vm: GameViewModel) {
    val s = vm.state
    if (s.mysteriesSolved.isEmpty()) return
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    GameCard(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mysteries", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                "${s.mysteriesSolved.size} solved" + (if (s.mysteryClaimed) " • next one Monday" else "") + "  ${if (open) "\u25B2" else "\u25BC"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            s.mysteriesSolved.reversed().forEach { (id, week) ->
                val sp = com.monsterquest.core.Bestiary.get(id)
                // Weeks start on Monday (see Goals.weekOf).
                val monday = java.time.LocalDate.ofEpochDay(week * 7 - 3)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MonsterArt(sp, Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(sp.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Week of ${monday.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${monday.dayOfMonth}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
