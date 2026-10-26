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

    val starters = listOf("emberkit", "bubblefin", "zapmouse")

    fun chooseStarter(state: GameState, speciesId: String, rng: Random = Random.Default): Update {
        if (state.starterChosen) return Update(state, "Starter already chosen.")
        val m = Monster.create(Bestiary.get(speciesId), 3, rng)
        return Update(
            state.copy(monsters = state.monsters + m, party = listOf(m.uid), starterChosen = true).withJournal(),
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
        val h = gain.state.hero
        if (gain.levelsGained > 0 && !h.classChosen && h.level >= CLASS_UNLOCK_LEVEL) {
            append(" Choose your class on the Hero tab to claim a Rare weapon!")
        }
    }

    /** Pick a class at level 5+. The first choice is free; switching costs gold. */
    fun chooseClass(state: GameState, cls: HeroClass, rng: Random = Random.Default): Update {
        val h = state.hero
        if (h.level < CLASS_UNLOCK_LEVEL) return Update(state, "Classes unlock at level $CLASS_UNLOCK_LEVEL.")
        if (h.classChosen && h.heroClass == cls) return Update(state, "You're already a ${cls.label}.")
        val cost = if (!h.classChosen) 0 else CLASS_CHANGE_COST
        if (state.gold < cost) return Update(state, "Changing class costs $cost gold.")
        var s = state.copy(hero = h.copy(heroClass = cls, classChosen = true), gold = state.gold - cost)
        var gift = ""
        if (!h.classChosen) {
            // Picking a path at level 5 comes with a Rare weapon suited to it.
            val type = when (cls) {
                HeroClass.WARRIOR -> WeaponType.BROADSWORD
                HeroClass.MAGE -> WeaponType.STAFF
                HeroClass.ROGUE -> WeaponType.DAGGER
                HeroClass.NONE -> WeaponType.SWORD
            }
            val w = Armory.roll(maxOf(h.level, CLASS_UNLOCK_LEVEL), rng, type, minimum = GearRarity.RARE).let {
                if (it.rarity == GearRarity.RARE) it else it.copy(rarity = GearRarity.RARE, effects = it.effects.take(1))
            }
            s = s.copy(gear = s.gear + w)
            gift = " You received a Rare ${w.name}! Equip it from the Bag."
        }
        val what = if (cls == HeroClass.NONE) "You stay an Adventurer: no strengths, no weaknesses." else "You are now a ${cls.label}!"
        return Update(s, what + (if (cost > 0) " (−$cost gold)" else "") + gift)
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

    /** Claim a level upgrade: the bag and monster storage both grow. */
    fun applyUpgrade(state: GameState): Update {
        if (state.pendingUpgrades <= 0) return Update(state, "No upgrades available. One is earned every $UPGRADE_LEVEL_INTERVAL levels.")
        val s = state.copy(
            pendingUpgrades = state.pendingUpgrades - 1,
            bagCapacity = state.bagCapacity + BAG_UPGRADE_AMOUNT,
            monsterCapacity = state.monsterCapacity + STORAGE_UPGRADE_AMOUNT,
        )
        return Update(s, "Bag expanded to ${s.bagCapacity} slots and monster storage to ${s.monsterCapacity}.")
    }

    /** Pay [PAID_UPGRADE_COST] gold for [PAID_UPGRADE_AMOUNT] more bag slots or monster storage. */
    fun buyStorage(state: GameState, bag: Boolean): Update {
        if (state.gold < PAID_UPGRADE_COST) return Update(state, "Expanding storage costs $PAID_UPGRADE_COST gold.")
        val s = state.copy(gold = state.gold - PAID_UPGRADE_COST)
        return if (bag) {
            Update(s.copy(bagCapacity = s.bagCapacity + PAID_UPGRADE_AMOUNT), "Bag expanded to ${s.bagCapacity + PAID_UPGRADE_AMOUNT} slots.")
        } else {
            Update(s.copy(monsterCapacity = s.monsterCapacity + PAID_UPGRADE_AMOUNT), "Monster storage expanded to ${s.monsterCapacity + PAID_UPGRADE_AMOUNT}.")
        }
    }

    // ---- tasks -------------------------------------------------------------

    fun addTask(
        state: GameState,
        title: String,
        difficulty: Difficulty,
        rng: Random = Random.Default,
        nowMs: Long = System.currentTimeMillis(),
    ): Update {
        val t = title.trim()
        if (t.isEmpty()) return Update(state, "Quest needs a name.")
        val task = QuestTask(id = newId(rng), title = t, difficulty = difficulty, createdAtMs = nowMs)
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
            questDays = state.questDays.let { it + (epochDay(nowMs) to (it[epochDay(nowMs)] ?: 0) + 1) },
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
        if (uid in Adventures.awayUids(state)) return Update(state, "${m.displayName} is out on an adventure.")
        val gained = releaseCandy(m)
        val family = m.species.family
        return Update(
            state.copy(
                monsters = state.monsters.filterNot { it.uid == uid },
                party = state.party - uid,
                candy = state.candy + (family to (state.candy[family] ?: 0) + gained),
            ),
            "${m.displayName} was released into the wild. +$gained ${candyName(m.species)}.",
        )
    }

    // ---- candy ----------------------------------------------------------------

    /** Every evolution line has its own candy, named after its first form. */
    fun candyName(sp: Species): String = "${Bestiary.lineOf(sp).first().name} candy"

    fun candyOf(state: GameState, sp: Species): Int = state.candy[sp.family] ?: 0

    /** Releasing gives more candy for evolved and higher-level monsters. */
    fun releaseCandy(m: Monster): Int = m.species.stage + m.level / 15

    /** Candy to level up from [level]; it climbs as the monster grows. */
    fun candyCost(level: Int): Int = 1 + level / 5

    /**
     * Spend candy to raise a monster one level (it may evolve or learn moves).
     * Its stat points (IVs) never change: better ones come from taming more monsters.
     */
    fun candyLevelUp(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (m.level >= MAX_MONSTER_LEVEL) return Update(state, "${m.displayName} is already max level.")
        val cost = candyCost(m.level)
        val have = candyOf(state, m.species)
        if (have < cost) return Update(state, "Need $cost ${candyName(m.species)} (you have $have). Release ${m.species.family.removeSuffix(" line")}-line monsters to get more.")
        val (grown, notes) = grow(m, (m.xpToNext - m.xp).coerceAtLeast(1))
        val family = m.species.family
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == uid) grown else it },
                candy = state.candy + (family to have - cost),
            ),
            "${m.displayName} grew to Lv ${grown.level}! (-$cost candy)" + notes.joinToString("") { " $it" },
        )
    }

    fun rename(state: GameState, uid: String, name: String): Update {
        val n = name.trim().take(16).ifEmpty { null }
        return Update(
            state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(nickname = n) else it }),
            "Renamed.",
        )
    }

    private fun evolutionText(before: Monster, after: Monster): String =
        if (before.speciesId == after.speciesId) "" else " ${before.displayName} evolved into ${after.species.name}!"

    /**
     * Adds XP, then reports evolutions and newly learned moves. New moves fill
     * empty slots of a custom loadout; automatic loadouts update themselves.
     */
    fun grow(m: Monster, xp: Int): Pair<Monster, List<String>> {
        val before = m.learnedMoves.map { it.id }.toSet()
        var after = m.gainXp(xp)
        val notes = mutableListOf<String>()
        evolutionText(m, after).takeIf { it.isNotBlank() }?.let { notes += it.trim() }
        val newMoves = after.learnedMoves.filter { it.id !in before }
        for (mv in newMoves) {
            val custom = after.moves
            if (custom != null && custom.size < Moves.MAX_MOVES) {
                after = after.copy(moves = custom + mv.id)
                notes += "${after.displayName} learned ${mv.name}!"
            } else if (custom != null) {
                notes += "${after.displayName} can learn ${mv.name}. Swap it in from its stat page."
            } else {
                notes += "${after.displayName} learned ${mv.name}!"
            }
        }
        return after to notes
    }

    /** Sets a monster's 4 move slots (must be moves it has learned). */
    fun setMoves(state: GameState, uid: String, moveIds: List<String>): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        val learned = m.learnedMoves.map { it.id }.toSet()
        val valid = moveIds.distinct().filter { it in learned }.take(Moves.MAX_MOVES)
        if (valid.isEmpty()) return Update(state, "A monster needs at least one move.")
        return Update(
            state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(moves = valid) else it }),
            "${m.displayName}'s moves updated.",
        )
    }

    fun restore(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (!m.fainted) return Update(state, "${m.displayName} isn't hurt.")
        if (state.count(Item.RESTORE_POTION) <= 0) return Update(state, "You need a Restore Potion (Shop or loot).")
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == uid) it.copy(fainted = false, damage = 0) else it },
                inventory = removeItems(state.inventory, mapOf(Item.RESTORE_POTION to 1)),
            ),
            "${m.displayName} is back on its feet!",
        )
    }

    /**
     * Safety net: if every monster you own has fainted, your strongest one
     * recovers on its own once per day so you're never stuck.
     */
    fun dailyRecovery(state: GameState, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Update? {
        if (state.monsters.isEmpty() || state.monsters.any { !it.fainted }) return null
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        if (state.lastRecoveryDay == today) return null
        val best = state.monsters.maxBy { it.level }
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == best.uid) it.copy(fainted = false, damage = 0) else it },
                lastRecoveryDay = today,
            ),
            "${best.displayName} recovered overnight. Restore Potions heal the rest.",
        )
    }

    /** The shadow curse lifted: perfect one stat of the player's choice. */
    fun perfectStat(state: GameState, uid: String, stat: Stat): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (!m.cureReady) return Update(state, "${m.displayName} has no stat to perfect.")
        if (stat !in MONSTER_STATS) return Update(state, "Choose HP, Attack, Defense or Speed.")
        val iv = m.ivs
        val perfected = when (stat) {
            Stat.HP -> iv.copy(hp = MAX_IV)
            Stat.ATTACK -> iv.copy(attack = MAX_IV, magic = MAX_IV)
            Stat.DEFENSE -> iv.copy(defense = MAX_IV)
            Stat.SPEED -> iv.copy(speed = MAX_IV)
            Stat.MAGIC -> iv
        }
        return Update(
            state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(ivs = perfected, cureReady = false) else it }),
            "${m.displayName}'s ${stat.label} is now perfect!",
        )
    }

    /** Heal the hero ([Combatant.HERO_KEY]) or a monster with a potion outside battle. */
    fun usePotion(state: GameState, item: Item, target: String): Update {
        if (item.kind != ItemKind.HEAL_HP) return Update(state, "${item.label} can't be used here.")
        if (state.count(item) <= 0) return Update(state, "No ${item.label} left.")
        val spent = state.copy(inventory = removeItems(state.inventory, mapOf(item to 1)))
        if (target == Combatant.HERO_KEY) {
            if (state.heroDamage <= 0) return Update(state, "You're already at full health.")
            val heal = maxOf(50, (state.hero.maxHp * item.power).toInt())
            return Update(spent.copy(heroDamage = (state.heroDamage - heal).coerceAtLeast(0)), "${item.label}: you recover $heal HP.")
        }
        val m = state.monsters.find { it.uid == target } ?: return Update(state, "Monster not found.")
        if (m.fainted) return Update(state, "${m.displayName} has fainted. Only a Restore Potion can help.")
        if (m.damage <= 0) return Update(state, "${m.displayName} is already at full health.")
        val heal = maxOf(50, (m.stat(Stat.HP) * item.power).toInt())
        return Update(
            spent.copy(monsters = state.monsters.map { if (it.uid == target) it.copy(damage = (it.damage - heal).coerceAtLeast(0)) else it }),
            "${item.label}: ${m.displayName} recovers $heal HP.",
        )
    }

    fun treatXp(m: Monster): Int = maxOf(Item.TREAT.power.toInt(), m.xpToNext * 2 / 5)

    fun feedTreat(state: GameState, uid: String): Update {
        if (state.count(Item.TREAT) <= 0) return Update(state, "No treats. Buy some in the shop.")
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (m.level >= MAX_MONSTER_LEVEL) return Update(state, "${m.displayName} is already max level.")
        val xp = treatXp(m)
        val (fed, notes) = grow(m, xp)
        val lvlMsg = (if (fed.level > m.level) " Grew to Lv ${fed.level}!" else "") + notes.joinToString("") { " $it" }
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
        val level = wildLevel(state.hero.level, rng)
        val enemy = Monster.create(Bestiary.randomWild(rng, level), level, rng)
        return state.copy(huntTokens = state.huntTokens - 1) to enemy
    }

    fun markSpawnUsed(state: GameState, spawnId: String, nowMs: Long): GameState {
        val window = World.spawnWindow(nowMs)
        val pruned = state.usedSpawns.filter { (World.windowOf(it) ?: 0) >= window - 8 }.toSet()
        return state.copy(usedSpawns = pruned + spawnId)
    }

    fun newBattle(state: GameState, enemy: Monster, encounter: EncounterType, shrineId: String? = null, rng: Random = Random.Default) =
        Battle(
            state.hero,
            // Monsters out on adventures can't fight.
            state.partyMonsters.filter { it.uid !in Adventures.awayUids(state) },
            enemy, encounter, state.inventory, state.storageFull, shrineId, rng, state.equippedGear, state.heroDamage, state.equippedArmor,
        )

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
                        val (after, notes) = grow(m, xp)
                        if (after.level > m.level) levelled += "${after.displayName} → Lv ${after.level}"
                        levelled += notes
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
                    msg.append(" Found: ").append(r.drops.entries.joinToString { "${it.key.label} x${it.value}" }).append(".")
                    if (lost > 0) msg.append(" (Bag full: $lost item(s) left behind.)")
                }
                val gain = addHeroXp(s, r.heroXp)
                s = gain.state
                msg.append(levelUpText(gain))
                // Boss shrine progress
                if (r.shrineId != null && r.encounter == EncounterType.SILVER_BOSS) {
                    val rec = s.shrines[r.shrineId] ?: ShrineRecord()
                    s = s.copy(shrines = s.shrines + (r.shrineId to rec.copy(lastVisitMs = nowMs)))
                } else if (r.shrineId != null && r.encounter != EncounterType.WILD) {
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
        // Damage carries over to the next battle. A fallen hero limps home at 10% HP.
        val heroMax = s.hero.maxHp
        s = s.copy(
            heroDamage = if (r.heroFell) heroMax - maxOf(1, heroMax / 10) else (r.damage[Combatant.HERO_KEY] ?: s.heroDamage),
            monsters = s.monsters.map { m -> r.damage[m.uid]?.let { m.copy(damage = it) } ?: m },
        )
        // Monsters knocked out stay down until a Restore Potion is used.
        if (r.faintedUids.isNotEmpty()) {
            val names = s.monsters.filter { it.uid in r.faintedUids }.map { it.displayName }
            s = s.copy(monsters = s.monsters.map { if (it.uid in r.faintedUids) it.copy(fainted = true, damage = 0) else it })
            msg.append(" ${names.joinToString()} fainted and need${if (names.size == 1) "s" else ""} a Restore Potion.")
        }
        // Fighting slowly lifts the shadow curse.
        if (r.outcome == BattleOutcome.WON || r.outcome == BattleOutcome.CAPTURED) {
            val lifted = mutableListOf<String>()
            s = s.copy(
                monsters = s.monsters.map { m ->
                    if (!m.cursed || m.uid !in r.monsterXp) return@map m
                    val progress = m.cureProgress + 1
                    if (progress >= m.cureNeeded) {
                        lifted += m.displayName
                        m.copy(cursed = false, cureProgress = 0, cureReady = true)
                    } else {
                        m.copy(cureProgress = progress)
                    }
                },
            )
            lifted.forEach { msg.append(" $it's shadow curse is lifted! Choose a stat to perfect on its stat page.") }
        }
        return Update(s, msg.toString())
    }

    // ---- shrines -----------------------------------------------------------

    fun shrineCooldownLeft(state: GameState, shrine: Shrine, nowMs: Long): Long {
        val rec = state.shrines[shrine.id] ?: return 0
        val cd = when (shrine.tier) {
            ShrineTier.ITEM -> World.SHRINE_COOLDOWN_MS
            ShrineTier.SILVER -> World.SILVER_COOLDOWN_MS
            ShrineTier.GOLD -> World.BOSS_COOLDOWN_MS
        }
        return (rec.lastVisitMs + cd - nowMs).coerceAtLeast(0)
    }

    /** What an item shrine (or a phone-free session) hands out. */
    data class Loot(
        val items: Map<Item, Int> = emptyMap(),
        val weapons: List<Gear> = emptyList(),
        val armor: List<Armor> = emptyList(),
        /** Spell id -> pages. */
        val pages: Map<String, Int> = emptyMap(),
        val parts: Map<WeaponType, Int> = emptyMap(),
    )

    /**
     * Rolls item-shrine loot: weapons, spell-book pages, capture orbs and potions.
     * Pages are only for spells the hero doesn't know yet.
     */
    fun rollLoot(state: GameState, rolls: Int, rng: Random): Loot {
        val items = mutableMapOf<Item, Int>()
        val weapons = mutableListOf<Gear>()
        val armor = mutableListOf<Armor>()
        val pages = mutableMapOf<String, Int>()
        val unknownSpells = Spells.all.filter { it.id !in state.hero.knownSpells }
        repeat(rolls) {
            val r = rng.nextDouble()
            when {
                r < 0.12 -> weapons += Armory.roll(state.hero.level, rng)
                r < 0.19 -> armor += Armory.rollArmor(state.hero.level, rng)
                r < 0.23 -> items[Item.LURE] = (items[Item.LURE] ?: 0) + 1
                r < 0.46 && unknownSpells.isNotEmpty() -> {
                    val sp = unknownSpells.random(rng)
                    pages[sp.id] = (pages[sp.id] ?: 0) + 1
                }
                r < 0.71 -> {
                    val orb = if (rng.nextDouble() < 0.75) Item.TAMING_ORB else Item.GREAT_ORB
                    items[orb] = (items[orb] ?: 0) + 1
                }
                else -> {
                    // Restore Potions are deliberately rare.
                    val potion = if (rng.nextDouble() < 0.06) Item.RESTORE_POTION
                    else listOf(Item.POTION, Item.POTION, Item.POTION, Item.HI_POTION, Item.ETHER, Item.PHOENIX_DOWN).random(rng)
                    items[potion] = (items[potion] ?: 0) + 1
                }
            }
        }
        return Loot(items, weapons, armor, pages)
    }

    /** Some parts of one random weapon type. */
    fun rollParts(count: Int, rng: Random): Map<WeaponType, Int> =
        if (count <= 0) emptyMap() else mapOf(Armory.randomPartType(rng) to count)

    /** Adds loot to the save (respecting bag space) and describes it. */
    fun grantLoot(state: GameState, loot: Loot): Update {
        var s = state
        val parts = mutableListOf<String>()
        val (withItems, lostItems) = addItems(s, loot.items)
        s = withItems
        loot.items.forEach { (item, n) -> parts += "${item.label} x$n" }
        var lostWeapons = 0
        for (w in loot.weapons) {
            if (s.bagCount < s.bagCapacity) {
                s = s.copy(gear = s.gear + w)
                parts += "${w.rarity.label} ${w.name}"
            } else {
                lostWeapons++
            }
        }
        for (a in loot.armor) {
            if (s.bagCount < s.bagCapacity) {
                s = s.copy(armor = s.armor + a)
                parts += "${a.rarity.label} ${a.name}"
            } else {
                lostWeapons++
            }
        }
        for ((type, n) in loot.parts) {
            s = s.copy(parts = s.parts + (type to (s.parts[type] ?: 0) + n))
            parts += "${type.label} part x$n"
        }
        val learned = mutableListOf<String>()
        for ((spellId, n) in loot.pages) {
            val spell = Spells.byId(spellId) ?: continue
            val total = (s.spellPages[spellId] ?: 0) + n
            parts += "${spell.name} page"
            if (total >= Spells.PAGES_TO_LEARN && spellId !in s.hero.knownSpells) {
                s = s.copy(
                    hero = s.hero.copy(knownSpells = s.hero.knownSpells + spellId),
                    spellPages = s.spellPages - spellId,
                )
                learned += spell.name
            } else {
                s = s.copy(spellPages = s.spellPages + (spellId to total))
            }
        }
        val msg = buildString {
            append(parts.joinToString(", ").ifEmpty { "nothing" })
            val lost = lostItems + lostWeapons
            if (lost > 0) append(" (Bag full: $lost left behind.)")
            learned.forEach { append(" You learned $it!") }
        }
        return Update(s, msg)
    }

    /** Collect loot from an item shrine. */
    fun visitShrine(state: GameState, shrine: Shrine, nowMs: Long, rng: Random = Random.Default): Update {
        if (shrine.isBoss) return Update(state, "This is a boss shrine. Challenge the guardian!")
        if (shrineCooldownLeft(state, shrine, nowMs) > 0) return Update(state, "This shrine is recharging.")
        val gold = rng.nextInt(5, 16)
        var s = state.copy(
            gold = state.gold + gold,
            shrines = state.shrines + (shrine.id to (state.shrines[shrine.id] ?: ShrineRecord()).copy(lastVisitMs = nowMs)),
            stats = state.stats.copy(shrinesVisited = state.stats.shrinesVisited + 1),
        )
        val loot = grantLoot(s, rollLoot(s, rng.nextInt(2, 4), rng))
        s = loot.state
        return Update(s, "${shrine.name}: ${loot.message}, +$gold gold.")
    }

    // ---- weapons -----------------------------------------------------------

    const val PARTS_TO_FORGE = 5

    /** Turns [PARTS_TO_FORGE] parts of one type into a weapon of that type (better odds than shrines). */
    fun forge(state: GameState, type: WeaponType, rng: Random = Random.Default): Update {
        val have = state.parts[type] ?: 0
        if (have < PARTS_TO_FORGE) return Update(state, "Need $PARTS_TO_FORGE ${type.label.lowercase()} parts to forge.")
        if (state.bagFull) return Update(state, "Bag is full.")
        val w = Armory.roll(state.hero.level, rng, type, luck = 10)
        return Update(
            state.copy(parts = state.parts + (type to have - PARTS_TO_FORGE), gear = state.gear + w),
            "You forged a ${w.rarity.label} ${w.name} (Lv ${w.level})!",
        )
    }

    fun equip(state: GameState, id: String): Update {
        val w = state.gear.find { it.id == id } ?: return Update(state, "Weapon not found.")
        val rest = state.gear.filterNot { it.id == id }
        // The previously equipped weapon goes back in the bag (equipping frees a slot, so it fits).
        val back = state.equippedGear?.let { rest + it } ?: rest
        val fit = if (w.matches(state.hero.heroClass)) " Class bonus +${(CLASS_MATCH_BONUS * 100).toInt()}%!" else ""
        return Update(state.copy(gear = back, equippedGear = w), "Equipped ${w.name}.$fit")
    }

    fun unequip(state: GameState): Update {
        val w = state.equippedGear ?: return Update(state, "Nothing equipped.")
        if (state.bagFull) return Update(state, "Bag is full.")
        return Update(state.copy(gear = state.gear + w, equippedGear = null), "Unequipped ${w.name}.")
    }

    fun equipArmor(state: GameState, id: String): Update {
        val a = state.armor.find { it.id == id } ?: return Update(state, "Armor not found.")
        val rest = state.armor.filterNot { it.id == id }
        val back = state.equippedArmor?.let { rest + it } ?: rest
        val fit = if (a.matches(state.hero.heroClass)) " Class bonus +${(CLASS_MATCH_BONUS * 100).toInt()}%!" else ""
        return Update(state.copy(armor = back, equippedArmor = a), "Put on ${a.name}.$fit")
    }

    fun unequipArmor(state: GameState): Update {
        val a = state.equippedArmor ?: return Update(state, "No armor on.")
        if (state.bagFull) return Update(state, "Bag is full.")
        return Update(state.copy(armor = state.armor + a, equippedArmor = null), "Took off ${a.name}.")
    }

    fun dropArmor(state: GameState, id: String): Update {
        val a = state.armor.find { it.id == id } ?: return Update(state, "Armor not found.")
        return Update(state.copy(armor = state.armor.filterNot { it.id == id }), "Dropped ${a.name}.")
    }

    fun dropWeapon(state: GameState, id: String): Update {
        val w = state.gear.find { it.id == id } ?: return Update(state, "Weapon not found.")
        return Update(state.copy(gear = state.gear.filterNot { it.id == id }), "Dropped ${w.name}.")
    }

    // ---- walking -----------------------------------------------------------

    /** Steps that take activity from 0 to full. */
    const val ACTIVITY_STEPS = 1500.0
    /** Activity halves every this many minutes of standing still. */
    const val ACTIVITY_HALF_LIFE_MIN = 15.0

    /** How much you've been moving lately, 0..1. */
    fun activityLevel(state: GameState, nowMs: Long): Double {
        val minutes = ((nowMs - state.activityAtMs).coerceAtLeast(0)) / 60_000.0
        return state.activity * Math.pow(0.5, minutes / ACTIVITY_HALF_LIFE_MIN)
    }

    // ---- lures -------------------------------------------------------------

    const val LURE_MS = 30 * 60_000L

    /** One free lure each day (when there's room in the bag). */
    fun claimDailyLure(state: GameState, nowMs: Long): Update? {
        val day = epochDay(nowMs)
        if (state.lastFreeLureDay == day || state.bagFull) return null
        val (s, _) = addItems(state.copy(lastFreeLureDay = day), mapOf(Item.LURE to 1))
        return Update(s, "Your free daily Lure is in the bag. Use it from the Map to draw monsters to you.")
    }

    fun useLure(state: GameState, nowMs: Long): Update {
        if (state.count(Item.LURE) <= 0) return Update(state, "No lures. Buy one in the Shop or find them at shrines.")
        val until = maxOf(state.lureUntilMs, nowMs) + LURE_MS
        return Update(
            state.copy(inventory = removeItems(state.inventory, mapOf(Item.LURE to 1)), lureUntilMs = until),
            "Lure active! More monsters will appear around you for the next 30 minutes.",
        )
    }

    // ---- journal -----------------------------------------------------------

    fun markSeen(state: GameState, speciesId: String): GameState =
        if (speciesId in state.seen) state else state.copy(seen = state.seen + speciesId)

    fun epochDay(nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    const val STEPS_PER_REWARD = 100
    const val XP_PER_REWARD = 10
    const val HP_PER_REWARD = 10

    /**
     * A new reading from the phone's step counter (total steps since boot).
     * Steps since the previous reading count as walking; a lower reading means
     * the phone rebooted, so the new total counts from zero.
     */
    fun onStepCount(state: GameState, totalSteps: Long, nowMs: Long = System.currentTimeMillis()): Update {
        val last = state.lastStepCount
        val delta = when {
            last == null -> 0L
            totalSteps >= last -> totalSteps - last
            else -> totalSteps
        }
        val u = walk(state, delta.toInt(), nowMs)
        return Update(u.state.copy(lastStepCount = totalSteps), u.message)
    }

    /**
     * Walking [steps]: unlocking loot boxes advance, and every
     * [STEPS_PER_REWARD] steps give the hero and each battle-party monster
     * [XP_PER_REWARD] XP and heal [HP_PER_REWARD] HP. The message is empty
     * unless something notable happened (a level-up, evolution or new move).
     */
    fun walk(state: GameState, steps: Int, nowMs: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Update {
        if (steps <= 0) return Update(state, "")
        var s = LootBoxes.addWalk(state, steps * METERS_PER_MILE / STEPS_PER_MILE)
        val day = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        val today = if (s.stepsDay == day) s.stepsToday + steps else steps
        s = s.copy(
            stepsToday = today,
            stepsDay = day,
            stepHistory = s.stepHistory.filterKeys { it > day - 90 } + (day to today),
            activity = (activityLevel(s, nowMs) + steps / ACTIVITY_STEPS).coerceAtMost(1.0),
            activityAtMs = nowMs,
            stats = s.stats.copy(totalSteps = s.stats.totalSteps + steps),
        )
        val bank = s.stepBank + steps
        val rewards = bank / STEPS_PER_REWARD
        s = s.copy(stepBank = bank % STEPS_PER_REWARD)
        if (rewards == 0) return Update(s, "")

        val xp = rewards * XP_PER_REWARD
        val hp = rewards * HP_PER_REWARD
        s = s.copy(heroDamage = (s.heroDamage - hp).coerceAtLeast(0))
        val gain = addHeroXp(s, xp)
        s = gain.state
        val away = Adventures.awayUids(s)
        val notes = mutableListOf<String>()
        s = s.copy(
            monsters = s.monsters.map { m ->
                if (m.uid !in s.party || m.fainted || m.uid in away) return@map m
                val (after, n) = grow(m, xp)
                notes += n
                if (after.level > m.level) notes += "${after.displayName} reached Lv ${after.level}!"
                after.copy(damage = (after.damage - hp).coerceAtLeast(0))
            },
        )
        val text = (levelUpText(gain).trim().takeIf { it.isNotEmpty() }?.let { listOf("Walking: $it") } ?: emptyList()) + notes
        return Update(s, text.joinToString(" "))
    }

    // ---- phone-free focus sessions ------------------------------------------

    /** Grace period after starting a session to lock the phone. */
    const val FOCUS_GRACE_MS = 60_000L

    fun startFocus(state: GameState, minutes: Int, nowMs: Long, rng: Random = Random.Default): Update {
        if (state.focus != null) return Update(state, "A phone-free session is already running.")
        if (state.lootBoxes.size >= MAX_LOOT_BOXES) {
            return Update(state, "You're holding $MAX_LOOT_BOXES loot boxes. Open or discard one first.")
        }
        val session = FocusSession(newId(rng), nowMs, nowMs + minutes * 60_000L, minutes)
        return Update(state.copy(focus = session), "Phone-free for $minutes minutes. Lock your phone now!")
    }

    fun focusPenalty(state: GameState): Int = maxOf(10, state.gold * GOLD_LOSS_PERCENT / 100).coerceAtMost(state.gold)

    /**
     * Ends the current session. Success awards a loot box (rarer for longer
     * sessions) that opens after walking; failure costs gold and a hunt token.
     */
    fun resolveFocus(state: GameState, success: Boolean, rng: Random = Random.Default): Update {
        val f = state.focus ?: return Update(state, "")
        var s = state.copy(focus = null)
        return if (success) {
            s = s.copy(stats = s.stats.copy(focusWins = s.stats.focusWins + 1, focusMinutes = s.stats.focusMinutes + f.minutes))
            if (s.lootBoxes.size >= MAX_LOOT_BOXES) {
                // Shouldn't happen (starting is blocked when full), but don't lose the reward entirely.
                val gold = f.minutes
                return Update(s.copy(gold = s.gold + gold), "Phone-free for ${f.minutes} min! Box storage full, so you got $gold gold instead.")
            }
            val rarity = LootBoxes.roll(f.minutes, rng)
            s = s.copy(lootBoxes = s.lootBoxes + LootBox(newId(rng), rarity, f.endMs))
            Update(s, "Phone-free for ${f.minutes} min! You earned a Level ${rarity.level} ${rarity.label} loot box. Walk ${rarity.miles} mile(s) to open it.")
        } else {
            val loss = focusPenalty(s)
            s = s.copy(
                gold = s.gold - loss,
                huntTokens = (s.huntTokens - 1).coerceAtLeast(0),
                stats = s.stats.copy(focusFails = s.stats.focusFails + 1),
            )
            Update(s, "You picked up your phone early. Lost $loss gold and a hunt token.")
        }
    }

    fun silverLevel(heroLevel: Int): Int = maxOf(6, heroLevel + 3)

    /** Sets at least [count] random stats to a perfect IV. */
    fun withPerfectStats(m: Monster, count: Int, rng: Random): Monster {
        val perfect = MONSTER_STATS.shuffled(rng).take(count).toSet()
        fun iv(stat: Stat) = if (stat in perfect) MAX_IV else m.ivs[stat]
        return m.copy(ivs = StatBlock(iv(Stat.HP), iv(Stat.ATTACK), iv(Stat.MAGIC), iv(Stat.DEFENSE), iv(Stat.SPEED)))
    }

    /** Number of perfect (maximum) IVs, shown as stars in the UI. */
    fun perfectStatCount(m: Monster): Int = MONSTER_STATS.count { m.ivs[it] == MAX_IV }

    /**
     * Build the guardian for a silver or gold shrine, or explain why it can't
     * be fought (closed today, quest goal not met, or resting).
     */
    fun bossEncounter(
        state: GameState,
        shrine: Shrine,
        nowMs: Long,
        rng: Random = Random.Default,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Pair<Monster?, String> {
        if (!shrine.isBoss) return null to "Not a boss shrine."
        if (state.monsters.isEmpty()) return null to "You need at least one monster."
        val access = Schedule.access(state, shrine, nowMs, zone)
        if (!access.open) return null to access.reason
        val left = shrineCooldownLeft(state, shrine, nowMs)
        if (left > 0) return null to "The guardian is resting. Back in ${left / 3_600_000}h ${(left / 60_000) % 60}m."
        val species = Bestiary.get(shrine.bossSpeciesId!!)
        // Seeded so the same shrine always has the same guardian personality.
        val seeded = Random(shrine.id.hashCode())
        if (shrine.tier == ShrineTier.SILVER) {
            val guardian = Monster.create(species, silverLevel(state.hero.level), seeded).copy(uid = newId(rng))
            return withPerfectStats(guardian, 3, seeded) to "silver"
        }
        val boss = Monster.create(species, bossLevel(state.hero.level), seeded).copy(uid = newId(rng))
        val defeated = state.shrines[shrine.id]?.bossDefeated == true
        return boss to (if (defeated) "taming" else "challenge")
    }
}
