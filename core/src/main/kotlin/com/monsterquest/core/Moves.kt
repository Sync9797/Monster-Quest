package com.monsterquest.core

/** Status conditions. Each one also makes the monster easier to tame. */
enum class Status(val label: String, val captureBonus: Double) {
    SLEEP("Asleep", 1.5),
    SHOCK("Shocked", 1.3),
    POISON("Poisoned", 1.2),
    BURN("Burned", 1.2),
}

enum class Weather(val label: String) {
    CLEAR("Clear"),
    /** Water and Electric moves +25%, Fire −15%. */
    RAIN("Rain"),
    /** Fire and Corrupt moves +25%, Water −15%. */
    DROUGHT("Drought");

    fun multiplier(type: Element): Double = when (this) {
        CLEAR -> 1.0
        RAIN -> when (type) {
            Element.WATER, Element.ELECTRIC -> 1.25
            Element.FIRE -> 0.85
            else -> 1.0
        }
        DROUGHT -> when (type) {
            Element.FIRE, Element.CORRUPT -> 1.25
            Element.WATER -> 0.85
            else -> 1.0
        }
    }
}

enum class BattleStat(val label: String) { ATTACK("Attack"), DEFENSE("Defense"), SPEED("Speed") }

/** What a move does besides (or instead of) damage. Chances are 0..1. */
sealed interface MoveEffect {
    /** Inflict a status for a random number of turns in [minTurns]..[maxTurns]. */
    data class Inflict(val status: Status, val chance: Double, val minTurns: Int, val maxTurns: Int) : MoveEffect
    data class LowerFoe(val stats: List<BattleStat>, val chance: Double) : MoveEffect
    data class SetWeather(val weather: Weather, val chance: Double) : MoveEffect
    /** The foe's next move has a 40% chance to miss. */
    data class FoeAccuracyDown(val chance: Double) : MoveEffect
    /** The foe's next move misses outright. */
    data class FoeMissesNext(val chance: Double) : MoveEffect
    /** The foe loses its next turn. */
    data class Flinch(val chance: Double) : MoveEffect
    /** Damage over time: [base] (scaled by the user's level) each turn for [turns] turns. */
    data class Scorch(val base: Int, val turns: Int) : MoveEffect
    /** Puts the foe to sleep; it also takes [base] (scaled) damage every turn it sleeps. */
    data class Nightmare(val chance: Double, val base: Int) : MoveEffect
    /** Blocks all damage until the user's next turn. Can't be used twice in a row. */
    data object Protect : MoveEffect
    /** The user must rest on its next turn. */
    data object Recharge : MoveEffect
    /** [chance] to take [base] (scaled) damage. */
    data class Recoil(val base: Int, val chance: Double) : MoveEffect
    /** The user's damage rises by [percent] for the rest of the battle (stacks). */
    data class PowerUp(val percent: Int) : MoveEffect
    /** The foe deals 25% less damage for the rest of the battle. */
    data object Scare : MoveEffect
    /** Slowest fighters move first for 3 rounds. */
    data object ReverseOrder : MoveEffect
    /** Turn 1: fly out of reach (only Thunder Bolt can hit). Turn 2: strike. */
    data object TwoTurnDive : MoveEffect
    /** The user heals [percent]% of its max HP. */
    data class HealSelf(val percent: Int) : MoveEffect
}

data class Move(
    val id: String,
    val name: String,
    val type: Element,
    /** Base damage, before the user's Attack and the target's Defense. 0 = status move. */
    val power: Int,
    val accuracy: Int = 100,
    val effects: List<MoveEffect> = emptyList(),
    val description: String,
) {
    val isDamaging: Boolean get() = power > 0
}

/**
 * Every move, and the level at which each type learns them. Every monster
 * learns its own type's moves plus the Normal moves. (No monster is Normal
 * type; Normal is only an attack type.)
 */
object Moves {
    private fun m(id: String, name: String, type: Element, power: Int, acc: Int = 100, desc: String, vararg fx: MoveEffect) =
        Move(id, name, type, power, acc, fx.toList(), desc)


    val all: List<Move> = listOf(
        // ---- Normal ----
        m("tackle", "Tackle", Element.NEUTRAL, 40, desc = "A full-body charge."),
        m("bite", "Bite", Element.NEUTRAL, 40, desc = "25% chance the foe loses its next turn.", fx = *arrayOf(MoveEffect.Flinch(0.25))),
        m("intimidate", "Intimidate", Element.NEUTRAL, 0, desc = "Lowers the foe's Attack.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.ATTACK), 1.0))),
        m("scare", "Scare", Element.NEUTRAL, 0, desc = "The foe deals 25% less damage for the battle.", fx = *arrayOf(MoveEffect.Scare)),
        m("headbutt", "Headbutt", Element.NEUTRAL, 100, 90, "25% chance of taking 20 recoil damage.", MoveEffect.Recoil(20, 0.25)),
        m("sleep_wave", "Sleep Wave", Element.NEUTRAL, 0, desc = "75% chance the foe falls asleep.", fx = *arrayOf(MoveEffect.Inflict(Status.SLEEP, 0.75, 2, 4))),

        // ---- Fire ----
        m("spit_fire", "Spit Fire", Element.FIRE, 40, desc = "A gob of flame."),
        m("fire_fang", "Fire Fang", Element.FIRE, 50, desc = "10% chance to burn.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 0.10, 4, 4))),
        m("fire_wave", "Fire Wave", Element.FIRE, 60, desc = "Scorches for 5 damage a turn for 5 turns.", fx = *arrayOf(MoveEffect.Scorch(5, 5))),
        m("solar_flare", "Solar Flare", Element.FIRE, 0, desc = "Blinding light: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("drought", "Drought", Element.FIRE, 0, desc = "Dries up rain. Fire and Corrupt +25%, Water −15%.", fx = *arrayOf(MoveEffect.SetWeather(Weather.DROUGHT, 1.0))),
        m("fire_storm", "Fire Storm", Element.FIRE, 100, 85, "A raging firestorm."),
        m("blast_burn", "Blast Burn", Element.FIRE, 120, 90, "Huge damage, but the user must rest next turn.", MoveEffect.Recharge),

        // ---- Electric ----
        m("spark", "Spark", Element.ELECTRIC, 40, desc = "A crackling jolt."),
        m("shock", "Shock", Element.ELECTRIC, 20, desc = "Shocks the foe: 25% chance each turn it can't act.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4))),
        m("thunder_shock", "Thunder Shock", Element.ELECTRIC, 40, desc = "15% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.15, 4, 4))),
        m("thunder_wave", "Thunder Wave", Element.ELECTRIC, 60, desc = "25% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.25, 4, 4))),
        m("thunder_bolt", "Thunder Bolt", Element.ELECTRIC, 80, desc = "25% chance to bring rain. Hits fliers mid-dive.", fx = *arrayOf(MoveEffect.SetWeather(Weather.RAIN, 0.25))),
        m("volt_crash", "Volt Crash", Element.ELECTRIC, 110, 90, "A reckless charge. 25% chance of 20 recoil.", MoveEffect.Recoil(20, 0.25)),

        // ---- Water ----
        m("splash", "Splash", Element.WATER, 20, desc = "Spray in the eyes: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("water_shot", "Water Shot", Element.WATER, 60, desc = "A pressurized jet."),
        m("wave_guard", "Wave Guard", Element.WATER, 0, desc = "Blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("rain", "Rain", Element.WATER, 0, desc = "Summons rain. Water and Electric +25%, Fire −15%.", fx = *arrayOf(MoveEffect.SetWeather(Weather.RAIN, 1.0))),
        m("hydro_blast", "Hydro Blast", Element.WATER, 100, 85, "A crushing torrent."),
        m("hydro_surprise", "Hydro Surprise", Element.WATER, 100, 85, "25% chance to bring rain.", MoveEffect.SetWeather(Weather.RAIN, 0.25)),

        // ---- Flight ----
        m("peck", "Peck", Element.FLIGHT, 40, desc = "A sharp jab."),
        m("gust", "Gust", Element.FLIGHT, 60, desc = "15% chance the foe misses its next attack.", fx = *arrayOf(MoveEffect.FoeMissesNext(0.15))),
        m("steel_wing", "Steel Wing", Element.FLIGHT, 40, desc = "Your damage rises 10% for the rest of the battle.", fx = *arrayOf(MoveEffect.PowerUp(10))),
        m("lullaby", "Lullaby", Element.FLIGHT, 0, 70, "Sings the foe to sleep for 3–5 turns.", MoveEffect.Inflict(Status.SLEEP, 1.0, 3, 5)),
        m("wind_tunnel", "Wind Tunnel", Element.FLIGHT, 70, desc = "A spiraling blast of wind."),
        m("flying_kick", "Flying Kick", Element.FLIGHT, 80, desc = "A soaring kick."),
        m("wild_wind", "Wild Wind", Element.FLIGHT, 0, desc = "Slowest fighters move first for 3 rounds.", fx = *arrayOf(MoveEffect.ReverseOrder)),
        m("dive_bomb", "Dive Bomb", Element.FLIGHT, 120, desc = "Flies out of reach for a turn, then strikes. Thunder Bolt can hit it mid-air.", fx = *arrayOf(MoveEffect.TwoTurnDive)),

        // ---- Gas ----
        m("puff", "Puff", Element.GAS, 40, desc = "A burst of foul air."),
        m("poison_gas", "Poison Gas", Element.GAS, 0, 90, "Poisons the foe: 10% of its HP each turn.", MoveEffect.Inflict(Status.POISON, 1.0, 5, 5)),
        m("gunk_shot", "Gunk Shot", Element.GAS, 50, desc = "10% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.10, 5, 5))),
        m("toxic_wave", "Toxic Wave", Element.GAS, 60, desc = "25% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.25, 5, 5))),
        m("smog_screen", "Smog Screen", Element.GAS, 0, desc = "Thick smog: the foe's next move may miss and its Speed drops.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0), MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 1.0))),
        m("acid_rain", "Acid Rain", Element.GAS, 90, desc = "20% chance to lower Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.2))),
        m("miasma_burst", "Miasma Burst", Element.GAS, 110, 85, "An eruption of toxic gas. 20% chance to poison.", MoveEffect.Inflict(Status.POISON, 0.2, 5, 5)),

        // ---- Corrupt ----
        m("snarl", "Snarl", Element.CORRUPT, 40, desc = "Lowers the foe's Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 1.0))),
        m("dark_wave", "Dark Wave", Element.CORRUPT, 60, desc = "25% chance to lower Speed.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 0.25))),
        m("corruptive_bite", "Corruptive Bite", Element.CORRUPT, 60, desc = "15% chance to lower Speed, Defense and Attack.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED, BattleStat.DEFENSE, BattleStat.ATTACK), 0.15))),
        m("nightmare", "Nightmare", Element.CORRUPT, 0, desc = "80% chance of sleep; 20 damage every turn asleep.", fx = *arrayOf(MoveEffect.Nightmare(0.8, 20))),
        m("shadow_claw", "Shadow Claw", Element.CORRUPT, 90, desc = "Claws from the dark."),
        m("void_pulse", "Void Pulse", Element.CORRUPT, 110, 85, "A pulse from the void."),

        // ---- Earth ----
        m("seed_bomb", "Seed Bomb", Element.EARTH, 40, desc = "A burst of hard seeds."),
        m("mulch", "Mulch", Element.EARTH, 0, desc = "Burrows into rich soil and heals 10% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(10))),
        m("sap", "Sap", Element.EARTH, 40, desc = "Drains the foe and heals 10% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(10))),
        m("rock_throw", "Rock Throw", Element.EARTH, 60, desc = "Hurls a heavy stone."),
        m("mega_sap", "Mega Sap", Element.EARTH, 60, desc = "Drains the foe and heals 25% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(25))),
        m("vine_whip", "Vine Whip", Element.EARTH, 80, desc = "Lashes with thorny vines."),
        m("earthquake", "Earthquake", Element.EARTH, 100, 90, "The ground splits beneath the foe."),
        m("leaf_storm", "Leaf Storm", Element.EARTH, 120, 90, "A storm of razor leaves. The user must rest next turn.", MoveEffect.Recharge),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Move = byId.getValue(id)
    fun find(id: String): Move? = byId[id]

    /** (level, move id) pairs, per attack type. */
    val learnsets: Map<Element, List<Pair<Int, String>>> = mapOf(
        Element.NEUTRAL to listOf(1 to "tackle", 5 to "bite", 9 to "intimidate", 15 to "scare", 24 to "headbutt", 34 to "sleep_wave"),
        Element.FIRE to listOf(1 to "spit_fire", 7 to "fire_fang", 13 to "fire_wave", 18 to "solar_flare", 22 to "drought", 30 to "fire_storm", 40 to "blast_burn"),
        Element.ELECTRIC to listOf(1 to "spark", 5 to "shock", 9 to "thunder_shock", 16 to "thunder_wave", 26 to "thunder_bolt", 38 to "volt_crash"),
        Element.WATER to listOf(1 to "splash", 8 to "water_shot", 12 to "wave_guard", 18 to "rain", 28 to "hydro_blast", 36 to "hydro_surprise"),
        Element.FLIGHT to listOf(1 to "peck", 7 to "gust", 12 to "steel_wing", 17 to "lullaby", 22 to "wind_tunnel", 27 to "flying_kick", 32 to "wild_wind", 38 to "dive_bomb"),
        Element.GAS to listOf(1 to "puff", 6 to "poison_gas", 11 to "gunk_shot", 17 to "toxic_wave", 23 to "smog_screen", 30 to "acid_rain", 38 to "miasma_burst"),
        Element.CORRUPT to listOf(1 to "snarl", 10 to "dark_wave", 16 to "corruptive_bite", 24 to "nightmare", 30 to "shadow_claw", 38 to "void_pulse"),
        Element.EARTH to listOf(1 to "seed_bomb", 6 to "mulch", 10 to "sap", 15 to "rock_throw", 22 to "mega_sap", 28 to "vine_whip", 34 to "earthquake", 40 to "leaf_storm"),
    )

    /** Moves a monster of [species] knows at [level], with the level each was learned. */
    fun learnable(species: Species): List<Pair<Int, Move>> =
        (learnsets.getValue(species.element) + learnsets.getValue(Element.NEUTRAL))
            .map { (lvl, id) -> lvl to get(id) }
            .sortedBy { it.first }

    fun learned(species: Species, level: Int): List<Move> =
        learnable(species).filter { it.first <= level }.map { it.second }

    /**
     * Default loadout: the three strongest attacks (own type first on ties)
     * plus the newest status move, so monsters get better moves as they grow.
     */
    fun autoLoadout(species: Species, level: Int): List<Move> {
        val known = learned(species, level)
        val attacks = known.filter { it.isDamaging }
            .sortedWith(compareByDescending<Move> { it.power }.thenByDescending { it.type == species.element })
        val status = known.filter { !it.isDamaging }.lastOrNull()
        val picks = attacks.take(if (status != null) 3 else 4) + listOfNotNull(status)
        return (picks + known.filter { it !in picks }).take(MAX_MOVES)
    }

    const val MAX_MOVES = 4
}
