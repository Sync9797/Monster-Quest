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
    data class Scorch(val base: Int, val turns: Int, val verb: String = "is scorched") : MoveEffect
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
    /** The user's own [stats] rise by [stages] (up to +4). */
    data class RaiseSelf(val stats: List<BattleStat>, val stages: Int) : MoveEffect
    /** Strikes [min]..[max] times in a row (power is per hit). */
    data class MultiHit(val min: Int, val max: Int) : MoveEffect
    /** +25% chance of a critical hit. */
    data object HighCrit : MoveEffect
    /** Never misses, and can't be dodged. */
    data object SureHit : MoveEffect
    /** Cures the user's own status and lingering burns. */
    data object CureSelf : MoveEffect
    /** Strikes every foe at once (at 80% power each when there's more than one). */
    data object HitsAllFoes : MoveEffect
    /** Heals the user for [percent]% of the damage it deals. */
    data class LifeDrain(val percent: Int) : MoveEffect
    /** Goes through guards, Wave Guard and the foe's raised Defense. */
    data object Pierce : MoveEffect
    /** Every ally (the user included) heals [percent]% of its max HP. */
    data class TeamHeal(val percent: Int) : MoveEffect
    /** Every ally's [stats] rise by [stages]. */
    data class TeamRaise(val stats: List<BattleStat>, val stages: Int) : MoveEffect
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

    /** Average damage power, counting multiple hits and crit chances (for loadouts and AI). */
    val expectedPower: Double
        get() {
            val hits = effects.filterIsInstance<MoveEffect.MultiHit>().firstOrNull()?.let { (it.min + it.max) / 2.0 } ?: 1.0
            val crit = if (MoveEffect.HighCrit in effects) 1.12 else 1.0
            val spread = if (MoveEffect.HitsAllFoes in effects) 1.15 else 1.0
            return power * hits * crit * spread * accuracy / 100.0
        }
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
        m("quick_strike", "Quick Strike", Element.NEUTRAL, 40, desc = "Never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("fury_swipes", "Fury Swipes", Element.NEUTRAL, 18, desc = "Swipes 2 to 5 times.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("focus", "Focus", Element.NEUTRAL, 0, desc = "Sharply raises Attack.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 2))),
        m("slam", "Slam", Element.NEUTRAL, 80, 85, "A heavy body slam."),

        // ---- Fire ----
        m("spit_fire", "Spit Fire", Element.FIRE, 40, desc = "A gob of flame."),
        m("fire_fang", "Fire Fang", Element.FIRE, 50, desc = "10% chance to burn.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 0.10, 4, 4))),
        m("fire_wave", "Fire Wave", Element.FIRE, 60, desc = "Scorches for 5 damage a turn for 5 turns.", fx = *arrayOf(MoveEffect.Scorch(5, 5))),
        m("solar_flare", "Solar Flare", Element.FIRE, 0, desc = "Blinding light: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("drought", "Drought", Element.FIRE, 0, desc = "Dries up rain. Fire and Corrupt +25%, Water −15%.", fx = *arrayOf(MoveEffect.SetWeather(Weather.DROUGHT, 1.0))),
        m("fire_storm", "Fire Storm", Element.FIRE, 100, 85, "A raging firestorm."),
        m("blast_burn", "Blast Burn", Element.FIRE, 120, 90, "Huge damage, but the user must rest next turn.", MoveEffect.Recharge),
        m("flame_charge", "Flame Charge", Element.FIRE, 50, desc = "Cloaked in flame: raises the user's Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.SPEED), 1))),
        m("ember_barrage", "Ember Barrage", Element.FIRE, 22, desc = "Spits 2 to 5 embers.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("heat_claws", "Heat Claws", Element.FIRE, 70, desc = "Red-hot claws: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
        m("kindle", "Kindle", Element.FIRE, 0, desc = "Stokes its inner fire: cures its status and raises Attack.", fx = *arrayOf(MoveEffect.CureSelf, MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 1))),
        m("lava_plume", "Lava Plume", Element.FIRE, 85, desc = "30% chance to burn.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 0.3, 4, 4))),

        // ---- Electric ----
        m("spark", "Spark", Element.ELECTRIC, 40, desc = "A crackling jolt."),
        m("shock", "Shock", Element.ELECTRIC, 20, desc = "Shocks the foe: 25% chance each turn it can't act.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4))),
        m("thunder_shock", "Thunder Shock", Element.ELECTRIC, 40, desc = "15% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.15, 4, 4))),
        m("thunder_wave", "Thunder Wave", Element.ELECTRIC, 60, desc = "25% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.25, 4, 4))),
        m("thunder_bolt", "Thunder Bolt", Element.ELECTRIC, 80, desc = "25% chance to bring rain. Hits fliers mid-dive.", fx = *arrayOf(MoveEffect.SetWeather(Weather.RAIN, 0.25))),
        m("volt_crash", "Volt Crash", Element.ELECTRIC, 110, 90, "A reckless charge. 25% chance of 20 recoil.", MoveEffect.Recoil(20, 0.25)),
        m("quick_bolt", "Quick Bolt", Element.ELECTRIC, 45, desc = "Lightning-fast: never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("spark_storm", "Spark Storm", Element.ELECTRIC, 20, desc = "Zaps 2 to 5 times.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("charge", "Charge", Element.ELECTRIC, 0, desc = "Builds up power: raises Attack and Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK, BattleStat.SPEED), 1))),
        m("volt_fang", "Volt Fang", Element.ELECTRIC, 65, desc = "20% chance to shock, 10% chance the foe loses its turn.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.2, 4, 4), MoveEffect.Flinch(0.1))),
        m("zap_cannon", "Zap Cannon", Element.ELECTRIC, 120, 70, "Wildly inaccurate, but always shocks.", MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4)),

        // ---- Water ----
        m("splash", "Splash", Element.WATER, 20, desc = "Spray in the eyes: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("water_shot", "Water Shot", Element.WATER, 60, desc = "A pressurized jet."),
        m("wave_guard", "Wave Guard", Element.WATER, 0, desc = "Blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("rain", "Rain", Element.WATER, 0, desc = "Summons rain. Water and Electric +25%, Fire −15%.", fx = *arrayOf(MoveEffect.SetWeather(Weather.RAIN, 1.0))),
        m("hydro_blast", "Hydro Blast", Element.WATER, 100, 85, "A crushing torrent."),
        m("hydro_surprise", "Hydro Surprise", Element.WATER, 100, 85, "25% chance to bring rain.", MoveEffect.SetWeather(Weather.RAIN, 0.25)),
        m("aqua_jet", "Aqua Jet", Element.WATER, 45, desc = "A darting jet that never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("bubble_barrage", "Bubble Barrage", Element.WATER, 20, desc = "Fires 2 to 5 bubbles.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("soak", "Soak", Element.WATER, 0, desc = "Drenches the foe: its Attack and Speed fall.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.ATTACK, BattleStat.SPEED), 1.0))),
        m("tidal_slam", "Tidal Slam", Element.WATER, 85, desc = "Crashes down like a wave."),
        m("cleansing_wave", "Cleansing Wave", Element.WATER, 0, desc = "Washes away its status and heals 15% of its HP.", fx = *arrayOf(MoveEffect.CureSelf, MoveEffect.HealSelf(15))),

        // ---- Flight ----
        m("peck", "Peck", Element.FLIGHT, 40, desc = "A sharp jab."),
        m("gust", "Gust", Element.FLIGHT, 60, desc = "15% chance the foe misses its next attack.", fx = *arrayOf(MoveEffect.FoeMissesNext(0.15))),
        m("steel_wing", "Steel Wing", Element.FLIGHT, 40, desc = "Your damage rises 10% for the rest of the battle.", fx = *arrayOf(MoveEffect.PowerUp(10))),
        m("lullaby", "Lullaby", Element.FLIGHT, 0, 70, "Sings the foe to sleep for 3–5 turns.", MoveEffect.Inflict(Status.SLEEP, 1.0, 3, 5)),
        m("wind_tunnel", "Wind Tunnel", Element.FLIGHT, 70, desc = "A spiraling blast of wind."),
        m("flying_kick", "Flying Kick", Element.FLIGHT, 80, desc = "A soaring kick."),
        m("wild_wind", "Wild Wind", Element.FLIGHT, 0, desc = "Slowest fighters move first for 3 rounds.", fx = *arrayOf(MoveEffect.ReverseOrder)),
        m("dive_bomb", "Dive Bomb", Element.FLIGHT, 120, desc = "Flies out of reach for a turn, then strikes. Thunder Bolt can hit it mid-air.", fx = *arrayOf(MoveEffect.TwoTurnDive)),
        m("aerial_ace", "Aerial Ace", Element.FLIGHT, 60, desc = "A swooping strike that never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("feather_flurry", "Feather Flurry", Element.FLIGHT, 18, desc = "Flings 2 to 5 razor feathers.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("tailwind", "Tailwind", Element.FLIGHT, 0, desc = "Rides the wind: sharply raises Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.SPEED), 2))),
        m("razor_gale", "Razor Gale", Element.FLIGHT, 75, desc = "Cutting winds: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
        m("roost", "Roost", Element.FLIGHT, 0, desc = "Lands to rest and heals 30% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(30))),

        // ---- Gas ----
        m("puff", "Puff", Element.GAS, 40, desc = "A burst of foul air."),
        m("poison_gas", "Poison Gas", Element.GAS, 0, 90, "Poisons the foe: 10% of its HP each turn.", MoveEffect.Inflict(Status.POISON, 1.0, 5, 5)),
        m("gunk_shot", "Gunk Shot", Element.GAS, 50, desc = "10% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.10, 5, 5))),
        m("toxic_wave", "Toxic Wave", Element.GAS, 60, desc = "25% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.25, 5, 5))),
        m("smog_screen", "Smog Screen", Element.GAS, 0, desc = "Thick smog: the foe's next move may miss and its Speed drops.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0), MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 1.0))),
        m("acid_rain", "Acid Rain", Element.GAS, 90, desc = "20% chance to lower Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.2))),
        m("miasma_burst", "Miasma Burst", Element.GAS, 110, 85, "An eruption of toxic gas. 20% chance to poison.", MoveEffect.Inflict(Status.POISON, 0.2, 5, 5)),
        m("toxic_barrage", "Toxic Barrage", Element.GAS, 18, desc = "Spits 2 to 5 globs of venom.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("venom_fang", "Venom Fang", Element.GAS, 60, desc = "30% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.3, 5, 5))),
        m("spore_cloud", "Spore Cloud", Element.GAS, 0, 80, "Drowsy spores: puts the foe to sleep.", MoveEffect.Inflict(Status.SLEEP, 1.0, 2, 4)),
        m("corrode", "Corrode", Element.GAS, 70, desc = "Eats away at armor: 40% chance to lower Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.4))),
        m("purify", "Purify", Element.GAS, 0, desc = "Filters out toxins: cures its status and raises Defense.", fx = *arrayOf(MoveEffect.CureSelf, MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 1))),

        // ---- Corrupt ----
        m("snarl", "Snarl", Element.CORRUPT, 40, desc = "Lowers the foe's Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 1.0))),
        m("dark_wave", "Dark Wave", Element.CORRUPT, 60, desc = "25% chance to lower Speed.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 0.25))),
        m("corruptive_bite", "Corruptive Bite", Element.CORRUPT, 60, desc = "15% chance to lower Speed, Defense and Attack.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED, BattleStat.DEFENSE, BattleStat.ATTACK), 0.15))),
        m("nightmare", "Nightmare", Element.CORRUPT, 0, desc = "80% chance of sleep; 20 damage every turn asleep.", fx = *arrayOf(MoveEffect.Nightmare(0.8, 20))),
        m("shadow_claw", "Shadow Claw", Element.CORRUPT, 90, desc = "Claws from the dark."),
        m("void_pulse", "Void Pulse", Element.CORRUPT, 110, 85, "A pulse from the void."),
        m("sucker_punch", "Sucker Punch", Element.CORRUPT, 50, desc = "A dirty blow that never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("hex_barrage", "Hex Barrage", Element.CORRUPT, 20, desc = "Casts 2 to 5 small hexes.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("night_slash", "Night Slash", Element.CORRUPT, 70, desc = "A slash from the shadows: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
        m("dark_pact", "Dark Pact", Element.CORRUPT, 0, desc = "Trades life for power: sharply raises Attack, costs HP.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 2), MoveEffect.Recoil(15, 1.0))),
        m("soul_drain", "Soul Drain", Element.CORRUPT, 75, desc = "Drains the foe's spirit and heals 15% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(15))),

        // ---- Earth ----
        m("seed_bomb", "Seed Bomb", Element.EARTH, 40, desc = "A burst of hard seeds."),
        m("mulch", "Mulch", Element.EARTH, 0, desc = "Burrows into rich soil and heals 10% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(10))),
        m("sap", "Sap", Element.EARTH, 40, desc = "Drains the foe and heals 10% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(10))),
        m("rock_throw", "Rock Throw", Element.EARTH, 60, desc = "Hurls a heavy stone."),
        m("mega_sap", "Mega Sap", Element.EARTH, 60, desc = "Drains the foe and heals 25% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(25))),
        m("vine_whip", "Vine Whip", Element.EARTH, 80, desc = "Lashes with thorny vines."),
        m("earthquake", "Earthquake", Element.EARTH, 100, 90, "The ground splits beneath the foe."),
        m("leaf_storm", "Leaf Storm", Element.EARTH, 120, 90, "A storm of razor leaves. The user must rest next turn.", MoveEffect.Recharge),
        m("rock_blast", "Rock Blast", Element.EARTH, 22, desc = "Hurls 2 to 5 stones.", fx = *arrayOf(MoveEffect.MultiHit(2, 5))),
        m("harden", "Harden", Element.EARTH, 0, desc = "Turns its hide to stone: sharply raises Defense.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 2))),
        m("thorn_trap", "Thorn Trap", Element.EARTH, 0, desc = "Thorns that cut 6 damage a turn for 5 turns.", fx = *arrayOf(MoveEffect.Scorch(6, 5))),
        m("ground_pound", "Ground Pound", Element.EARTH, 70, desc = "20% chance the foe loses its next turn.", fx = *arrayOf(MoveEffect.Flinch(0.2))),
        m("stone_edge", "Stone Edge", Element.EARTH, 100, 80, "Jagged stones: high critical-hit chance.", MoveEffect.HighCrit),

        // ---- Starter signature moves: only the four starter lines learn these ----
        m("storm_antlers", "Storm Antlers", Element.ELECTRIC, 85, desc = "Lightning leaps from its antlers to every foe. 30% chance to shock each.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4))),
        m("heavens_charge", "Heaven's Charge", Element.ELECTRIC, 130, 95, "A divine charge that pierces guards and raised Defense, then raises the user's Speed.", MoveEffect.Pierce, MoveEffect.RaiseSelf(listOf(BattleStat.SPEED), 1)),
        m("wyrmfire", "Wyrmfire", Element.FIRE, 90, desc = "Dragonfire that always burns and keeps scorching for 3 turns.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 1.0, 4, 4), MoveEffect.Scorch(8, 3))),
        m("inferno_wings", "Inferno Wings", Element.FIRE, 125, 90, "Sweeps every foe with burning wings, then the user's Attack rises.", MoveEffect.HitsAllFoes, MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 1)),
        m("tidal_embrace", "Tidal Embrace", Element.WATER, 80, desc = "Wraps the foe in the tide and heals half the damage dealt.", fx = *arrayOf(MoveEffect.LifeDrain(50))),
        m("maelstrom_vortex", "Maelstrom Vortex", Element.WATER, 115, 95, "A crushing whirlpool: brings rain and traps the foe for 10 damage a turn for 4 turns.", MoveEffect.SetWeather(Weather.RAIN, 1.0), MoveEffect.Scorch(10, 4, "is battered by the whirlpool")),
        m("grove_guardian", "Grove Guardian", Element.EARTH, 0, desc = "The ancient grove shelters the team: every ally heals 20% and gains Defense.", fx = *arrayOf(MoveEffect.TeamHeal(20), MoveEffect.TeamRaise(listOf(BattleStat.DEFENSE), 1))),
        m("tectonic_fury", "Tectonic Fury", Element.EARTH, 120, 90, "Splits the earth under every foe. 30% chance each loses its turn.", MoveEffect.HitsAllFoes, MoveEffect.Flinch(0.3)),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Move = byId.getValue(id)
    fun find(id: String): Move? = byId[id]

    /** (level, move id) pairs, per attack type. */
    val learnsets: Map<Element, List<Pair<Int, String>>> = mapOf(
        Element.NEUTRAL to listOf(1 to "tackle", 3 to "quick_strike", 5 to "bite", 9 to "intimidate", 12 to "fury_swipes", 15 to "scare", 20 to "focus", 24 to "headbutt", 29 to "slam", 34 to "sleep_wave"),
        Element.FIRE to listOf(1 to "spit_fire", 4 to "flame_charge", 7 to "fire_fang", 11 to "ember_barrage", 13 to "fire_wave", 16 to "heat_claws", 18 to "solar_flare", 22 to "drought", 25 to "kindle", 30 to "fire_storm", 34 to "lava_plume", 40 to "blast_burn"),
        Element.ELECTRIC to listOf(1 to "spark", 3 to "quick_bolt", 5 to "shock", 9 to "thunder_shock", 12 to "spark_storm", 16 to "thunder_wave", 20 to "charge", 22 to "volt_fang", 26 to "thunder_bolt", 38 to "volt_crash", 44 to "zap_cannon"),
        Element.WATER to listOf(1 to "splash", 4 to "aqua_jet", 8 to "water_shot", 10 to "bubble_barrage", 12 to "wave_guard", 15 to "soak", 18 to "rain", 24 to "tidal_slam", 28 to "hydro_blast", 32 to "cleansing_wave", 36 to "hydro_surprise"),
        Element.FLIGHT to listOf(1 to "peck", 4 to "aerial_ace", 7 to "gust", 10 to "feather_flurry", 12 to "steel_wing", 15 to "tailwind", 17 to "lullaby", 22 to "wind_tunnel", 25 to "razor_gale", 27 to "flying_kick", 30 to "roost", 32 to "wild_wind", 38 to "dive_bomb"),
        Element.GAS to listOf(1 to "puff", 3 to "toxic_barrage", 6 to "poison_gas", 9 to "venom_fang", 11 to "gunk_shot", 14 to "spore_cloud", 17 to "toxic_wave", 20 to "corrode", 23 to "smog_screen", 27 to "purify", 30 to "acid_rain", 38 to "miasma_burst"),
        Element.CORRUPT to listOf(1 to "snarl", 4 to "sucker_punch", 8 to "hex_barrage", 10 to "dark_wave", 16 to "corruptive_bite", 19 to "night_slash", 24 to "nightmare", 27 to "dark_pact", 30 to "shadow_claw", 33 to "soul_drain", 38 to "void_pulse"),
        Element.EARTH to listOf(1 to "seed_bomb", 3 to "rock_blast", 6 to "mulch", 10 to "sap", 12 to "harden", 15 to "rock_throw", 18 to "thorn_trap", 22 to "mega_sap", 25 to "ground_pound", 28 to "vine_whip", 34 to "earthquake", 38 to "stone_edge", 40 to "leaf_storm"),
    )

    /**
     * Signature moves: each evolution line also learns a few moves of other
     * types that suit the creature (a drake's wings, a shark's bite, a
     * hound's elemental fangs), so lines of the same type fight differently.
     */
    val familyMoves: Map<String, List<Pair<Int, String>>> = mapOf(
        "Voltfawn" to listOf(18 to "gust", 22 to "storm_antlers", 30 to "ground_pound", 40 to "heavens_charge"),
        "Cindrake" to listOf(18 to "steel_wing", 22 to "wyrmfire", 30 to "razor_gale", 40 to "inferno_wings"),
        "Drizzlet" to listOf(18 to "venom_fang", 22 to "tidal_embrace", 34 to "thunder_bolt", 40 to "maelstrom_vortex"),
        "Mosscub" to listOf(20 to "night_slash", 22 to "grove_guardian", 32 to "tidal_slam", 40 to "tectonic_fury"),
        "Emberkit" to listOf(18 to "night_slash", 40 to "razor_gale"),
        "Zapmouse" to listOf(12 to "sucker_punch", 24 to "ground_pound"),
        "Fluffchick" to listOf(20 to "night_slash", 36 to "thunder_bolt"),
        "Bubblefin" to listOf(18 to "night_slash", 30 to "volt_fang"),
        "Toxbug" to listOf(20 to "night_slash", 30 to "razor_gale"),
        "Gloomwisp" to listOf(14 to "tailwind", 22 to "fire_wave"),
        "Hound" to listOf(14 to "fire_fang", 22 to "volt_fang", 30 to "venom_fang"),
        "Goopling" to listOf(12 to "bubble_barrage", 26 to "soul_drain"),
        "Pebblit" to listOf(8 to "rock_blast", 34 to "stone_edge"),
        "Zipwing" to listOf(10 to "aerial_ace", 26 to "razor_gale"),
        "Shellsnap" to listOf(10 to "harden", 24 to "ground_pound"),
        "Twiglet" to listOf(12 to "spore_cloud", 26 to "cleansing_wave"),
        "Impling" to listOf(10 to "ember_barrage", 30 to "lava_plume"),
        "Mimic" to listOf(8 to "fury_swipes", 20 to "stone_edge"),
        "Prism Fairy" to listOf(8 to "cleansing_wave", 20 to "quick_bolt"),
    )

    fun familyMovesFor(species: Species): List<Pair<Int, String>> =
        familyMoves.entries.firstOrNull { (key, _) -> species.family.startsWith(key) || (key == "Hound" && species.family.contains("Hound")) }
            ?.value.orEmpty()

    /** Moves a monster of [species] knows at [level], with the level each was learned. */
    fun learnable(species: Species): List<Pair<Int, Move>> =
        (learnsets.getValue(species.element) + learnsets.getValue(Element.NEUTRAL) + familyMovesFor(species))
            .distinctBy { it.second }
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
            .sortedWith(compareByDescending<Move> { it.expectedPower }.thenByDescending { it.type == species.element })
        val status = known.filter { !it.isDamaging }.lastOrNull()
        val picks = attacks.take(if (status != null) 3 else 4) + listOfNotNull(status)
        return (picks + known.filter { it !in picks }).take(MAX_MOVES)
    }

    const val MAX_MOVES = 4
}
