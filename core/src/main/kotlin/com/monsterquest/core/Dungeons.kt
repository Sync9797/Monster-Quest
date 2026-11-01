package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.random.Random

/** Where a dungeon is: its look, the type of monsters inside, and its names. */
@Serializable
enum class DungeonTheme(val label: String, val element: Element, val names: List<String>) {
    CAVE("Cave", Element.EARTH, listOf("Mossy Cave", "Hollow Burrow", "Echo Cavern")),
    CRYPT("Crypt", Element.CORRUPT, listOf("Forgotten Crypt", "Bone Vault", "Silent Catacomb")),
    FORGE("Forge", Element.FIRE, listOf("Ember Forge", "Ashen Furnace", "Cinder Halls")),
    GROTTO("Grotto", Element.WATER, listOf("Sunken Grotto", "Tide Caves", "Drowned Temple")),
    SPIRE("Spire", Element.ELECTRIC, listOf("Storm Spire", "Static Tower", "Thunder Keep")),
    ROOST("Roost", Element.FLIGHT, listOf("Sky Roost", "Wind Ruins", "Cloud Aerie")),
    MIRE("Mire", Element.GAS, listOf("Fume Mire", "Spore Hollow", "Rotting Marsh")),
}

/** What waits behind a door. */
@Serializable
enum class RoomKind(val label: String, val hint: String) {
    MONSTERS("Monster lair", "Monsters to fight, and to tame"),
    ELITE("Alpha's den", "One big Alpha with perfect genes"),
    COMBAT("Guarded hall", "Cursed guardians (can't be tamed)"),
    TRAP("Trap", "A risky trap: you'll see the odds first"),
    TREASURY("Treasury", "A chest of loot"),
    FOUNTAIN("Fairy fountain", "Restores you and your monsters"),
    EMPTY("Quiet room", "Nothing at all"),
    BOSS("Champion", "The dungeon's champion"),
    /** The tutorial's first room: choose your starter. */
    STARTER("Starter's chamber", "Choose your first monster"),
    ;

    val isFight: Boolean get() = this in setOf(MONSTERS, ELITE, COMBAT, BOSS)
}

/** A dungeon on the map. Everything inside is drawn from [seed], so it's the same each time you look. */
@Serializable
data class Dungeon(
    val id: String,
    val name: String,
    val theme: DungeonTheme,
    /** 1 (easiest) to 5 (hardest). */
    val stars: Int,
    /** The level of what's inside (and the recommended level). */
    val level: Int,
    val lat: Double,
    val lon: Double,
    val seed: Long,
    /** The 0-star tutorial: one of a kind, every room shown once, and it can't be lost. */
    val tutorial: Boolean = false,
    /** The real place it's in, e.g. "Blue Bottle Coffee" (null for scattered ones). */
    val place: String? = null,
) {
    /** Rooms (each a choice of two doors) before the champion: more the harder it is. */
    val rooms: Int get() = if (tutorial) Dungeons.TUTORIAL_ROOMS.size else 2 + stars * 2
    val torchCost: Int get() = if (tutorial) 0 else (stars + 1) / 2
    /** The champion's class (the boss is a hero, with monsters of the dungeon's type). */
    val bossClass: HeroClass get() = listOf(HeroClass.WARRIOR, HeroClass.MAGE, HeroClass.ROGUE)[Math.floorMod(seed, 3L).toInt()]
    /** The stat the champion's defeat raises for good. */
    val bossStat: HeroAttribute get() = Dungeons.statFor(bossClass)
}

/** A dungeon in progress: how far in, and what's been found so far (banked when you leave or win). */
@Serializable
data class DungeonRun(
    val dungeon: Dungeon,
    /** Rooms passed (0 = at the first pair of doors). */
    val step: Int = 0,
    /** The room being fought now (the battle decides what happens next). */
    val fighting: RoomKind? = null,
    val gold: Int = 0,
    val items: Map<Item, Int> = emptyMap(),
    val torches: Int = 0,
    val pages: Map<String, Int> = emptyMap(),
    val gear: List<Gear> = emptyList(),
    val armor: List<Armor> = emptyList(),
    /** What happened in the last room, to show. */
    val last: String = "",
    /** The champion is beaten. */
    val cleared: Boolean = false,
) {
    val atBoss: Boolean get() = step >= dungeon.rooms
}

/** One reward in a champion's pool, with its chance (percent). */
data class BossReward(val label: String, val pct: Int)

/**
 * Dungeons: entered with torches, a few rooms deep (more the harder it is),
 * each room a choice of two doors drawn at random for the difficulty. At the
 * end waits a champion (a hero of one of the classes); beating them raises a
 * stat for good and rolls the rare reward pool. Leave any time and keep what
 * you found; fall, and half of it is lost.
 */
object Dungeons {
    const val RANGE_M = 100.0

    /** The tutorial's rooms in order: one of each kind, then its champion. */
    val TUTORIAL_ROOMS = listOf(RoomKind.STARTER, RoomKind.TREASURY, RoomKind.MONSTERS, RoomKind.TRAP, RoomKind.COMBAT, RoomKind.FOUNTAIN)

    /** The tutorial dungeon (0 stars): a Warrior champion, a cave, level 1. */
    val tutorialDungeon = Dungeon("tutorial", "Hollow of Beginnings", DungeonTheme.CAVE, 0, 1, 0.0, 0.0, seed = 3L, tutorial = true)

    /** Starts the tutorial dungeon for a new player (once). */
    fun startTutorial(state: GameState): GameState =
        if (state.tutorialDungeonDone || state.dungeonRun != null) state
        else state.copy(
            dungeonRun = DungeonRun(tutorialDungeon, last = "Your adventure begins in the Hollow of Beginnings. Every room here teaches you something, and nothing here can beat you."),
            tutorialIntroSeen = true,
        )

    /** The tutorial's first room: your starter joins you. */
    fun pickStarter(state: GameState, speciesId: String, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        if (!run.dungeon.tutorial || run.step != 0) return Update(state, "")
        val u = GameEngine.chooseStarter(state, speciesId, rng)
        return advance(u.state, run, u.message + " Together you head deeper in.")
    }
    private const val CELL_DEG = 0.007 // ~780 m
    private const val CELL_RADIUS = 2
    const val CHANCE = 0.45

    fun statFor(cls: HeroClass): HeroAttribute = when (cls) {
        HeroClass.WARRIOR -> HeroAttribute.ATTACK
        HeroClass.MAGE -> HeroAttribute.MAGIC
        HeroClass.ROGUE -> HeroAttribute.SPEED
        HeroClass.NONE -> HeroAttribute.DEFENSE
    }

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun seed(vararg parts: Long): Long {
        var h = 1469598103934665603L
        for (p in parts) {
            h = (h xor p) * 1099511628211L
            h = h xor (h ushr 29)
        }
        return h
    }

    /** Today's dungeons around a location, every difficulty in reach (ones you cleared today excluded). */
    fun near(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Dungeon> {
        val day = epochDay(nowMs, zone)
        val cx = floor(lat / CELL_DEG).toLong()
        val cy = floor(lon / CELL_DEG).toLong()
        val out = mutableListOf<Dungeon>()
        for (dx in -CELL_RADIUS..CELL_RADIUS) for (dy in -CELL_RADIUS..CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val s = seed(x, y, day, 0xD06E)
            val r = Random(s)
            if (r.nextDouble() > CHANCE) continue
            val id = "dg:$day:$x:$y"
            if (id in state.dungeonsCleared) continue
            val theme = DungeonTheme.entries.random(r)
            val stars = 1 + r.nextInt(5)
            val level = (state.hero.level + (stars - 2) * 3 + r.nextInt(-1, 2)).coerceAtLeast(2)
            out += Dungeon(
                id, theme.names.random(r), theme, stars, level,
                (x + 0.2 + r.nextDouble() * 0.6) * CELL_DEG, (y + 0.2 + r.nextDouble() * 0.6) * CELL_DEG, s,
            )
        }
        return out
    }

    // ---- what's inside -------------------------------------------------------

    /** Monsters you may meet inside (for the preview): the theme's first forms around its level. */
    fun residents(d: Dungeon): List<Species> {
        val r = Random(d.seed xor 0x5EED)
        return (1..40).map { Bestiary.randomWild(r, d.level, setOf(d.theme.element)) }
            .filter { d.theme.element in it.types }.distinctBy { it.family }.take(4)
            .ifEmpty { listOf(Bestiary.randomWild(r, d.level, setOf(d.theme.element))) }
    }

    /** Door odds for a difficulty: harder dungeons hold more fights and traps, fewer gifts. */
    private fun roomWeights(stars: Int): Map<RoomKind, Int> = mapOf(
        RoomKind.MONSTERS to 30,
        RoomKind.ELITE to 2 * stars,
        RoomKind.COMBAT to 8 + 5 * stars,
        RoomKind.TRAP to 8 + 4 * stars,
        RoomKind.TREASURY to 18,
        RoomKind.FOUNTAIN to 13 - 2 * stars,
        RoomKind.EMPTY to 16 - 2 * stars,
    )

    private fun rng(run: DungeonRun, salt: Int) = Random(run.dungeon.seed * 31 + run.step * 1_009L + salt)

    /** The two doors at this step (left, right): drawn at random for the difficulty, never both the same. */
    fun doors(run: DungeonRun): Pair<RoomKind, RoomKind> {
        if (run.atBoss) return RoomKind.BOSS to RoomKind.BOSS
        // The tutorial shows every kind of room once, in order.
        if (run.dungeon.tutorial) TUTORIAL_ROOMS[run.step].let { return it to it }
        val r = rng(run, 1)
        val w = roomWeights(run.dungeon.stars).filterValues { it > 0 }
        fun draw(except: RoomKind?): RoomKind {
            val pool = w.filterKeys { it != except }
            var roll = r.nextInt(pool.values.sum())
            for ((k, v) in pool) { roll -= v; if (roll < 0) return k }
            return pool.keys.last()
        }
        val left = draw(null)
        return left to draw(left)
    }

    /** Whether a door's sign is hidden ("?"): more often in harder dungeons. */
    fun hidden(run: DungeonRun, left: Boolean): Boolean =
        !run.atBoss && !run.dungeon.tutorial && rng(run, if (left) 2 else 3).nextDouble() < 0.08 * run.dungeon.stars

    /** Who fights in a room (the champion's monsters for the boss). */
    fun monstersFor(run: DungeonRun, room: RoomKind): List<Monster> {
        val d = run.dungeon
        val r = rng(run, 4 + room.ordinal)
        if (d.tutorial) {
            // Gentle first fights: one small monster each.
            val id = when (room) { RoomKind.COMBAT -> "gloomwisp"; RoomKind.BOSS -> "pebblit"; else -> "zapmouse" }
            val m = Monster.create(Bestiary.get(id), 2, r, evolve = false).withWildMoves(r)
            return listOf(if (room == RoomKind.COMBAT) m.copy(cursed = true) else m)
        }
        fun lvl() = (d.level + r.nextInt(-1, 2)).coerceAtLeast(1)
        return when (room) {
            RoomKind.MONSTERS -> {
                // Easy dungeons send at most three at once.
                val most = if (d.stars <= 2) 3 else 4
                val n = 1 + r.nextInt(most)
                val species = List(n) { Bestiary.randomWild(r, d.level, setOf(d.theme.element)) }
                species.map { Monster.create(it, lvl(), r, shiny = rollShiny(r), evolve = false).withWildMoves(r) }
            }
            RoomKind.ELITE -> {
                val sp = Bestiary.lineOf(Bestiary.randomWild(r, d.level, setOf(d.theme.element))).first()
                val m = Monster.create(sp, d.level + 1, r, evolve = false).withWildMoves(r)
                listOf(GameEngine.withPerfectStats(m, Alphas.PERFECT_STATS, r).copy(alpha = true))
            }
            RoomKind.COMBAT -> {
                // Cursed guardians: evolved monsters of the dark and of the dungeon's type.
                val n = (1 + r.nextInt(1 + (d.stars + 1) / 2)).coerceAtMost(3)
                List(n) {
                    val favored = setOf(Element.CORRUPT, d.theme.element)
                    val lv = lvl()
                    Monster.create(Bestiary.evolvedFor(Bestiary.randomWild(r, lv, favored), lv), lv, r).withWildMoves(r).copy(cursed = true)
                }
            }
            RoomKind.BOSS -> List(2) {
                val lv = d.level + 2
                Monster.create(Bestiary.evolvedFor(Bestiary.randomWild(r, lv, setOf(d.theme.element)), lv), lv, r).withWildMoves(r)
            }
            else -> emptyList()
        }
    }

    private val championNames = listOf("Varric", "Seraphine", "Doran", "Ysolde", "Kael", "Mirela", "Thorne", "Isolde")

    /** The champion at the end: a hero of the dungeon's boss class. */
    fun champion(d: Dungeon): Thug {
        if (d.tutorial) return Thug("Brannoc", d.name, 2, d.bossClass, fullTitle = "Brannoc the Gatekeeper")
        val name = championNames[Math.floorMod(d.seed shr 3, championNames.size.toLong()).toInt()]
        val title = when (d.bossClass) {
            HeroClass.WARRIOR -> "$name the Warlord"
            HeroClass.MAGE -> "$name the Archmage"
            else -> "$name the Shadowblade"
        }
        return Thug(name, d.name, d.level + 3, d.bossClass, fullTitle = title)
    }

    // ---- entering, doors and rooms --------------------------------------------

    fun enter(state: GameState, d: Dungeon): Update {
        if (state.dungeonRun != null) return Update(state, "You're already in a dungeon.")
        if (d.tutorial) return Update(startTutorial(state), "")
        if (state.torches < d.torchCost) return Update(state, "You need ${d.torchCost} torch${if (d.torchCost == 1) "" else "es"} to enter (you have ${state.torches}). Walk to light more.")
        if (state.monsters.none { it.uid in state.party && !it.fainted }) return Update(state, "Bring at least one monster that can fight.")
        val run = DungeonRun(d, last = "You light a torch and step into the ${d.name}.")
        return Update(state.copy(torches = state.torches - d.torchCost, dungeonRun = run), run.last)
    }

    /** A trap's odds: worse in harder dungeons, better for a quick hero (Rogues most of all). */
    fun trapChance(state: GameState, d: Dungeon): Double {
        if (d.tutorial) return 1.0
        val base = 0.85 - 0.08 * (d.stars - 1)
        val rogue = if (state.hero.heroClass == HeroClass.ROGUE) 0.12 else 0.0
        val quick = (state.hero.attr(HeroAttribute.SPEED) - STARTING_ATTRIBUTE) * 0.01
        return (base + rogue + quick).coerceIn(0.2, 0.95)
    }

    private val trapFails = listOf(
        "You stepped on a pressure plate and arrows flew at you!",
        "The floor gave way and you tumbled into a pit of spikes!",
        "A swinging blade sliced out of the wall!",
        "Poison gas hissed from the cracks in the stones!",
        "A boulder rolled down the corridor and clipped your team!",
        "Flames burst from the vents in the floor!",
    )
    private val trapPasses = listOf(
        "You spotted the tripwire and stepped over it.",
        "You timed the swinging blades and slipped through.",
        "You jammed the pressure plate with a stone. Safe!",
        "You held your breath and dashed through the gas.",
    )

    /** Opens the door on the [left] or right. Fights hand over to a battle; the rest happen right here. */
    fun open(state: GameState, left: Boolean, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "You're not in a dungeon.")
        if (run.fighting != null || run.cleared) return Update(state, "")
        val (l, r) = doors(run)
        val room = if (left) l else r
        return when {
            room.isFight -> Update(state.copy(dungeonRun = run.copy(fighting = room)), "")
            room == RoomKind.TRAP -> Update(state, "") // the player decides first (see [springTrap])
            room == RoomKind.STARTER -> Update(state, "") // chosen on its own screen (see [pickStarter])
            else -> resolve(state, run, room, rng)
        }
    }

    /** Walks into a trap room: [trapChance] to get through unhurt, otherwise the team takes damage. Either way you go on. */
    fun springTrap(state: GameState, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        val ok = rng.nextDouble() < trapChance(state, run.dungeon)
        if (ok) return advance(state, run, trapPasses.random(rng))
        // Harder dungeons hurt more: a share of everyone's max HP.
        val pct = 0.12 + 0.04 * run.dungeon.stars
        val heroHit = (state.hero.maxHp * pct).toInt()
        val s = state.copy(
            heroDamage = (state.heroDamage + heroHit).coerceAtMost(state.hero.maxHp - 1),
            monsters = state.monsters.map { m ->
                if (m.uid !in state.party || m.fainted) m else m.copy(damage = (m.damage + (m.stats.hp * pct).toInt()).coerceAtMost(m.stats.hp - 1))
            },
        )
        return advance(s, run, "${trapFails.random(rng)} Everyone loses ${(pct * 100).toInt()}% of their health.")
    }

    private fun resolve(state: GameState, run: DungeonRun, room: RoomKind, rng: Random): Update = when (room) {
        RoomKind.FOUNTAIN -> advance(
            state.copy(
                heroDamage = 0,
                heroEnergyUsed = 0,
                monsters = state.monsters.map { if (it.uid in state.team) it.copy(damage = 0, fainted = false) else it },
            ),
            run,
            "A fairy fountain! Its water restores you and your monsters to full strength.",
        )
        RoomKind.TREASURY -> {
            val (r2, text) = treasure(state, run, rng)
            advance(state, r2, text)
        }
        else -> advance(state, run, "An empty room. Dust, cobwebs and silence.")
    }

    /** A chest: gold, and often potions, crystals, spell pages, torches, now and then gear. Better in harder dungeons. */
    private fun treasure(state: GameState, run: DungeonRun, rng: Random): Pair<DungeonRun, String> {
        val d = run.dungeon
        if (d.tutorial) {
            // Everything you need for the rooms ahead.
            val r = run.copy(gold = run.gold + 100, torches = run.torches + 3, items = run.items + (Item.TAMING_ORB to 5) + (Item.POTION to 3))
            return r to "A treasury! The chest holds 100 gold, 3 torches, 5 Binding Crystals and 3 Potions. Chests in other dungeons are random, and better the harder the dungeon."
        }
        var r = run
        val found = mutableListOf<String>()
        val gold = d.level * (4 + d.stars * 2) + rng.nextInt(20)
        r = r.copy(gold = r.gold + gold)
        found += "$gold gold"
        fun item(i: Item, n: Int) { r = r.copy(items = r.items + (i to (r.items[i] ?: 0) + n)); found += "${i.label}${if (n > 1) " x$n" else ""}" }
        if (rng.nextDouble() < 0.5) item(listOf(Item.POTION, Item.HI_POTION, Item.ETHER).random(rng), 1 + rng.nextInt(2))
        if (rng.nextDouble() < 0.35) item(if (d.stars >= 3) Item.GREAT_ORB else Item.TAMING_ORB, 1 + rng.nextInt(3))
        if (rng.nextDouble() < 0.3 + 0.05 * d.stars) {
            val n = 1 + (if (rng.nextDouble() < d.stars / 6.0) 1 else 0)
            r = r.copy(torches = r.torches + n)
            found += "$n torch${if (n == 1) "" else "es"}"
        }
        val spells = GameEngine.pageCandidates(state)
        if (spells.isNotEmpty() && rng.nextDouble() < 0.35) {
            val sp = spells.random(rng)
            val n = 1 + rng.nextInt(2)
            r = r.copy(pages = r.pages + (sp.id to (r.pages[sp.id] ?: 0) + n))
            found += "$n ${sp.name} page${if (n == 1) "" else "s"}"
        }
        if (rng.nextDouble() < 0.06 * d.stars) {
            val g = Armory.roll(d.level, rng, luck = 10 * d.stars)
            r = r.copy(gear = r.gear + g)
            found += "${g.rarity.label} ${g.name}"
        }
        return r to "A treasury! The chest holds ${found.joinToString()}."
    }

    private fun advance(state: GameState, run: DungeonRun, text: String): Update {
        val next = run.copy(step = run.step + 1, fighting = null, last = text)
        return Update(state.copy(dungeonRun = next), text)
    }

    /**
     * A room's fight is over. Won (or tamed): on to the next room, or, for the
     * champion, the dungeon is cleared. Lost or fled: the dungeon ends, and
     * half of what you found is lost (fleeing keeps it all).
     */
    fun afterFight(state: GameState, outcome: BattleOutcome, nowMs: Long, rng: Random = Random.Default, zone: ZoneId = ZoneId.systemDefault()): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        val room = run.fighting ?: return Update(state, "")
        // The tutorial can't be lost: a fall just means your team picks itself up and goes on.
        if (run.dungeon.tutorial && outcome != BattleOutcome.WON && outcome != BattleOutcome.CAPTURED) {
            val healed = state.copy(heroDamage = 0, monsters = state.monsters.map { it.copy(fainted = false, damage = 0) })
            return if (room == RoomKind.BOSS) clear(healed, run, nowMs, rng, zone)
            else advance(healed, run, "Your team picks itself up and pushes on. (In real dungeons, falling ends the run and loses half your finds.)")
        }
        return when (outcome) {
            BattleOutcome.WON, BattleOutcome.CAPTURED -> if (room == RoomKind.BOSS) clear(state, run, nowMs, rng, zone)
            else advance(state, run, "The way ahead is clear.")
            BattleOutcome.FLED -> leave(state, "You fled the fight and made it back out with everything you found.")
            else -> leave(state, "Your team fell in the ${run.dungeon.name}. You dragged yourselves out, losing half of what you found.", half = true)
        }
    }

    /** The champion's reward pool, as shown before you enter (chances in percent). */
    fun bossRewards(d: Dungeon): List<BossReward> = listOf(
        BossReward("+1 ${statFor(d.bossClass).label} for good (once a day)", 100),
        BossReward("${d.level * 30} gold", 100),
        BossReward("Epic or better weapon", 10 + 6 * d.stars),
        BossReward("Rare or better armor", 15 + 5 * d.stars),
        BossReward("3 spell pages", 50),
        BossReward("3 Resonant Crystals", 40),
        BossReward("2 torches", 30),
        BossReward("Incubator", 6 + 2 * d.stars),
        BossReward("Golden Berry", 20),
        BossReward("Mood Crystal", 3 + d.stars),
    )

    private fun clear(state: GameState, run: DungeonRun, nowMs: Long, rng: Random, zone: ZoneId): Update {
        val d = run.dungeon
        val day = epochDay(nowMs, zone)
        if (d.tutorial) {
            // Your first champion: +1 to their class's stat, and the tutorial is done.
            val h = state.hero
            val s = state.copy(hero = h.copy(attributes = h.attributes + (d.bossStat to h.attr(d.bossStat) + 1)), tutorialDungeonDone = true)
            val banked = bank(s, run.copy(gold = run.gold + 150), half = false)
            return Update(
                banked.state.copy(dungeonRun = null),
                "Brannoc the Gatekeeper is defeated! +1 ${d.bossStat.label} for good. Real dungeons wait on your map: walk to light torches and explore them. ${banked.message}".trim(),
            )
        }
        var r = run.copy(gold = run.gold + d.level * 30)
        val got = mutableListOf<String>()
        var s = state
        // The champion's stat, once a day.
        val stat = d.bossStat
        if (s.championStatDay != day) {
            val h = s.hero
            s = s.copy(hero = h.copy(attributes = h.attributes + (stat to h.attr(stat) + 1)), championStatDay = day)
            got += "+1 ${stat.label} for good"
        }
        got += "${d.level * 30} gold"
        val pool = bossRewards(d)
        fun hit(i: Int) = rng.nextInt(100) < pool[i].pct
        fun item(i: Item, n: Int) { r = r.copy(items = r.items + (i to (r.items[i] ?: 0) + n)) }
        if (hit(2)) Armory.roll(d.level, rng, luck = 30 + 10 * d.stars, minimum = GearRarity.EPIC).let { r = r.copy(gear = r.gear + it); got += "${it.rarity.label} ${it.name}" }
        if (hit(3)) Armory.rollArmor(d.level, rng, luck = 20 + 10 * d.stars, minimum = GearRarity.RARE).let { r = r.copy(armor = r.armor + it); got += "${it.rarity.label} ${it.name}" }
        if (hit(4)) GameEngine.pageCandidates(s).randomOrNull(rng)?.let { r = r.copy(pages = r.pages + (it.id to (r.pages[it.id] ?: 0) + 3)); got += "3 ${it.name} pages" }
        if (hit(5)) { item(Item.GREAT_ORB, 3); got += "3 Resonant Crystals" }
        if (hit(6)) { r = r.copy(torches = r.torches + 2); got += "2 torches" }
        if (hit(7)) { item(Item.INCUBATOR, 1); got += "an Incubator" }
        if (hit(8)) { item(Item.GOLDEN_BERRY, 1); got += "a Golden Berry" }
        if (hit(9)) { item(Item.MOOD_CRYSTAL, 1); got += "a Mood Crystal" }
        s = s.copy(dungeonsCleared = s.dungeonsCleared.filterValues { it >= day - 1 } + (d.id to day))
        val banked = bank(s, r, half = false)
        return Update(
            banked.state.copy(dungeonRun = null, stats = banked.state.stats.copy(dungeonsCleared = banked.state.stats.dungeonsCleared + 1)),
            "${champion(d).title} is defeated! The ${d.name} is cleared: ${got.joinToString()}. ${banked.message}".trim(),
        )
    }

    /** Leaves the dungeon, banking what was found (half of it if your team fell). */
    fun leave(state: GameState, text: String = "You leave the dungeon with everything you found.", half: Boolean = false): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        val banked = bank(state, run, half)
        return Update(banked.state.copy(dungeonRun = null), "$text ${banked.message}".trim())
    }

    private fun bank(state: GameState, run: DungeonRun, half: Boolean): Update {
        fun cut(n: Int) = if (half) n / 2 else n
        var s = state.copy(gold = state.gold + cut(run.gold))
        s = Torches.add(s, cut(run.torches))
        val loot = GameEngine.Loot(
            items = run.items.mapValues { cut(it.value) }.filterValues { it > 0 },
            weapons = if (half) run.gear.take(run.gear.size / 2) else run.gear,
            armor = if (half) run.armor.take(run.armor.size / 2) else run.armor,
            pages = run.pages.mapValues { cut(it.value) }.filterValues { it > 0 },
        )
        val granted = GameEngine.grantLoot(s, loot)
        val total = listOfNotNull(
            cut(run.gold).takeIf { it > 0 }?.let { "$it gold" },
            cut(run.torches).takeIf { it > 0 }?.let { "$it torch${if (it == 1) "" else "es"}" },
            granted.message.takeIf { it.isNotBlank() && it != "nothing" },
        )
        return Update(granted.state, if (total.isEmpty()) "" else "Banked: ${total.joinToString()}.")
    }
}
