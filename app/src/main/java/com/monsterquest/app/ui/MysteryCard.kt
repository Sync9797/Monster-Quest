package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                } else if (s.mysteryClaimed) {
                    Text("Solved and claimed. A new mystery arrives Monday.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (solved && !s.mysteryClaimed) Button(onClick = vm::claimMystery) { Text("Claim") }
        }
    }
}
