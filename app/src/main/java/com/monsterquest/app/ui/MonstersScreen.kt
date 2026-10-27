package com.monsterquest.app.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.monsterquest.core.trait
import com.monsterquest.core.canEvolveTo

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Adventures
import com.monsterquest.core.Bestiary
import com.monsterquest.core.Element
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Item
import com.monsterquest.core.MAX_IV
import com.monsterquest.core.MAX_PARTY_SIZE
import com.monsterquest.core.MAX_SWAPS
import com.monsterquest.core.MONSTER_STATS
import com.monsterquest.core.Monster
import com.monsterquest.core.Move
import com.monsterquest.core.Moves
import com.monsterquest.core.Rarity
import com.monsterquest.core.Stat

fun rarityColor(r: Rarity) = when (r) {
    Rarity.COMMON -> Color(0xFFB0B0B0)
    Rarity.UNCOMMON -> HpGreen
    Rarity.RARE -> MpBlue
    Rarity.EPIC -> XpPurple
    Rarity.LEGENDARY -> Gold
}

@Composable
fun MonstersScreen(vm: GameViewModel) {
    // 0 = collection, 1 = journal, 2 = adventures
    var tab by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("Collection", { tab = 0 }, Modifier.weight(1f), outlined = tab != 0)
            SmallButton("Journal", { tab = 1 }, Modifier.weight(1f), outlined = tab != 1)
            val away = vm.state.adventures.size
            SmallButton(
                "Adventures" + if (away > 0) " ($away)" else "",
                { tab = 2 },
                Modifier.weight(1.2f),
                outlined = tab != 2,
            )
        }
        when (tab) {
            1 -> JournalTab(vm)
            2 -> AdventuresTab(vm)
            else -> MonsterCollection(vm)
        }
    }
}

@Composable
private fun MonsterCollection(vm: GameViewModel) {
    val s = vm.state
    var selectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = s.monsters.find { it.uid == selectedUid }
    // Back from a monster's profile returns to the list instead of closing the app.
    BackHandler(enabled = selected != null) { selectedUid = null }

    // Party first, then by level.
    val sorted = s.monsters.sortedWith(compareBy<Monster> { it.uid !in s.party }.thenByDescending { it.level })

    val list: @Composable () -> Unit = {
        MonsterList(vm, sorted, selectedUid) { selectedUid = it }
    }
    AdaptivePanes(
        firstWeight = 0.45f,
        compact = {
            if (selected != null) {
                MonsterDetail(vm, selected, onBack = { selectedUid = null })
            } else {
                list()
            }
        },
        first = list,
        second = {
            val shown = selected ?: sorted.firstOrNull()
            if (shown != null) MonsterDetail(vm, shown, onBack = null)
        },
    )
}

private enum class SortBy(val label: String) { LEVEL("Level"), TYPE("Type"), NAME("Name"), NEWEST("Newest"), POTENTIAL("Potential") }

/** Party slots on top, then a tidy grid of the rest of the collection. */
@Composable
private fun MonsterList(vm: GameViewModel, sorted: List<Monster>, selectedUid: String?, onSelect: (String) -> Unit) {
    val s = vm.state
    var sortBy by rememberSaveable { mutableStateOf(SortBy.LEVEL) }
    var showChart by remember { mutableStateOf(false) }
    val away = Adventures.awayUids(s)
    val bench = s.monsters.filter { it.uid !in s.team }.let { list ->
        when (sortBy) {
            SortBy.LEVEL -> list.sortedByDescending { it.level }
            SortBy.TYPE -> list.sortedWith(compareBy<Monster> { it.species.element }.thenByDescending { it.level })
            SortBy.NAME -> list.sortedBy { it.displayName }
            SortBy.NEWEST -> list.reversed()
            // Best genes first (ties: higher level first).
            SortBy.POTENTIAL -> list.sortedWith(compareByDescending<Monster> { it.potential }.thenByDescending { it.level })
        }
    }
    val drag = remember { PartyDrag() }
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    fun drop() {
        val uid = drag.uid ?: return
        val slot = drag.hoveredSlot()
        when {
            slot != null && slot >= MAX_PARTY_SIZE -> vm.setSwapSlot(uid, slot - MAX_PARTY_SIZE)
            slot != null -> vm.setPartySlot(uid, slot)
            // A party or swap member dropped anywhere else goes back to the collection.
            uid in s.team -> vm.toggleParty(uid)
        }
        drag.uid = null
    }
    val hovered = if (drag.uid != null) drag.hoveredSlot() else null

    // While dragging a monster, hold it near the top or bottom edge and the list scrolls that way
    // (faster the closer to the edge), so the party slots can be reached from anywhere.
    val gridState = rememberLazyGridState()
    var listHeight by remember { mutableStateOf(0f) }
    val edge = with(LocalDensity.current) { 96.dp.toPx() }
    val maxStep = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(drag.uid) {
        if (drag.uid == null) return@LaunchedEffect
        while (drag.uid != null) {
            val y = drag.pos.y - listOrigin.y
            val step = when {
                y < edge -> -maxStep * ((edge - y) / edge).coerceIn(0f, 1f)
                y > listHeight - edge -> maxStep * ((y - (listHeight - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f) gridState.scrollBy(step)
            withFrameNanos { }
        }
    }

    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            listOrigin = it.positionInRoot()
            listHeight = it.size.height.toFloat()
        },
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // Scrolled out of view: its slots can't be dropped on.
                DisposableEffect(Unit) { onDispose { drag.slots.clear() } }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Battle party", style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showChart = true }) { Text("Type chart") }
                    }
                    Text(
                        "Long-press a monster and drag it onto a slot. Drag a party member out to bench it.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    // Five slots: three on top, two below (so each tile stays a usable size).
                    (0 until MAX_PARTY_SIZE).chunked(3).forEach { row ->
                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { i ->
                            val m = s.partyMonsters.getOrNull(i)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { drag.slots[i] = it.boundsInRoot() }
                                    .border(if (hovered == i) 3.dp else 0.dp, if (hovered == i) Gold else Color.Transparent, RoundedCornerShape(12.dp)),
                            ) {
                                if (m != null) {
                                    MonsterTile(
                                        m, inParty = true, away = m.uid in away, selected = m.uid == selectedUid,
                                        modifier = Modifier
                                            .dragSource(drag, m.uid, ::drop)
                                            .alpha(if (drag.uid == m.uid) 0.35f else 1f),
                                    ) { onSelect(m.uid) }
                                } else {
                                    EmptySlot(highlight = drag.uid != null)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    }
                    // Swap slots: they come along and can be sent in mid-battle.
                    SectionTitle("Swap slots", Modifier.padding(top = 4.dp))
                    Text(
                        "Sent in during battle with Swap, e.g. when a monster faints. Half XP unless they fight.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(MAX_SWAPS) { j ->
                            val i = MAX_PARTY_SIZE + j
                            val m = s.swapMonsters.getOrNull(j)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { drag.slots[i] = it.boundsInRoot() }
                                    .border(if (hovered == i) 3.dp else 0.dp, if (hovered == i) Gold else Color.Transparent, RoundedCornerShape(12.dp)),
                            ) {
                                if (m != null) {
                                    MonsterTile(
                                        m, inParty = true, away = m.uid in away, selected = m.uid == selectedUid,
                                        modifier = Modifier
                                            .dragSource(drag, m.uid, ::drop)
                                            .alpha(if (drag.uid == m.uid) 0.35f else 1f),
                                    ) { onSelect(m.uid) }
                                } else {
                                    EmptySlot(highlight = drag.uid != null)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Collection", style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.weight(1f))
                        Text("${s.monsters.size}/${s.monsterCapacity}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Bar(s.monsters.size.toFloat() / s.monsterCapacity, if (s.storageFull) HpRed else Mint, Modifier.fillMaxWidth().padding(vertical = 4.dp), heightDp = 4)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SortBy.entries.forEach { o -> SmallButton(o.label, { sortBy = o }, outlined = o != sortBy) }
                    }
                }
            }
            if (bench.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Everyone you own is in the party. Tame more monsters to fill your collection.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(bench, key = { it.uid }) { m ->
                MonsterTile(
                    m, inParty = false, away = m.uid in away, selected = m.uid == selectedUid,
                    showPotential = sortBy == SortBy.POTENTIAL,
                    modifier = Modifier
                        .dragSource(drag, m.uid, ::drop)
                        .alpha(if (drag.uid == m.uid) 0.35f else 1f),
                ) { onSelect(m.uid) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
        // The monster being dragged follows your finger.
        val dragged = drag.uid?.let { uid -> s.monsters.find { it.uid == uid } }
        if (dragged != null) {
            val half = with(LocalDensity.current) { 40.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((drag.pos.x - listOrigin.x - half).roundToInt(), (drag.pos.y - listOrigin.y - half * 1.4f).roundToInt()) }
                    .size(80.dp)
                    .shadow(8.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.dp, Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MonsterArt(dragged.species, Modifier.size(66.dp), shiny = dragged.shiny)
            }
        }
    }
    if (showChart) {
        AlertDialog(
            onDismissRequest = { showChart = false },
            title = { Text("Type chart") },
            text = { TypeChart() },
            confirmButton = { TextButton(onClick = { showChart = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun EmptySlot(highlight: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(162.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(if (highlight) 2.dp else 1.dp, if (highlight) Gold else Line, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (highlight) "Drop here" else "Empty slot", fontSize = 12.sp, color = if (highlight) Gold else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Drag-and-drop state for moving monsters into and out of the battle party. */
private class PartyDrag {
    var uid by mutableStateOf<String?>(null)
    /** Finger position, in root coordinates. */
    var pos by mutableStateOf(Offset.Zero)
    val slots = mutableStateMapOf<Int, Rect>()

    fun hoveredSlot(): Int? = slots.entries.firstOrNull { it.value.contains(pos) }?.key
}

/** Long-press a tile and drag it; dropping is handled by [onDrop]. */
private fun Modifier.dragSource(drag: PartyDrag, uid: String, onDrop: () -> Unit): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val pinnable = androidx.compose.ui.layout.LocalPinnableContainer.current
    var pin by remember { mutableStateOf<androidx.compose.ui.layout.PinnableContainer.PinnedHandle?>(null) }
    // The gesture outlives recompositions, so always call the latest drop handler.
    val latestDrop by rememberUpdatedState(onDrop)
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(uid) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Keep this tile alive while the list scrolls under the finger.
                    pin = pinnable?.pin()
                    drag.uid = uid
                    drag.pos = origin + offset
                },
                onDrag = { change, _ ->
                    change.consume()
                    // The tile moves when the list auto-scrolls, so work from where the finger is on screen.
                    drag.pos = origin + change.position
                },
                onDragEnd = {
                    latestDrop()
                    pin?.release()
                    pin = null
                },
                onDragCancel = {
                    drag.uid = null
                    pin?.release()
                    pin = null
                },
            )
        }
}

/** Compact tile: art, name, level and type, HP, and status badges. */
@Composable
private fun MonsterTile(
    m: Monster,
    inParty: Boolean,
    away: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** Show the monster's potential (when the list is sorted by it). */
    showPotential: Boolean = false,
    onClick: () -> Unit,
) {
    val sp = m.species
    val border = when {
        selected -> MpBlue
        m.fainted -> HpRed
        m.cursed -> XpPurple
        inParty -> Gold
        else -> Line
    }
    Column(
        modifier
            .fillMaxWidth()
            .height(162.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (border == Line) 1.dp else 2.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MonsterArt(sp, Modifier.size(64.dp), dim = m.fainted, shiny = m.shiny)
        Text(m.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(sp.element.icon(), Modifier.size(11.dp), sp.element.color())
            Spacer(Modifier.width(3.dp))
            Text("Lv ${m.level}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showPotential) {
                Text("  •  ${m.potential}%", fontSize = 11.sp, color = if (m.potential >= 80) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        val hpMax = m.stats.hp
        Bar(m.currentHp.toFloat() / hpMax, hpColor(m.currentHp.toFloat() / hpMax), Modifier.fillMaxWidth(), heightDp = 4)
        val badge = when {
            m.fainted -> "Fainted" to HpRed
            away -> "Away" to MpBlue
            m.cureReady -> "Cured!" to Gold
            m.cursed -> "Cursed" to XpPurple
            else -> null
        }
        badge?.let { (t, c) -> Text(t, fontSize = 10.sp, color = c, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** Full stat page for one monster, with comparison and move management. */
@Composable
private fun MonsterDetail(vm: GameViewModel, m: Monster, onBack: (() -> Unit)?) {
    val s = vm.state
    val sp = m.species
    val inParty = m.uid in s.party
    var compareUid by remember(m.uid) { mutableStateOf<String?>(null) }
    val other = s.monsters.find { it.uid == compareUid && it.uid != m.uid }
    var editingSlot by remember(m.uid) { mutableStateOf<Int?>(null) }
    var releasing by remember { mutableStateOf(false) }
    var usingPotion by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("All monsters") }
        }
        GameCard(border = if (m.fainted) HpRed else if (inParty) Gold else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonsterArt(sp, Modifier.size(120.dp), dim = m.fainted, shiny = m.shiny)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.displayName, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m.shiny) IconText(GameIcon.SPARK, "Shiny", tint = Gold, textColor = Gold, bold = true, fontSize = 13.sp, size = 14.dp)
                    IconText(sp.element.icon(), "Lv ${m.level} ${sp.name} • ${sp.element.label}", tint = sp.element.color(), fontSize = 13.sp, size = 14.dp)
                    Row {
                        Text(sp.rarity.label, fontSize = 12.sp, color = rarityColor(sp.rarity))
                        if (sp.stages > 1) Text("  • Stage ${sp.stage}/${sp.stages}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        sp.breed?.let { Text("  • $it breed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (m.fainted) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hours = GameEngine.hoursUntilAwake(m)
                    Text(
                        "Knocked out. Resting, it wakes up in about ${if (hours <= 1) "an hour" else "$hours hours"}; a Restore Potion works now.",
                        color = HpRed,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    SmallButton(
                        "Restore (${s.count(Item.RESTORE_POTION)})",
                        { vm.restore(m.uid) },
                        enabled = s.count(Item.RESTORE_POTION) > 0,
                    )
                }
            }
            s.adventures.find { m.uid in it.monsterUids }?.let { a ->
                Text(
                    "Away: ${a.adventure.title}. Back in ${formatDuration((a.endMs - System.currentTimeMillis()).coerceAtLeast(0))}.",
                    fontSize = 13.sp,
                    color = MpBlue,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (!m.fainted) {
                LabeledBar("HP", m.currentHp, m.stats.hp, hpColor(m.currentHp.toFloat() / m.stats.hp))
                if (m.damage > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("Heals 10 HP every 100 steps in your party.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        SmallButton("Use potion", { usingPotion = true }, outlined = true, enabled = s.count(Item.POTION) + s.count(Item.HI_POTION) > 0)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            LabeledBar("XP", m.xp, m.xpToNext, XpPurple)
            sp.evolveLevel?.let { lvl -> if (m.level < lvl) Text("Can evolve at Lv $lvl", fontSize = 12.sp, color = XpPurple) }
        }

        // Evolution is the player's choice, paid for with candy of the monster's line.
        val into = m.canEvolveTo
        if (into != null) {
            val cost = GameEngine.evolveCost(m)
            val have = GameEngine.candyOf(s, sp)
            var confirmEvolve by remember { mutableStateOf(false) }
            Spacer(Modifier.height(8.dp))
            GameCard(border = Gold) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonsterArt(into, Modifier.size(56.dp), silhouette = into.id !in s.seen)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ready to evolve!", fontWeight = FontWeight.Bold, color = Gold)
                        Text(
                            "Into ${if (into.id in s.seen) into.name else "a new creature"} • $cost ${GameEngine.candyName(sp)} (you have $have)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (have < cost) {
                            Text("Tame or beat more of its kind for candy.", fontSize = 11.sp, color = HpYellow)
                        }
                    }
                    SmallButton("Evolve", { confirmEvolve = true }, enabled = have >= cost)
                }
            }
            if (confirmEvolve) {
                AlertDialog(
                    onDismissRequest = { confirmEvolve = false },
                    title = { Text("Evolve ${m.displayName}?") },
                    text = { Text("It becomes ${into.name}, with much stronger stats and new moves. Costs $cost ${GameEngine.candyName(sp)}.") },
                    confirmButton = { TextButton(onClick = { vm.evolve(m.uid); confirmEvolve = false }) { Text("Evolve") } },
                    dismissButton = { TextButton(onClick = { confirmEvolve = false }) { Text("Not yet") } },
                )
            }
        }

        if (m.cursed) {
            Spacer(Modifier.height(8.dp))
            GameCard(border = XpPurple) {
                Text("Shadow curse", style = MaterialTheme.typography.titleMedium, color = XpPurple)
                Text("Defense −50%, Attack +25%. Battling together slowly lifts it.", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                LabeledBar("Cure progress (battles won)", m.cureProgress, m.cureNeeded, XpPurple)
            }
        }
        if (m.cureReady) {
            Spacer(Modifier.height(8.dp))
            GameCard(border = Gold) {
                Text("The curse is lifted!", style = MaterialTheme.typography.titleMedium, color = Gold)
                Text("Choose one stat to make perfect:", fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MONSTER_STATS.forEach { st -> SmallButton(st.label, { vm.perfectStat(m.uid, st) }, Modifier.weight(1f)) }
                }
            }
        }

        // ---- Candy ----
        val candy = GameEngine.candyOf(s, sp)
        val cost = GameEngine.candyCost(m.level)
        GameCard(Modifier.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(GameIcon.CANDY, Modifier.size(26.dp), sp.element.color())
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${GameEngine.candyName(sp)}: $candy", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tame (+3) or release monsters of this line to earn it. Next level costs $cost.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmallButton(
                    "Level up ($cost)",
                    { vm.candyLevelUp(m.uid) },
                    enabled = candy >= cost && m.level < com.monsterquest.core.MAX_MONSTER_LEVEL,
                )
            }
        }

        // ---- Stats + compare ----
        SectionTitle("Stats")
        Text(
            "${m.personality.label}: ${m.personality.description}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val perfect = GameEngine.perfectStatCount(m)
        Text(
            "Potential ${m.potential}%" + if (perfect > 0) "  •  $perfect perfect stat${if (perfect == 1) "" else "s"}" else "",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Its skill: one per kind of monster, gained at level 30 and stronger every 10 levels.
        val skill = com.monsterquest.core.MonsterTraits.of(m.species)
        val skillRank = m.trait?.second ?: 0
        GameCard(Modifier.padding(top = 6.dp), border = if (skillRank > 0) XpPurple else null) {
            if (skillRank > 0) {
                Text(
                    "Skill: ${skill.label}  ${"★".repeat(skillRank)}${"☆".repeat(com.monsterquest.core.MAX_SKILL_RANK - skillRank)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = XpPurple,
                )
                Text(skill.describe(skillRank, hero = false) + ".", fontSize = 12.sp)
                if (skillRank < com.monsterquest.core.MAX_SKILL_RANK) {
                    Text(
                        "Ranks up every ${com.monsterquest.core.SKILL_RANK_LEVELS} levels.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text("Skill: ${skill.label} (at level ${com.monsterquest.core.MONSTER_SKILL_LEVEL})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(skill.describe(1, hero = false) + ".", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Stat points (IVs): fixed at taming, so better monsters come from taming more.
        GameCard(Modifier.padding(top = 6.dp)) {
            Text("Stat points", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                MONSTER_STATS.forEach { st ->
                    val iv = m.ivs[st]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$iv/$MAX_IV", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (iv == MAX_IV) Gold else statColor(st))
                        Text(st.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Text(
                "Set when tamed. Leveling up and evolving never change them; tame more monsters to find better ones.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Compare with:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("None", { compareUid = null }, outlined = other != null)
            s.monsters.filter { it.uid != m.uid }.forEach { o ->
                SmallButton(o.displayName, { compareUid = o.uid }, outlined = o.uid != compareUid)
            }
        }
        GameCard {
            if (other != null) {
                Row {
                    Spacer(Modifier.weight(1f))
                    Text(m.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(other.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val scaleMax = (s.monsters + listOfNotNull(other)).flatMap { mon -> MONSTER_STATS.map { mon.stat(it) } }.maxOrNull() ?: 1
            MONSTER_STATS.forEach { stat -> StatLine(m, other, stat, scaleMax) }
        }

        // ---- Moves ----
        SectionTitle("Moves")
        Text(
            if (m.moves == null) "Automatic loadout (best moves). Tap a slot to choose your own."
            else "Custom loadout. Tap a slot to swap a move.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val active = m.activeMoves
        (0 until Moves.MAX_MOVES).forEach { i ->
            val mv = active.getOrNull(i)
            MoveCard(mv, sp.element, onClick = { editingSlot = i })
        }
        if (m.moves != null) {
            TextButton(onClick = { vm.resetMoves(m.uid) }) { Text("Reset to automatic") }
        }
        val upcoming = Moves.learnable(sp).filter { it.first > m.level }
        if (upcoming.isNotEmpty()) {
            Text("Learns later", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            upcoming.forEach { (lvl, mv) ->
                Text(
                    "Lv $lvl: ${mv.name} (${mv.type.label}" + (if (mv.isDamaging) ", ${mv.power})" else ")"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Type & evolution ----
        SectionTitle("Type")
        Text(
            "Strong vs ${sp.element.beats.joinToString(" & ") { it.label }} • Weak to ${sp.element.weakTo.joinToString(" & ") { it.label }}",
            fontSize = 13.sp,
        )
        val line = Bestiary.lineOf(sp)
        if (line.size > 1) {
            Text(
                "Evolution: " + line.joinToString(" → ") { x ->
                    val tag = x.evolveLevel?.let { " (Lv $it)" } ?: ""
                    if (x.id == sp.id) "[${x.name}]$tag" else "${x.name}$tag"
                },
                fontSize = 13.sp,
                color = XpPurple,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ---- Actions ----
        SectionTitle("Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(
                when {
                    m.uid in s.swaps -> "Off swap"
                    inParty -> "Bench"
                    else -> "To party"
                },
                { vm.toggleParty(m.uid) },
                Modifier.weight(1f),
            )
            SmallButton("Treat +${GameEngine.treatXp(m)} XP", { vm.feedTreat(m.uid) }, Modifier.weight(1f), enabled = s.count(Item.TREAT) > 0, outlined = true)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("Rename", { renaming = true }, Modifier.weight(1f), outlined = true)
            SmallButton("Release", { releasing = true }, Modifier.weight(1f), outlined = true)
        }
        Spacer(Modifier.height(24.dp))
    }

    editingSlot?.let { slot ->
        val current = m.activeMoves
        val options = m.learnedMoves.filter { it !in current }
        AlertDialog(
            onDismissRequest = { editingSlot = null },
            title = { Text("Slot ${slot + 1}: choose a move") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (options.isEmpty()) Text("${m.displayName} hasn't learned any other moves yet.")
                    options.forEach { mv ->
                        MoveCard(mv, sp.element) {
                            val ids = current.map { it.id }.toMutableList()
                            if (slot < ids.size) ids[slot] = mv.id else ids += mv.id
                            vm.setMoves(m.uid, ids)
                            editingSlot = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editingSlot = null }) { Text("Close") } },
        )
    }
    if (usingPotion) {
        val item = if (s.count(Item.HI_POTION) > 0 && m.damage > m.stats.hp / 2) Item.HI_POTION else if (s.count(Item.POTION) > 0) Item.POTION else Item.HI_POTION
        PotionTargetDialog(vm, item) { usingPotion = false }
    }
    if (releasing) {
        AlertDialog(
            onDismissRequest = { releasing = false },
            title = { Text("Release ${m.displayName}?") },
            text = {
                Text(
                    "It will return to the wild forever. This frees one storage slot and gives you " +
                        "${GameEngine.releaseCandy(m)} ${GameEngine.candyName(sp)}.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.release(m.uid); releasing = false }) { Text("Release") } },
            dismissButton = { TextButton(onClick = { releasing = false }) { Text("Keep") } },
        )
    }
    if (renaming) {
        var name by remember(m.uid) { mutableStateOf(m.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nickname") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, singleLine = true, label = { Text(sp.name) })
            },
            confirmButton = { TextButton(onClick = { vm.rename(m.uid, name); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

/** One stat row: label with personality arrow and a star for a perfect gene, a bar, and an optional comparison. */
@Composable
private fun StatLine(m: Monster, other: Monster?, stat: Stat, scaleMax: Int) {
    val v = m.stat(stat)
    val mod = m.personality.modifier(stat)
    val arrow = when {
        mod > 1.0 -> " ↑"
        mod < 1.0 -> " ↓"
        else -> ""
    }
    val perfect = m.ivs[stat] == MAX_IV
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(stat.label + arrow, fontSize = 13.sp)
                if (perfect) {
                    Spacer(Modifier.width(4.dp))
                    GIcon(GameIcon.STAR, Modifier.size(13.dp), Gold)
                }
            }
            Text(v.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(if (other != null) 1.4f else 0.4f))
            if (other != null) {
                val ov = other.stat(stat)
                val diff = v - ov
                Text(
                    "$ov  " + when {
                        diff > 0 -> "(+$diff)"
                        diff < 0 -> "($diff)"
                        else -> "(=)"
                    },
                    fontSize = 13.sp,
                    color = when {
                        diff > 0 -> HpGreen
                        diff < 0 -> HpRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
        Bar(v.toFloat() / scaleMax, statColor(stat), Modifier.fillMaxWidth())
        if (other != null) {
            Spacer(Modifier.height(2.dp))
            Bar(other.stat(stat).toFloat() / scaleMax, statColor(stat).copy(alpha = 0.45f), Modifier.fillMaxWidth(), heightDp = 5)
        }
    }
}

private fun statColor(stat: Stat): Color = when (stat) {
    Stat.HP -> HpGreen
    Stat.ATTACK -> HpRed
    Stat.DEFENSE -> MpBlue
    Stat.SPEED -> HpYellow
    Stat.MAGIC -> XpPurple
}

@Composable
private fun MoveCard(mv: Move?, ownType: Element, onClick: () -> Unit) {
    GameCard(Modifier.padding(vertical = 3.dp).clickable(onClick = onClick), border = mv?.let { typeColor(it.type) }) {
        if (mv == null) {
            Text("— Empty slot —", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(mv.type.icon(), Modifier.size(16.dp), mv.type.color())
                Spacer(Modifier.width(6.dp))
                Text(mv.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    (if (mv.isDamaging) "Pow ${mv.power}" else "Status") + " • Acc ${mv.accuracy}" +
                        if (mv.type == ownType && mv.isDamaging) " • +20%" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(mv.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The rock-paper-scissors cycle: each type beats the next one. */
@Composable
fun TypeChart() {
    GameCard {
        Element.typed.forEach { t ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(92.dp)) { IconText(t.icon(), t.label, tint = t.color(), fontSize = 13.sp, size = 14.dp) }
                Text("beats ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                t.beats.forEachIndexed { i, b ->
                    if (i > 0) Text(" & ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconText(b.icon(), b.label, tint = b.color(), fontSize = 13.sp, size = 14.dp)
                }
            }
        }
        Text(
            "Every type beats two types and is weak to two. Super effective hits deal ×1.5; hitting a type that beats yours deals ×0.67. " +
                "Moves matching the monster's own type get +20%. Normal moves are neutral against everything.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
