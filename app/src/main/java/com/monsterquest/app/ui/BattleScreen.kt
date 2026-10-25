package com.monsterquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.BattleView
import com.monsterquest.app.FighterView
import com.monsterquest.core.BattleAction
import com.monsterquest.core.BattleOutcome
import com.monsterquest.core.Item
import com.monsterquest.core.ItemKind
import com.monsterquest.core.Skill
import com.monsterquest.core.SkillKind

private sealed interface Menu {
    data object Main : Menu
    data object Magic : Menu
    data object Items : Menu
    data object Tame : Menu
    /** Pick an ally for a heal spell or item. */
    data class Target(val spell: Skill? = null, val item: Item? = null) : Menu
}

@Composable
fun BattleScreen(b: BattleView, onAction: (BattleAction) -> Unit, onClose: () -> Unit) {
    var menu by remember { mutableStateOf<Menu>(Menu.Main) }
    // Return to the main menu whenever the turn passes to someone else.
    LaunchedEffect(b.current?.key, b.round, b.log.size) { menu = Menu.Main }

    // Enemy + log on top / left; party + commands on bottom / right. On a
    // half-folded phone in tabletop posture the controls sit on the lower half.
    val top: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().clipToBounds()) {
            EnemyPanel(b)
            Spacer(Modifier.height(8.dp))
            BattleLog(b.log, Modifier.weight(1f))
        }
    }
    val controls: @Composable () -> Unit = {
        Column {
            AllyRow(b)
            Spacer(Modifier.height(8.dp))
            if (b.outcome == BattleOutcome.ONGOING) {
                val actor = b.current
                if (actor != null) {
                    ActionPanel(b, actor, menu, onMenu = { menu = it }, onAction = onAction)
                }
            } else {
                ResultPanel(b, onClose)
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AdaptivePanes(
            modifier = Modifier.safeDrawingPadding().padding(12.dp),
            compact = {
                // Commands may use at most ~55% of the height; the enemy and log
                // shrink (and clip) instead of being drawn underneath them.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val controlsMax = maxHeight * 0.55f
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { top() }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().heightIn(max = controlsMax)) { controls() }
                    }
                }
            },
            first = top,
            second = {
                Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.BottomCenter) { controls() }
            },
        )
    }
}

@Composable
private fun EnemyPanel(b: BattleView) {
    val e = b.enemy
    GameCard(border = if (b.isBoss) BossRed else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.emoji, fontSize = 52.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (b.isBoss) Text("BOSS", color = BossRed, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text(
                    "${e.name}  Lv ${e.level}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${e.element.emoji} ${e.element.label}", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Bar(e.hpFraction, hpColor(e.hpFraction), Modifier.fillMaxWidth(), heightDp = 10)
                Text("${e.hp} / ${e.maxHp} HP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BattleLog(log: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1) }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
    ) {
        itemsIndexed(log) { i, line ->
            Text(
                line,
                fontSize = 14.sp,
                color = if (i >= log.size - 3) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AllyRow(b: BattleView) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        b.allies.forEach { a ->
            AllyCard(a, active = a.key == b.current?.key, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AllyCard(a: FighterView, active: Boolean, modifier: Modifier) {
    val bg = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (a.alive) a.emoji else "💤", fontSize = 26.sp)
        Text(
            a.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Gold else MaterialTheme.colorScheme.onSurface,
        )
        Bar(a.hpFraction, hpColor(a.hpFraction), Modifier.fillMaxWidth(), heightDp = 6)
        Text("${a.hp}/${a.maxHp}", fontSize = 10.sp)
        if (a.isHero) {
            Bar(a.mp.toFloat() / a.maxMp.coerceAtLeast(1), MpBlue, Modifier.fillMaxWidth(), heightDp = 4)
            Text("⚡${a.mp}/${a.maxMp}", fontSize = 10.sp)
        }
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color) else ButtonDefaults.buttonColors(),
    ) {
        Text(text, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionPanel(
    b: BattleView,
    actor: FighterView,
    menu: Menu,
    onMenu: (Menu) -> Unit,
    onAction: (BattleAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("What will ${actor.name} do?", fontWeight = FontWeight.Bold, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!actor.isHero && b.specialName != null) {
            Text(
                "Special: ${b.specialName}" + if (b.specialCooldown > 0) " (ready in ${b.specialCooldown})" else " (ready)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        when (menu) {
            Menu.Main -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("⚔️ Attack", Modifier.weight(1f)) { onAction(BattleAction.Attack) }
                    if (actor.isHero) {
                        ActionButton("✨ Magic", Modifier.weight(1f), enabled = b.spells.isNotEmpty()) { onMenu(Menu.Magic) }
                    } else {
                        val ready = b.specialCooldown == 0
                        ActionButton(
                            if (ready) "💥 Special" else "💥 (${b.specialCooldown})",
                            Modifier.weight(1f),
                            enabled = ready && b.specialName != null,
                        ) { onAction(BattleAction.Special) }
                    }
                    ActionButton("🛡️ Defend", Modifier.weight(1f)) { onAction(BattleAction.Defend) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton(
                        "🎒 Items (${b.itemsLeft})",
                        Modifier.weight(1f),
                        enabled = b.itemsLeft > 0 && b.items.isNotEmpty(),
                    ) { onMenu(Menu.Items) }
                    ActionButton(
                        "🔮 Tame",
                        Modifier.weight(1f),
                        color = if (b.captureBlocked == null) Color(0xFF7C3AED) else null,
                    ) { onMenu(Menu.Tame) }
                    ActionButton("🏃 Flee", Modifier.weight(1f), color = Color(0xFF4B5563)) { onAction(BattleAction.Flee) }
                }
            }
            Menu.Magic -> {
                b.spells.forEach { sp ->
                    val affordable = actor.mp >= sp.mpCost
                    OutlinedButton(
                        onClick = {
                            if (sp.kind == SkillKind.HEAL) onMenu(Menu.Target(spell = sp))
                            else onAction(BattleAction.CastSpell(sp.id))
                        },
                        enabled = affordable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${sp.element.emoji} ${sp.name}  •  ⚡${sp.mpCost}  •  ${if (sp.kind == SkillKind.HEAL) "heal" else "power ${sp.power}"}")
                    }
                }
                BackButton(onMenu)
            }
            Menu.Items -> {
                Text("${b.itemsLeft} item use(s) left this battle", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                b.items.forEach { (item, count) ->
                    OutlinedButton(onClick = { onMenu(Menu.Target(item = item)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${item.emoji} ${item.label} x$count")
                    }
                }
                BackButton(onMenu)
            }
            Menu.Tame -> {
                val blocked = b.captureBlocked
                if (blocked != null) {
                    Text(blocked, color = HpYellow)
                } else {
                    b.orbs.forEach { o ->
                        OutlinedButton(onClick = { onAction(BattleAction.Capture(o.item)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${o.item.emoji} ${o.item.label} x${o.count}  •  ${(o.odds * 100).toInt()}% chance")
                        }
                    }
                }
                BackButton(onMenu)
            }
            is Menu.Target -> {
                val item = menu.item
                val spell = menu.spell
                Text("Choose a target", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                b.allies.forEach { a ->
                    val valid = when {
                        item?.kind == ItemKind.REVIVE -> !a.alive
                        item?.kind == ItemKind.HEAL_MP -> a.isHero && a.alive
                        else -> a.alive
                    }
                    OutlinedButton(
                        onClick = {
                            if (spell != null) onAction(BattleAction.CastSpell(spell.id, a.key))
                            else if (item != null) onAction(BattleAction.UseItem(item, a.key))
                        },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${a.emoji} ${a.name}  ${a.hp}/${a.maxHp} HP" + if (a.isHero) "  ⚡${a.mp}/${a.maxMp}" else "")
                    }
                }
                BackButton(onMenu)
            }
        }
    }
}

@Composable
private fun BackButton(onMenu: (Menu) -> Unit) {
    OutlinedButton(onClick = { onMenu(Menu.Main) }, modifier = Modifier.fillMaxWidth()) { Text("← Back") }
}

@Composable
private fun ResultPanel(b: BattleView, onClose: () -> Unit) {
    val (title, color) = when (b.outcome) {
        BattleOutcome.WON -> "🏆 Victory!" to Gold
        BattleOutcome.CAPTURED -> "🔮 Tamed!" to XpPurple
        BattleOutcome.LOST -> "💀 Defeat" to HpRed
        BattleOutcome.FLED -> "🏃 Escaped" to MaterialTheme.colorScheme.onSurfaceVariant
        BattleOutcome.ONGOING -> "" to Color.Unspecified
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            b.resultText?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}
