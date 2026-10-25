package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Species(
    val id: String,
    val name: String,
    val emoji: String,
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
 * All 50 monsters:
 * - 6 four-stage lines (3 evolutions), one per type: the strongest final forms.
 * - 6 three-stage hound lines (2 evolutions): one breed per type.
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

    private data class Member(val id: String, val name: String, val emoji: String, val special: String)

    private fun scale(b: StatBlock, m: Double) = StatBlock(
        (b.hp * m).toInt(), (b.attack * m).toInt(), (b.magic * m).toInt(), (b.defense * m).toInt(), (b.speed * m).toInt(),
    )

    private fun line(
        family: String,
        element: Element,
        base: StatBlock,
        kind: SkillKind,
        members: List<Member>,
        breed: String? = null,
    ): List<Species> {
        val four = members.size == 4
        val mults = if (four) fourStageMultipliers else threeStageMultipliers
        val rarities = if (four) fourStageRarity else threeStageRarity
        val levels = if (four) fourStageLevels else threeStageLevels
        return members.mapIndexed { i, m ->
            Species(
                id = m.id,
                name = m.name,
                emoji = m.emoji,
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
                Member(ids[0], names[0], "🐕", specials[0]),
                Member(ids[1], names[1], "🐕‍🦺", specials[1]),
                Member(ids[2], names[2], "🐺", specials[2]),
            ),
            breed = breed,
        )
    }

    private fun single(
        id: String, name: String, emoji: String, element: Element, rarity: Rarity,
        base: StatBlock, special: String, kind: SkillKind, power: Int, bossOnly: Boolean, minWildLevel: Int = 1,
    ) = Species(
        id, name, emoji, element, rarity, base, Skill("${id}_special", special, element, kind, power),
        bossOnly = bossOnly, family = name, minWildLevel = minWildLevel,
    )

    val all: List<Species> = buildList {
        // ---- Four-stage lines (3 evolutions) ----
        addAll(line("Emberkit line", Element.FIRE, StatBlock(40, 15, 10, 9, 15), SkillKind.PHYSICAL, listOf(
            Member("emberkit", "Emberkit", "🐱", "Ember Swipe"),
            Member("blazecat", "Blazecat", "🐈", "Blaze Pounce"),
            Member("flarepanther", "Flarepanther", "🐆", "Flare Rush"),
            Member("solar_tiger", "Solar Tiger", "🐅", "Solar Fang"),
        )))
        addAll(line("Zapmouse line", Element.ELECTRIC, StatBlock(36, 9, 16, 9, 17), SkillKind.MAGICAL, listOf(
            Member("zapmouse", "Zapmouse", "🐭", "Static Nip"),
            Member("voltrat", "Voltrat", "🐀", "Spark Bolt"),
            Member("thunderhog", "Thunderhog", "🦔", "Thunder Quills"),
            Member("storm_kirin", "Storm Kirin", "🦄", "Heaven's Bolt"),
        )))
        addAll(line("Fluffchick line", Element.FLIGHT, StatBlock(36, 13, 11, 9, 18), SkillKind.PHYSICAL, listOf(
            Member("fluffchick", "Fluffchick", "🐣", "Peck Flurry"),
            Member("skylark", "Skylark", "🐦", "Wing Slice"),
            Member("galehawk", "Galehawk", "🦅", "Gale Dive"),
            Member("tempest_roc", "Tempest Roc", "🦚", "Tempest Wings"),
        )))
        addAll(line("Bubblefin line", Element.WATER, StatBlock(44, 12, 13, 12, 10), SkillKind.MAGICAL, listOf(
            Member("bubblefin", "Bubblefin", "🐟", "Bubble Jet"),
            Member("tidefin", "Tidefin", "🐠", "Tide Spout"),
            Member("reefjaw", "Reefjaw", "🦈", "Riptide"),
            Member("leviathan", "Leviathan", "🐋", "Abyssal Wave"),
        )))
        addAll(line("Toxbug line", Element.POISON, StatBlock(42, 12, 13, 13, 10), SkillKind.MAGICAL, listOf(
            Member("toxbug", "Toxbug", "🐛", "Toxic Spit"),
            Member("mirepede", "Mirepede", "🐜", "Acid Bite"),
            Member("stingscor", "Stingscor", "🦂", "Venom Sting"),
            Member("basilisk", "Basilisk", "🐍", "Petrifying Gaze"),
        )))
        addAll(line("Gloomwisp line", Element.CORRUPT, StatBlock(38, 11, 16, 10, 15), SkillKind.MAGICAL, listOf(
            Member("gloomwisp", "Gloomwisp", "👻", "Hex"),
            Member("gloombat", "Gloombat", "🦇", "Night Shriek"),
            Member("nightstalker", "Nightstalker", "🕷️", "Shadow Web"),
            Member("abyss_lord", "Abyss Lord", "👹", "Oblivion"),
        )))

        // ---- Hound breeds: three-stage lines (2 evolutions) ----
        addAll(hounds("Ember", Element.FIRE, listOf("Ember Pup", "Cinder Hound", "Inferno Fenrir"), listOf("Ember Bite", "Cinder Fang", "Inferno Howl")))
        addAll(hounds("Spark", Element.ELECTRIC, listOf("Spark Pup", "Volt Hound", "Thunder Fenrir"), listOf("Spark Bite", "Volt Fang", "Thunder Howl")))
        addAll(hounds("Gale", Element.FLIGHT, listOf("Gale Pup", "Sky Hound", "Tempest Fenrir"), listOf("Gust Bite", "Sky Fang", "Tempest Howl")))
        addAll(hounds("Tide", Element.WATER, listOf("Tide Pup", "Surf Hound", "Tsunami Fenrir"), listOf("Splash Bite", "Surf Fang", "Tsunami Howl")))
        addAll(hounds("Venom", Element.POISON, listOf("Venom Pup", "Toxic Hound", "Plague Fenrir"), listOf("Venom Bite", "Toxic Fang", "Plague Howl")))
        addAll(hounds("Shadow", Element.CORRUPT, listOf("Shadow Pup", "Dusk Hound", "Void Fenrir"), listOf("Shadow Bite", "Dusk Fang", "Void Howl")))

        // ---- Mythics: rare single-stage monsters ----
        add(single("mimic", "Mimic", "🧰", Element.CORRUPT, Rarity.RARE, StatBlock(66, 24, 24, 24, 10), "Treasure Trap", SkillKind.PHYSICAL, 44, false, minWildLevel = 8))
        add(single("prism_fairy", "Prism Fairy", "🧚", Element.FLIGHT, Rarity.RARE, StatBlock(54, 10, 30, 15, 27), "Prism Beam", SkillKind.MAGICAL, 44, false, minWildLevel = 8))

        // ---- Legendary bosses ----
        add(single("magma_behemoth", "Magma Behemoth", "🌋", Element.FIRE, Rarity.LEGENDARY, StatBlock(84, 27, 18, 20, 9), "Eruption", SkillKind.PHYSICAL, 60, true))
        add(single("thunder_titan", "Thunder Titan", "🌩️", Element.ELECTRIC, Rarity.LEGENDARY, StatBlock(78, 20, 27, 18, 16), "Judgement Bolt", SkillKind.MAGICAL, 60, true))
        add(single("sky_serpent", "Sky Serpent", "🐲", Element.FLIGHT, Rarity.LEGENDARY, StatBlock(72, 22, 24, 16, 22), "Cyclone Coil", SkillKind.MAGICAL, 58, true))
        add(single("abyss_kraken", "Abyss Kraken", "🐙", Element.WATER, Rarity.LEGENDARY, StatBlock(80, 24, 22, 18, 12), "Maelstrom", SkillKind.MAGICAL, 58, true))
        add(single("miasma_hydra", "Miasma Hydra", "🐉", Element.POISON, Rarity.LEGENDARY, StatBlock(86, 23, 22, 21, 10), "Nine Venoms", SkillKind.MAGICAL, 58, true))
        add(single("dread_lich", "Dread Lich", "💀", Element.CORRUPT, Rarity.LEGENDARY, StatBlock(70, 14, 28, 18, 16), "Death Knell", SkillKind.MAGICAL, 62, true))
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

    /** Boss shrine guardians: the legendaries plus the final forms of the four-stage lines. */
    val bosses: List<Species> = all.filter { it.bossOnly || (it.stages == 4 && it.stage == 4) }

    /** Random wild species that can appear at [level], weighted by rarity. */
    fun randomWild(rng: Random, level: Int = MAX_MONSTER_LEVEL): Species {
        val pool = wild.filter { it.minWildLevel <= level }
        val total = pool.sumOf { it.rarity.spawnWeight }
        var roll = rng.nextInt(total)
        for (s in pool) {
            roll -= s.rarity.spawnWeight
            if (roll < 0) return s
        }
        return pool.last()
    }

    /** The full evolution line containing [species]. */
    fun lineOf(species: Species): List<Species> = all.filter { it.family == species.family }.sortedBy { it.stage }
}
