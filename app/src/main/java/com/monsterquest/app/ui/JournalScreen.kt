package com.monsterquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.core.Bestiary
import com.monsterquest.core.Species

/**
 * The monster journal: every species in order. Unseen ones are shadows,
 * seen ones are shown, and caught ones are marked.
 */
@Composable
fun JournalTab(vm: GameViewModel) {
    val s = vm.state
    val all = Bestiary.all
    var open by remember { mutableStateOf<Species?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                val seen = all.count { it.id in s.seen }
                val caught = all.count { it.id in s.caught }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconText(GameIcon.BOOK, "Monster Journal", tint = Gold, textColor = Gold, size = 20.dp, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                LabeledBar("Seen", seen, all.size, MpBlue)
                Spacer(Modifier.height(4.dp))
                LabeledBar("Caught", caught, all.size, Gold)
                val shinies = all.count { it.id in s.shinyCaught }
                if (shinies > 0) IconText(GameIcon.SPARK, "Shinies caught: $shinies", tint = Gold, textColor = Gold, fontSize = 12.sp)
                Text(
                    "Battle a monster to record it; tame it to mark it caught. About 1 in 200 wild monsters is shiny (1 in 100 with a lure). Legendaries guard Gold shrines.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        items(all, key = { it.id }) { sp ->
            JournalCell(all.indexOf(sp) + 1, sp, seen = sp.id in s.seen, caught = sp.id in s.caught, shiny = sp.id in s.shinyCaught) { open = sp }
        }
    }
    open?.let { sp -> JournalEntry(vm, sp) { open = null } }
}

@Composable
private fun JournalCell(number: Int, sp: Species, seen: Boolean, caught: Boolean, shiny: Boolean, onClick: () -> Unit) {
    val border = when {
        caught -> Gold
        seen -> Line
        else -> Line.copy(alpha = 0.4f)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(142.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (caught) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = seen, onClick = onClick)
            .padding(6.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.align(Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
                Text("#%03d".format(number), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (shiny) GIcon(GameIcon.SPARK, Modifier.padding(start = 3.dp).size(11.dp), Gold)
            }
            MonsterArt(sp, Modifier.size(62.dp), silhouette = !seen)
            Text(
                if (seen) sp.name else "???",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (seen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (seen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(sp.element.icon(), Modifier.size(11.dp), sp.element.color())
                    Spacer(Modifier.width(3.dp))
                    Text(sp.element.label, fontSize = 10.sp, color = sp.element.color())
                }
            }
        }
        val mark = when {
            caught -> GameIcon.ORB to Gold
            seen -> GameIcon.EYE to MpBlue
            else -> null
        }
        mark?.let { (icon, tint) -> GIcon(icon, Modifier.size(14.dp).align(Alignment.TopEnd), tint) }
    }
}

@Composable
private fun JournalEntry(vm: GameViewModel, sp: Species, onDismiss: () -> Unit) {
    val s = vm.state
    val line = Bestiary.lineOf(sp)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sp.name) },
        text = {
            Column {
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    MonsterArt(sp, Modifier.size(120.dp))
                    // Caught a shiny one? Show its colors too.
                    if (sp.id in s.shinyCaught) MonsterArt(sp, Modifier.size(96.dp), shiny = true)
                }
                if (sp.id in s.shinyCaught) IconText(GameIcon.SPARK, "Shiny caught", tint = Gold, textColor = Gold, fontSize = 12.sp)
                Text(
                    "${sp.element.label} type • ${sp.rarity.label}" + (sp.breed?.let { " • $it breed" } ?: ""),
                    color = rarityColor(sp.rarity),
                )
                Text(
                    when {
                        sp.bossOnly -> "Found only as a guardian at Gold shrines."
                        sp.id in Bestiary.silverGuardians.map { it.id } -> "Roams the wild; also guards Silver shrines."
                        else -> "Found in the wild from level ${sp.minWildLevel}."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (sp.id in s.caught) "Caught" else "Seen, not yet caught",
                    fontSize = 13.sp,
                    color = if (sp.id in s.caught) Gold else MpBlue,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (line.size > 1) {
                    Text("Evolution line", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        line.forEach { stage ->
                            val known = stage.id in s.seen
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                MonsterArt(stage, Modifier.size(48.dp), silhouette = !known)
                                Text(if (known) stage.name else "???", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                stage.evolveLevel?.let { Text("Lv $it ->", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
