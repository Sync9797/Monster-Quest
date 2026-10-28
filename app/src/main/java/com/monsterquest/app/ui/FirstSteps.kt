package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.core.Tutorial

/** First steps on Home: a checklist that ticks itself off as you play, each with a small reward. */
@Composable
fun FirstStepsCard(vm: GameViewModel) {
    val s = vm.state
    val claimed = s.tutorialClaimed
    GameCard(Modifier.padding(top = 8.dp), border = Gold) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                IconText(GameIcon.STAR, "First steps  ${claimed.size}/${Tutorial.steps.size}", tint = Gold, textColor = Gold, bold = true, fontSize = 14.sp)
            }
            TextButton(onClick = vm::dismissTutorial) { Text("Hide", fontSize = 12.sp) }
        }
        Tutorial.steps.forEach { step ->
            val done = step.done(s)
            val got = step.id in claimed
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(if (got || done) GameIcon.CHECK else GameIcon.TARGET, Modifier.size(16.dp), if (got || done) HpGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (got) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (!got) Text(step.hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (done && !got) Button(onClick = { vm.claimTutorialStep(step.id) }) { Text("+${step.gold}") }
            }
        }
        Text(
            "Finish them all: +${Tutorial.BONUS_GOLD} gold, " + Tutorial.BONUS_ITEMS.entries.joinToString { "${it.key.label} x${it.value}" } + ".",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The first thing a new player sees after choosing a starter: how the game works, in four lines. */
@Composable
fun WelcomeDialog(heroName: String, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome, $heroName!") },
        text = {
            Column {
                WelcomeLine(GameIcon.QUESTS, "Quests are your real to-dos. Finish them for XP, gold and hunt tokens.")
                WelcomeLine(GameIcon.MAP, "The Map is your neighborhood: walk to shrines for items and to monsters to battle and tame them.")
                WelcomeLine(GameIcon.WALK, "Walking levels you and your monsters up, heals them and opens loot boxes.")
                WelcomeLine(GameIcon.PHONE_OFF, "Phone-free time earns loot boxes; two hours is the jackpot.")
                Text("Your First steps checklist on Home walks you through it, with rewards.", fontSize = 13.sp, color = Gold, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("Let's go") } },
    )
}

@Composable
private fun WelcomeLine(icon: GameIcon, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        GIcon(icon, Modifier.size(20.dp), Gold)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp)
    }
}
