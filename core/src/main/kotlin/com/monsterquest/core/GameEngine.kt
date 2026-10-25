package com.monsterquest.core

import kotlin.random.Random

/** A state change plus a message to show the player. */
data class Update(val state: GameState, val message: String)

/** An event read from the phone's calendar. */
data class CalendarEvent(
    /** Unique per occurrence, e.g. "eventId:beginMs". */
    val key: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendarName: String?,
)

object GameEngine {

    val starters = listOf("emberpup", "bubblefin", "sproutling")

    fun chooseStarter(state: GameState, speciesId: String, rng: Random = Random.Default): Update {
        if (state.starterChosen) return Update(state, "Starter already chosen.")
        val m = Monster.create(Bestiary.get(speciesId), 3, rng)
        return Update(
            state.copy(monsters = state.monsters + m, party = listOf(m.uid), starterChosen = true),
            "${m.species.name} joins you! Personality: ${m.personality.label} (${m.personality.description}).",
        )
    }

    // ---- hero progression --------------------------------------------------

    data class XpGain(val state: GameState, val levelsGained: Int, val upgradesGained: Int)

    fun addHeroXp(state: GameState, amount: Int): XpGain {
        var hero = state.hero
        var xp = hero.xp + amount
        var levels = 0
        var upgrades = 0
        while (xp >= heroXpForNext(hero.level)) {
            xp -= heroXpForNext(hero.level)
            hero = hero.copy(level = hero.level + 1, unspentPoints = hero.unspentPoints + STAT_POINTS_PER_LEVEL)
            levels++
            if (hero.level % UPGRADE_LEVEL_INTERVAL == 0) upgrades++
        }
        hero = hero.copy(xp = xp)
        return XpGain(
            state.copy(hero = hero, pendingUpgrades = state.pendingUpgrades + upgrades),
            levels,
            upgrades,
        )
    }

    private fun levelUpText(gain: XpGain): String = buildString {
        if (gain.levelsGained > 0) append(" LEVEL UP! Now level ${gain.state.hero.level}.")
        if (gain.upgradesGained > 0) append(" Storage upgrade unlocked!")
    }

    fun allocatePoint(state: GameState, attr: HeroAttribute): Update {
        val h = state.hero
        if (h.unspentPoints <= 0) return Update(state, "No points to spend.")
        val newAttrs = h.attributes + (attr to h.attr(attr) + 1)
        return Update(
            state.copy(hero = h.copy(attributes = newAttrs, unspentPoints = h.unspentPoints - 1)),
            "${attr.label} increased to ${h.attr(attr) + 1}.",
        )
    }

    fun applyUpgrade(state: GameState, bag: Boolean): Update {
        if (state.pendingUpgrades <= 0) return Update(state, "No upgrades available. One is earned every $UPGRADE_LEVEL_INTERVAL levels.")
        val s = state.copy(pendingUpgrades = state.pendingUpgrades - 1)
        return if (bag) {
            Update(s.copy(bagCapacity = s.bagCapacity + BAG_UPGRADE_AMOUNT), "Bag expanded to ${s.bagCapacity + BAG_UPGRADE_AMOUNT} slots.")
        } else {
            Update(
                s.copy(monsterCapacity = s.monsterCapacity + STORAGE_UPGRADE_AMOUNT),
                "Monster storage expanded to ${s.monsterCapacity + STORAGE_UPGRADE_AMOUNT}.",
            )
        }
    }

    // ---- tasks -------------------------------------------------------------

    fun addTask(state: GameState, title: String, difficulty: Difficulty, rng: Random = Random.Default): Update {
        val t = title.trim()
        if (t.isEmpty()) return Update(state, "Quest needs a name.")
        val task = QuestTask(id = newId(rng), title = t, difficulty = difficulty)
        return Update(state.copy(tasks = state.tasks + task), "Quest added: $t")
    }

    fun deleteTask(state: GameState, id: String): Update =
        Update(state.copy(tasks = state.tasks.filterNot { it.id == id }), "Quest removed.")

    fun completeTask(state: GameState, id: String, nowMs: Long): Update {
        val task = state.tasks.find { it.id == id } ?: return Update(state, "Quest not found.")
        if (task.completed) return Update(state, "Already completed.")
        val d = task.difficulty
        var s = state.copy(
            tasks = state.tasks.map { if (it.id == id) it.copy(completedAtMs = nowMs) else it },
            gold = state.gold + d.gold,
            huntTokens = minOf(MAX_HUNT_TOKENS, state.huntTokens + 1),
            stats = state.stats.copy(tasksCompleted = state.stats.tasksCompleted + 1),
        )
        val gain = addHeroXp(s, d.xp)
        s = gain.state
        return Update(s, "Quest complete! +${d.xp} XP, +${d.gold} gold, +1 hunt token.${levelUpText(gain)}")
    }

    fun difficultyFor(event: CalendarEvent): Difficulty {
        if (event.allDay) return Difficulty.NORMAL
        val minutes = (event.endMs - event.startMs) / 60_000
        return when {
            minutes <= 30 -> Difficulty.EASY
            minutes >= 120 -> Difficulty.HARD
            else -> Difficulty.NORMAL
        }
    }

    /**
     * Replace not-yet-completed calendar quests with [events]. Completed quests
     * are kept (their rewards were already paid) and never re-added.
     */
    fun syncCalendar(state: GameState, events: List<CalendarEvent>): Update {
        val completedKeys = state.tasks.filter { it.source == TaskSource.CALENDAR && it.completed }.map { it.id }.toSet()
        val kept = state.tasks.filter { it.source == TaskSource.MANUAL || it.completed }
        val fresh = events.filter { it.key !in completedKeys }.map {
            QuestTask(
                id = it.key,
                title = it.title.ifBlank { "(Untitled event)" },
                difficulty = difficultyFor(it),
                source = TaskSource.CALENDAR,
                startMs = it.startMs,
                endMs = it.endMs,
                calendarName = it.calendarName,
            )
        }
        return Update(state.copy(tasks = kept + fresh), "Synced ${fresh.size} calendar quest(s).")
    }

    // ---- items -------------------------------------------------------------

    /** Adds items while respecting bag capacity. Returns the new state and what didn't fit. */
    fun addItems(state: GameState, items: Map<Item, Int>): Pair<GameState, Int> {
        var inv = state.inventory
        var space = state.bagCapacity - state.bagCount
        var lost = 0
        for ((item, qty) in items) {
            val fit = minOf(qty, space.coerceAtLeast(0))
            if (fit > 0) inv = inv + (item to (inv[item] ?: 0) + fit)
            space -= fit
            lost += qty - fit
        }
        return state.copy(inventory = inv) to lost
    }

    private fun removeItems(inv: Map<Item, Int>, used: Map<Item, Int>): Map<Item, Int> {
        val out = inv.toMutableMap()
        for ((item, n) in used) {
            val left = (out[item] ?: 0) - n
            if (left <= 0) out.remove(item) else out[item] = left
        }
        return out
    }

    fun buy(state: GameState, item: Item, qty: Int = 1): Update {
        if (item.price <= 0) return Update(state, "${item.label} can't be bought.")
        val cost = item.price * qty
        if (state.gold < cost) return Update(state, "Not enough gold.")
        if (state.bagCount + qty > state.bagCapacity) return Update(state, "Bag is full (${state.bagCount}/${state.bagCapacity}).")
        val (s, _) = addItems(state.copy(gold = state.gold - cost), mapOf(item to qty))
        return Update(s, "Bought ${item.label}.")
    }

    fun discard(state: GameState, item: Item): Update {
        if (state.count(item) <= 0) return Update(state, "None to discard.")
        return Update(state.copy(inventory = removeItems(state.inventory, mapOf(item to 1))), "Discarded ${item.label}.")
    }

    // ---- monsters ----------------------------------------------------------

    fun toggleParty(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        return if (uid in state.party) {
            Update(state.copy(party = state.party - uid), "${m.displayName} left the battle party.")
        } else if (state.party.size >= MAX_PARTY_SIZE) {
            Update(state, "Battle party is full ($MAX_PARTY_SIZE). Remove a monster first.")
        } else {
            Update(state.copy(party = state.party + uid), "${m.displayName} joined the battle party.")
        }
    }

    fun release(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (state.monsters.size <= 1) return Update(state, "You can't release your last monster.")
        return Update(
            state.copy(monsters = state.monsters.filterNot { it.uid == uid }, party = state.party - uid),
            "${m.displayName} was released into the wild.",
        )
    }

    fun rename(state: GameState, uid: String, name: String): Update {
        val n = name.trim().take(16).ifEmpty { null }
        return Update(
            state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(nickname = n) else it }),
            "Renamed.",
        )
    }

    fun treatXp(m: Monster): Int = maxOf(Item.TREAT.power.toInt(), m.xpToNext * 2 / 5)

    fun feedTreat(state: GameState, uid: String): Update {
        if (state.count(Item.TREAT) <= 0) return Update(state, "No treats. Buy some in the shop.")
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (m.level >= MAX_MONSTER_LEVEL) return Update(state, "${m.displayName} is already max level.")
        val xp = treatXp(m)
        val fed = m.gainXp(xp)
        val lvlMsg = if (fed.level > m.level) " Grew to Lv ${fed.level}!" else ""
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == uid) fed else it },
                inventory = removeItems(state.inventory, mapOf(Item.TREAT to 1)),
            ),
            "${m.displayName} gobbles the treat. +$xp XP.$lvlMsg",
        )
    }

    // ---- encounters --------------------------------------------------------

    fun wildLevel(heroLevel: Int, rng: Random): Int = (heroLevel + rng.nextInt(-2, 3)).coerceAtLeast(1)

    fun bossLevel(heroLevel: Int): Int = maxOf(8, heroLevel + 5)

    /** Spends a hunt token for a wild encounter anywhere. */
    fun startHunt(state: GameState, rng: Random = Random.Default): Pair<GameState, Monster?> {
        if (state.huntTokens <= 0) return state to null
        val species = Bestiary.randomWild(rng)
        val enemy = Monster.create(species, wildLevel(state.hero.level, rng), rng)
        return state.copy(huntTokens = state.huntTokens - 1) to enemy
    }

    fun markSpawnUsed(state: GameState, spawnId: String, nowMs: Long): GameState {
        val window = World.spawnWindow(nowMs)
        val pruned = state.usedSpawns.filter { (World.windowOf(it) ?: 0) >= window - 8 }.toSet()
        return state.copy(usedSpawns = pruned + spawnId)
    }

    fun newBattle(state: GameState, enemy: Monster, encounter: EncounterType, shrineId: String? = null, rng: Random = Random.Default) =
        Battle(state.hero, state.partyMonsters, enemy, encounter, state.inventory, state.storageFull, shrineId, rng)

    fun applyBattleResult(state: GameState, r: BattleResult, nowMs: Long): Update {
        var s = state.copy(inventory = removeItems(state.inventory, r.itemsUsed))
        val msg = StringBuilder()
        when (r.outcome) {
            BattleOutcome.WON, BattleOutcome.CAPTURED -> {
                if (r.outcome == BattleOutcome.WON) {
                    msg.append("Victory! +${r.heroXp} XP, +${r.goldWon} gold.")
                    s = s.copy(gold = s.gold + r.goldWon, stats = s.stats.copy(battlesWon = s.stats.battlesWon + 1))
                } else {
                    msg.append("${r.enemy.displayName} was tamed! +${r.heroXp} XP.")
                }
                // Monster XP
                val levelled = mutableListOf<String>()
                s = s.copy(
                    monsters = s.monsters.map { m ->
                        val xp = r.monsterXp[m.uid] ?: return@map m
                        val after = m.gainXp(xp)
                        if (after.level > m.level) levelled += "${after.displayName} → Lv ${after.level}"
                        after
                    },
                )
                if (levelled.isNotEmpty()) msg.append(" ").append(levelled.joinToString(", ")).append("!")
                // Capture
                r.captured?.let { c ->
                    if (!s.storageFull) {
                        val party = if (s.party.size < MAX_PARTY_SIZE) s.party + c.uid else s.party
                        s = s.copy(
                            monsters = s.monsters + c,
                            party = party,
                            stats = s.stats.copy(monstersTamed = s.stats.monstersTamed + 1),
                        )
                        msg.append(" Personality: ${c.personality.label} (${c.personality.description}).")
                    }
                }
                // Drops
                if (r.drops.isNotEmpty()) {
                    val (withDrops, lost) = addItems(s, r.drops)
                    s = withDrops
                    msg.append(" Found: ").append(r.drops.entries.joinToString { "${it.key.emoji}${it.key.label} x${it.value}" }).append(".")
                    if (lost > 0) msg.append(" (Bag full: $lost item(s) left behind.)")
                }
                val gain = addHeroXp(s, r.heroXp)
                s = gain.state
                msg.append(levelUpText(gain))
                // Boss shrine progress
                if (r.shrineId != null && r.encounter != EncounterType.WILD) {
                    val rec = s.shrines[r.shrineId] ?: ShrineRecord()
                    val firstDefeat = !rec.bossDefeated && r.outcome == BattleOutcome.WON
                    val updated = rec.copy(
                        lastVisitMs = nowMs,
                        bossDefeated = true,
                        bossCaptured = rec.bossCaptured || r.outcome == BattleOutcome.CAPTURED,
                    )
                    s = s.copy(shrines = s.shrines + (r.shrineId to updated))
                    if (firstDefeat) {
                        s = s.copy(stats = s.stats.copy(bossesDefeated = s.stats.bossesDefeated + 1))
                        msg.append(" The shrine's boss can now be tamed on your next visit!")
                    }
                }
            }
            BattleOutcome.LOST -> {
                val loss = s.gold * GOLD_LOSS_PERCENT / 100
                s = s.copy(gold = s.gold - loss, stats = s.stats.copy(battlesLost = s.stats.battlesLost + 1))
                msg.append("Defeated... You dropped $loss gold while fleeing.")
            }
            BattleOutcome.FLED -> msg.append("You escaped.")
            BattleOutcome.ONGOING -> {}
        }
        return Update(s, msg.toString())
    }

    // ---- shrines -----------------------------------------------------------

    fun shrineCooldownLeft(state: GameState, shrine: Shrine, nowMs: Long): Long {
        val rec = state.shrines[shrine.id] ?: return 0
        val cd = if (shrine.isBoss) World.BOSS_COOLDOWN_MS else World.SHRINE_COOLDOWN_MS
        return (rec.lastVisitMs + cd - nowMs).coerceAtLeast(0)
    }

    /** Collect loot from a normal shrine. */
    fun visitShrine(state: GameState, shrine: Shrine, nowMs: Long, rng: Random = Random.Default): Update {
        if (shrine.isBoss) return Update(state, "This is a boss shrine. Challenge the guardian!")
        if (shrineCooldownLeft(state, shrine, nowMs) > 0) return Update(state, "This shrine is recharging.")
        val lvl = state.hero.level
        val gold = rng.nextInt(15, 41) + lvl * 2
        val xp = 10 + lvl * 2
        val pool = listOf(Item.POTION, Item.POTION, Item.TAMING_ORB, Item.TAMING_ORB, Item.ETHER, Item.GREAT_ORB, Item.TREAT)
        val loot = mutableMapOf<Item, Int>()
        repeat(rng.nextInt(1, 3)) { val i = pool.random(rng); loot[i] = (loot[i] ?: 0) + 1 }
        var s = state.copy(
            gold = state.gold + gold,
            huntTokens = minOf(MAX_HUNT_TOKENS, state.huntTokens + 1),
            shrines = state.shrines + (shrine.id to (state.shrines[shrine.id] ?: ShrineRecord()).copy(lastVisitMs = nowMs)),
            stats = state.stats.copy(shrinesVisited = state.stats.shrinesVisited + 1),
        )
        val (withLoot, lost) = addItems(s, loot)
        s = withLoot
        val gain = addHeroXp(s, xp)
        s = gain.state
        val lootText = loot.entries.joinToString { "${it.key.emoji}${it.key.label} x${it.value}" }
        val lostText = if (lost > 0) " (Bag full: $lost left behind.)" else ""
        return Update(s, "${shrine.name}: +$gold gold, +$xp XP, +1 hunt token, $lootText.$lostText${levelUpText(gain)}")
    }

    /** Build the boss for a boss shrine, or explain why it can't be fought. */
    fun bossEncounter(state: GameState, shrine: Shrine, nowMs: Long, rng: Random = Random.Default): Pair<Monster?, String> {
        if (!shrine.isBoss) return null to "Not a boss shrine."
        if (state.monsters.isEmpty()) return null to "You need at least one monster."
        val left = shrineCooldownLeft(state, shrine, nowMs)
        if (left > 0) return null to "The guardian is resting. Back in ${left / 3_600_000}h ${(left / 60_000) % 60}m."
        val species = Bestiary.get(shrine.bossSpeciesId!!)
        // Seeded so the same shrine always has the same boss personality.
        val seeded = Random(shrine.id.hashCode())
        val boss = Monster.create(species, bossLevel(state.hero.level), seeded).copy(uid = newId(rng))
        val defeated = state.shrines[shrine.id]?.bossDefeated == true
        return boss to (if (defeated) "taming" else "challenge")
    }
}
