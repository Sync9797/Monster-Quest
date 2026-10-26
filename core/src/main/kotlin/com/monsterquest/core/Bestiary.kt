package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Species(
    val id: String,
    val name: String,
    val element: Element,
    val rarity: Rarity,
    val base: StatBlock,
    val special: Skill,
    /** Boss species only appear at boss shrines. */
    val bossOnly: Boolean = false,
    /** Evolution line this species belongs to (e.g. "Emberkit line"). */
    val family: String,
    /** Breed name for species that share an archetype across types, e.g. "Ember" hound. */
    val breed: String? = null,
    /** 1-based position in the evolution line, and the line's length. */
    val stage: Int = 1,
    val stages: Int = 1,
    val evolvesTo: String? = null,
    val evolveLevel: Int? = null,
    /** Lowest level this species can be found at in the wild. */
    val minWildLevel: Int = 1,
)

/**
 * All 74 monsters:
 * - 3 starter lines (#1, #4, #7): pseudo-legendary three-stage lines, the
 *   strongest monsters short of the legendaries. Very rare in the wild.
 * - 6 four-stage lines (3 evolutions), one per type.
 * - 6 three-stage hound lines (2 evolutions): one breed per type.
 * - 5 more three-stage lines: slime, rock golem, thunderbird, shell turtle and imp.
 * - 2 rare one-stage mythics.
 * - 6 legendary bosses, one per type, found only at boss shrines.
 */
object Bestiary {
    private val fourStageMultipliers = listOf(1.0, 1.3, 1.65, 2.0)
    private val threeStageMultipliers = listOf(1.0, 1.35, 1.7)
    private val fourStageRarity = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC)
    private val threeStageRarity = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE)
    private val fourStageLevels = listOf(14, 28, 42)
    private val threeStageLevels = listOf(16, 32)

    private data class Member(val id: String, val name: String, val special: String)

    private fun scale(b: StatBlock, m: Double) = StatBlock(
        (b.hp * m).toInt(), (b.attack * m).toInt(), (b.magic * m).toInt(), (b.defense * m).toInt(), (b.speed * m).toInt(),
    )

    // Starters: three stages, strong growth, always rare.
    private val starterMultipliers = listOf(1.0, 1.45, 1.95)
    private val starterRarity = listOf(Rarity.EPIC, Rarity.EPIC, Rarity.EPIC)
    private val starterLevels = listOf(16, 36)

    private fun line(
        family: String,
        element: Element,
        base: StatBlock,
        kind: SkillKind,
        members: List<Member>,
        breed: String? = null,
        starter: Boolean = false,
    ): List<Species> {
        val four = members.size == 4
        val mults = if (starter) starterMultipliers else if (four) fourStageMultipliers else threeStageMultipliers
        val rarities = if (starter) starterRarity else if (four) fourStageRarity else threeStageRarity
        val levels = if (starter) starterLevels else if (four) fourStageLevels else threeStageLevels
        return members.mapIndexed { i, m ->
            Species(
                id = m.id,
                name = m.name,
                element = element,
                rarity = rarities[i],
                base = scale(base, mults[i]),
                special = Skill("${m.id}_special", m.special, element, kind, 30 + i * 9),
                family = family,
                breed = breed,
                stage = i + 1,
                stages = members.size,
                evolvesTo = members.getOrNull(i + 1)?.id,
                evolveLevel = levels.getOrNull(i),
                minWildLevel = if (i == 0) 1 else levels[i - 1],
            )
        }
    }

    private fun hounds(breed: String, element: Element, names: List<String>, specials: List<String>): List<Species> {
        val prefix = breed.lowercase()
        val ids = listOf("${prefix}_pup", "${names[1].lowercase().replace(' ', '_')}", "${names[2].lowercase().replace(' ', '_')}")
        return line(
            family = "$breed Hound line",
            element = element,
            base = StatBlock(40, 15, 8, 11, 14),
            kind = SkillKind.PHYSICAL,
            members = listOf(
                Member(ids[0], names[0], specials[0]),
                Member(ids[1], names[1], specials[1]),
                Member(ids[2], names[2], specials[2]),
            ),
            breed = breed,
        )
    }

    /**
     * Legendaries are the strongest monsters you can own: their HP, Attack,
     * Magic and Defense are boosted by this much. Boss fights scale them back
     * down (see [Battle]), so Gold shrines stay as hard as they were.
     */
    const val LEGENDARY_POWER = 1.6

    private fun single(
        id: String, name: String, element: Element, rarity: Rarity,
        base: StatBlock, special: String, kind: SkillKind, power: Int, bossOnly: Boolean, minWildLevel: Int = 1,
    ): Species {
        val b = if (rarity == Rarity.LEGENDARY) {
            StatBlock(
                (base.hp * LEGENDARY_POWER).toInt(), (base.attack * LEGENDARY_POWER).toInt(), (base.magic * LEGENDARY_POWER).toInt(),
                (base.defense * LEGENDARY_POWER).toInt(), base.speed,
            )
        } else {
            base
        }
        return Species(
            id, name, element, rarity, b, Skill("${id}_special", special, element, kind, power),
            bossOnly = bossOnly, family = name, minWildLevel = minWildLevel,
        )
    }

    val all: List<Species> = buildList {
        // ---- Starters (#1, #4, #7): pseudo-legendary three-stage lines ----
        addAll(line("Voltfawn line", Element.ELECTRIC, StatBlock(44, 15, 17, 13, 18), SkillKind.MAGICAL, listOf(
            Member("voltfawn", "Voltfawn", "Volt Kick"),
            Member("stormstag", "Stormstag", "Storm Charge"),
            Member("thunderhorn", "Thunderhorn", "Thunder Crown"),
        ), starter = true))
        addAll(line("Cindrake line", Element.FIRE, StatBlock(46, 18, 14, 14, 15), SkillKind.PHYSICAL, listOf(
            Member("cindrake", "Cindrake", "Cinder Breath"),
            Member("blazewyrm", "Blazewyrm", "Blaze Wing"),
            Member("infernodrake", "Infernodrake", "Infernal Roar"),
        ), starter = true))
        addAll(line("Drizzlet line", Element.WATER, StatBlock(50, 14, 16, 16, 13), SkillKind.MAGICAL, listOf(
            Member("drizzlet", "Drizzlet", "Drizzle Jet"),
            Member("tidewyrm", "Tidewyrm", "Tide Coil"),
            Member("maelstrom_serpent", "Maelstrom Serpent", "Maelstrom Fang"),
        ), starter = true))

        // ---- Four-stage lines (3 evolutions) ----
        addAll(line("Emberkit line", Element.FIRE, StatBlock(40, 15, 10, 9, 15), SkillKind.PHYSICAL, listOf(
            Member("emberkit", "Emberkit", "Ember Swipe"),
            Member("blazecat", "Blazecat", "Blaze Pounce"),
            Member("flarepanther", "Flarepanther", "Flare Rush"),
            Member("solar_tiger", "Solar Tiger", "Solar Fang"),
        )))
        addAll(line("Zapmouse line", Element.ELECTRIC, StatBlock(36, 9, 16, 9, 17), SkillKind.MAGICAL, listOf(
            Member("zapmouse", "Zapmouse", "Static Nip"),
            Member("voltrat", "Voltrat", "Spark Bolt"),
            Member("thunderhog", "Thunderhog", "Thunder Quills"),
            Member("storm_kirin", "Storm Kirin", "Heaven's Bolt"),
        )))
        addAll(line("Fluffchick line", Element.FLIGHT, StatBlock(36, 13, 11, 9, 18), SkillKind.PHYSICAL, listOf(
            Member("fluffchick", "Fluffchick", "Peck Flurry"),
            Member("skylark", "Skylark", "Wing Slice"),
            Member("galehawk", "Galehawk", "Gale Dive"),
            Member("tempest_roc", "Tempest Roc", "Tempest Wings"),
        )))
        addAll(line("Bubblefin line", Element.WATER, StatBlock(44, 12, 13, 12, 10), SkillKind.MAGICAL, listOf(
            Member("bubblefin", "Bubblefin", "Bubble Jet"),
            Member("tidefin", "Tidefin", "Tide Spout"),
            Member("reefjaw", "Reefjaw", "Riptide"),
            Member("leviathan", "Leviathan", "Abyssal Wave"),
        )))
        addAll(line("Toxbug line", Element.GAS, StatBlock(42, 12, 13, 13, 10), SkillKind.MAGICAL, listOf(
            Member("toxbug", "Toxbug", "Toxic Spit"),
            Member("mirepede", "Mirepede", "Acid Bite"),
            Member("stingscor", "Stingscor", "Venom Sting"),
            Member("basilisk", "Basilisk", "Petrifying Gaze"),
        )))
        addAll(line("Gloomwisp line", Element.CORRUPT, StatBlock(38, 11, 16, 10, 15), SkillKind.MAGICAL, listOf(
            Member("gloomwisp", "Gloomwisp", "Hex"),
            Member("gloombat", "Gloombat", "Night Shriek"),
            Member("nightstalker", "Nightstalker", "Shadow Web"),
            Member("abyss_lord", "Abyss Lord", "Oblivion"),
        )))

        // ---- Hound breeds: three-stage lines (2 evolutions) ----
        addAll(hounds("Ember", Element.FIRE, listOf("Ember Pup", "Cinder Hound", "Inferno Fenrir"), listOf("Ember Bite", "Cinder Fang", "Inferno Howl")))
        addAll(hounds("Spark", Element.ELECTRIC, listOf("Spark Pup", "Volt Hound", "Thunder Fenrir"), listOf("Spark Bite", "Volt Fang", "Thunder Howl")))
        addAll(hounds("Gale", Element.FLIGHT, listOf("Gale Pup", "Sky Hound", "Tempest Fenrir"), listOf("Gust Bite", "Sky Fang", "Tempest Howl")))
        addAll(hounds("Tide", Element.WATER, listOf("Tide Pup", "Surf Hound", "Tsunami Fenrir"), listOf("Splash Bite", "Surf Fang", "Tsunami Howl")))
        addAll(hounds("Venom", Element.GAS, listOf("Venom Pup", "Toxic Hound", "Plague Fenrir"), listOf("Venom Bite", "Toxic Fang", "Plague Howl")))
        addAll(hounds("Shadow", Element.CORRUPT, listOf("Shadow Pup", "Dusk Hound", "Void Fenrir"), listOf("Shadow Bite", "Dusk Fang", "Void Howl")))

        // ---- More three-stage lines ----
        addAll(line("Goopling line", Element.GAS, StatBlock(46, 10, 13, 13, 8), SkillKind.MAGICAL, listOf(
            Member("goopling", "Goopling", "Goo Toss"),
            Member("sludgeon", "Sludgeon", "Sludge Wave"),
            Member("slime_sovereign", "Slime Sovereign", "Royal Ooze"),
        )))
        addAll(line("Pebblit line", Element.FIRE, StatBlock(50, 16, 6, 17, 5), SkillKind.PHYSICAL, listOf(
            Member("pebblit", "Pebblit", "Rock Toss"),
            Member("boulderon", "Boulderon", "Boulder Slam"),
            Member("magma_colossus", "Magma Colossus", "Molten Quake"),
        )))
        addAll(line("Zipwing line", Element.ELECTRIC, StatBlock(34, 13, 14, 8, 19), SkillKind.PHYSICAL, listOf(
            Member("zipwing", "Zipwing", "Zap Peck"),
            Member("stormtalon", "Stormtalon", "Static Dive"),
            Member("thunderbird", "Thunderbird", "Thunderclap"),
        )))
        addAll(line("Shellsnap line", Element.WATER, StatBlock(48, 13, 10, 17, 6), SkillKind.PHYSICAL, listOf(
            Member("shellsnap", "Shellsnap", "Shell Ram"),
            Member("coralback", "Coralback", "Coral Crush"),
            Member("bastion_tortoise", "Bastion Tortoise", "Bastion Surge"),
        )))
        addAll(line("Impling line", Element.CORRUPT, StatBlock(36, 12, 16, 9, 16), SkillKind.MAGICAL, listOf(
            Member("impling", "Impling", "Imp Claw"),
            Member("hexfiend", "Hexfiend", "Hex Bolt"),
            Member("archfiend", "Archfiend", "Infernal Pact"),
        )))

        // ---- Mythics: rare single-stage monsters ----
        add(single("mimic", "Mimic", Element.CORRUPT, Rarity.RARE, StatBlock(66, 24, 24, 24, 10), "Treasure Trap", SkillKind.PHYSICAL, 44, false, minWildLevel = 8))
        add(single("prism_fairy", "Prism Fairy", Element.FLIGHT, Rarity.RARE, StatBlock(54, 10, 30, 15, 27), "Prism Beam", SkillKind.MAGICAL, 44, false, minWildLevel = 8))

        // ---- Legendary bosses ----
        add(single("magma_behemoth", "Magma Behemoth", Element.FIRE, Rarity.LEGENDARY, StatBlock(84, 27, 18, 20, 9), "Eruption", SkillKind.PHYSICAL, 60, true))
        add(single("thunder_titan", "Thunder Titan", Element.ELECTRIC, Rarity.LEGENDARY, StatBlock(78, 20, 27, 18, 16), "Judgement Bolt", SkillKind.MAGICAL, 60, true))
        add(single("sky_serpent", "Sky Serpent", Element.FLIGHT, Rarity.LEGENDARY, StatBlock(72, 22, 24, 16, 22), "Cyclone Coil", SkillKind.MAGICAL, 58, true))
        add(single("abyss_kraken", "Abyss Kraken", Element.WATER, Rarity.LEGENDARY, StatBlock(80, 24, 22, 18, 12), "Maelstrom", SkillKind.MAGICAL, 58, true))
        add(single("miasma_hydra", "Miasma Hydra", Element.GAS, Rarity.LEGENDARY, StatBlock(86, 23, 22, 21, 10), "Nine Venoms", SkillKind.MAGICAL, 58, true))
        add(single("dread_lich", "Dread Lich", Element.CORRUPT, Rarity.LEGENDARY, StatBlock(70, 14, 28, 18, 16), "Death Knell", SkillKind.MAGICAL, 62, true))
    }

    private val byId = all.associateBy { it.id }

    /** Species from older saves, mapped onto the current roster. */
    private val legacyIds = mapOf(
        "emberpup" to "ember_pup", "sproutling" to "venom_pup", "pebblet" to "zapmouse",
        "breezling" to "fluffchick", "greywolf" to "shadow_pup", "squeaker" to "zapmouse",
        "glowmoth" to "prism_fairy", "shadecat" to "gloomwisp", "thornboar" to "toxbug",
        "tidecrab" to "tidefin", "cinderfox" to "emberkit", "dustmole" to "voltrat",
        "stormhawk" to "galehawk", "frostbear" to "reefjaw", "ironshell" to "stingscor",
        "wraith" to "gloombat", "sunlion" to "flarepanther", "wyrmling" to "inferno_fenrir",
        "elder_treant" to "miasma_hydra", "titan_golem" to "thunder_titan", "seraph" to "prism_fairy",
    )

    fun get(id: String): Species = byId[id] ?: byId.getValue(migrateId(id))

    fun migrateId(id: String): String = when {
        id in byId -> id
        else -> legacyIds[id] ?: "ember_pup"
    }

    val wild: List<Species> = all.filter { !it.bossOnly }

    /**
     * Silver shrine guardians: strong evolved monsters but never legendary:
     * third stages of the four-stage lines and the final forms of the three-stage lines.
     */
    val silverGuardians: List<Species> = all.filter { !it.bossOnly && it.stage == 3 }

    /** Gold shrine guardians: the legendaries plus the final forms of the four-stage lines. */
    val bosses: List<Species> = all.filter { it.bossOnly || (it.stages == 4 && it.stage == 4) }

    /** Random wild species that can appear at [level], weighted by rarity. */
    /**
     * A random wild species for [level]. [favored] types are three times as
     * likely (time of day); [rareBoost] multiplies the odds of uncommon and
     * rarer monsters (moving around, lures).
     */
    fun randomWild(rng: Random, level: Int = MAX_MONSTER_LEVEL, favored: Set<Element> = emptySet(), rareBoost: Double = 1.0): Species {
        val pool = wild.filter { it.minWildLevel <= level }
        fun weight(s: Species): Int {
            var w = s.rarity.spawnWeight.toDouble()
            if (s.element in favored) w *= 3
            if (s.rarity.ordinal > 0) w *= rareBoost
            return w.toInt().coerceAtLeast(1)
        }
        var roll = rng.nextInt(pool.sumOf { weight(it) })
        for (s in pool) {
            roll -= weight(s)
            if (roll < 0) return s
        }
        return pool.last()
    }

    /** The full evolution line containing [species]. */
    fun lineOf(species: Species): List<Species> = all.filter { it.family == species.family }.sortedBy { it.stage }
}
