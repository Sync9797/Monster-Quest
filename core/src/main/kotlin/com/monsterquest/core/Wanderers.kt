package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.random.Random

/** Something a trader sells: a stack of a shop item at a discount, or a weapon or piece of armor. */
data class TraderOffer(
    val index: Int,
    val item: Item? = null,
    val qty: Int = 1,
    val gear: Gear? = null,
    val armor: Armor? = null,
    /** Gold for the whole offer. */
    val price: Int,
    /** Percent off the shop price (items only). */
    val discountPct: Int = 0,
) {
    val label: String
        get() = item?.let { "${it.label} x$qty" } ?: gear?.let { "${it.rarity.label} ${it.name}" } ?: armor?.let { "${it.rarity.label} ${it.name}" } ?: "?"
}

/** A wandering trader on the map for the day. */
data class Trader(val id: String, val name: String, val lat: Double, val lon: Double, val offers: List<TraderOffer>)

/**
 * A nomad adventurer who challenges you: three of their monsters against the
 * hero and two of yours, with two swaps waiting on each side.
 */
data class Nomad(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val level: Int,
    /** The first [Wanderers.NOMAD_ACTIVE] fight; the rest are the swaps. */
    val team: List<Monster>,
) {
    val active: List<Monster> get() = team.take(Wanderers.NOMAD_ACTIVE)
    val reserve: List<Monster> get() = team.drop(Wanderers.NOMAD_ACTIVE)
}

/**
 * Friendly folk on the road, changing daily like the thugs: traders with
 * gear and cut-price supplies, and nomad adventurers who want a fair fight.
 */
object Wanderers {
    const val RANGE_M = 100.0
    const val TRADER_CHANCE = 0.12
    const val NOMAD_CHANCE = 0.12
    const val NOMAD_ACTIVE = 3
    const val NOMAD_SWAPS = 2
    /** Gear sells for this many times what the shop would pay for it. */
    const val GEAR_MARKUP = 3
    const val NOMAD_GOLD_PER_LEVEL = 40
    /** Perfect stats in the egg a nomad gives. */
    const val NOMAD_EGG_PERFECT = 3

    private const val CELL_DEG = 0.006
    private const val CELL_RADIUS = 3

    private val traderNames = listOf("Pell the Peddler", "Old Marta", "Juniper Wares", "Tobin Two-Packs", "Saffi of the Road", "Brass Ollie")
    private val nomadNames = listOf("Kaito the Wanderer", "Rhea Farstride", "Ansel Dusk", "Mira of the Steppe", "Corin Ashfoot", "Tamsin Longroad", "Odo the Drifter")

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun seed(x: Long, y: Long, day: Long, salt: Long): Long {
        var h = 1469598103934665603L
        for (p in longArrayOf(x, y, day, salt)) {
            h = (h xor p) * 1099511628211L
            h = h xor (h ushr 31)
        }
        return h
    }

    private inline fun <T> scan(lat: Double, lon: Double, day: Long, salt: Long, chance: Double, make: (Long, Long, Random) -> T?): List<T> {
        val cx = floor(lat / CELL_DEG).toLong()
        val cy = floor(lon / CELL_DEG).toLong()
        val out = mutableListOf<T>()
        for (dx in -CELL_RADIUS..CELL_RADIUS) for (dy in -CELL_RADIUS..CELL_RADIUS) {
            val r = Random(seed(cx + dx, cy + dy, day, salt))
            if (r.nextDouble() > chance) continue
            make(cx + dx, cy + dy, r)?.let { out += it }
        }
        return out
    }

    /** Shop items a trader may carry (only what you could buy at your level). */
    private fun stockable(level: Int) = Item.entries.filter { it.price > 0 && it.minLevel <= level }

    fun tradersNear(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Trader> {
        val day = epochDay(nowMs, zone)
        val level = state.hero.level
        return scan(lat, lon, day, 0x7ade, TRADER_CHANCE) { x, y, r ->
            val id = "t:$day:$x:$y"
            val offers = mutableListOf<TraderOffer>()
            repeat(2) {
                val g = Armory.roll(level, r, luck = 10)
                offers += TraderOffer(offers.size, gear = g, price = g.sellPrice * GEAR_MARKUP)
            }
            val a = Armory.rollArmor(level, r, luck = 10)
            offers += TraderOffer(offers.size, armor = a, price = a.sellPrice * GEAR_MARKUP)
            stockable(level).shuffled(r).take(3).forEach { item ->
                val off = 20 + r.nextInt(4) * 5 // 20-35% off
                val qty = 1 + r.nextInt(3)
                offers += TraderOffer(offers.size, item = item, qty = qty, price = item.price * qty * (100 - off) / 100, discountPct = off)
            }
            Trader(
                id, traderNames.random(r),
                (x + r.nextDouble()) * CELL_DEG, (y + r.nextDouble()) * CELL_DEG,
                offers.filter { "$id:${it.index}" !in state.traderBought },
            )
        }
    }

    fun buy(state: GameState, trader: Trader, index: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
        val o = trader.offers.find { it.index == index } ?: return Update(state, "Already sold.")
        val key = "${trader.id}:$index"
        if (key in state.traderBought) return Update(state, "Already sold.")
        if (state.gold < o.price) return Update(state, "Not enough gold (${o.price} needed).")
        val room = if (o.item != null) o.qty else 1
        if (state.bagCount + room > state.bagCapacity) return Update(state, "Bag is full (${state.bagCount}/${state.bagCapacity}).")
        val day = epochDay(nowMs, zone)
        var s = state.copy(
            gold = state.gold - o.price,
            traderBought = state.traderBought.filterValues { it >= day - 1 } + (key to day),
        )
        s = when {
            o.item != null -> GameEngine.addItems(s, mapOf(o.item to o.qty)).first
            o.gear != null -> s.copy(gear = s.gear + o.gear)
            o.armor != null -> s.copy(armor = s.armor + o.armor)
            else -> s
        }
        return Update(s, "Bought ${o.label} from ${trader.name} for ${o.price} gold.")
    }

    fun nomadsNear(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Nomad> {
        val day = epochDay(nowMs, zone)
        return scan(lat, lon, day, 0x40ad, NOMAD_CHANCE) { x, y, r ->
            val id = "n:$day:$x:$y"
            if (id in state.nomadsBeaten) return@scan null
            val level = (state.hero.level + 1 + r.nextInt(3)).coerceAtLeast(3)
            val team = List(NOMAD_ACTIVE + NOMAD_SWAPS) {
                val lvl = (level + r.nextInt(-1, 2)).coerceAtLeast(2)
                Monster.create(Bestiary.trained(r, lvl), lvl, r).withWildMoves(r)
            }
            Nomad(id, nomadNames.random(r), (x + r.nextDouble()) * CELL_DEG, (y + r.nextDouble()) * CELL_DEG, level, team)
        }
    }

    /**
     * Beat a nomad: gold, two Hi-Potions, and an egg of one of their monsters'
     * lines with [NOMAD_EGG_PERFECT] perfect stats (two Great Orbs instead if
     * your egg slots are full, or their whole team can't breed).
     */
    fun nomadReward(state: GameState, nomad: Nomad, nowMs: Long, rng: Random = Random.Default, zone: ZoneId = ZoneId.systemDefault()): Update {
        val day = epochDay(nowMs, zone)
        val gold = nomad.level * NOMAD_GOLD_PER_LEVEL
        var s = state.copy(
            gold = state.gold + gold,
            nomadsBeaten = state.nomadsBeaten.filterValues { it >= day - 1 } + (nomad.id to day),
        )
        s = GameEngine.addItems(s, mapOf(Item.HI_POTION to 2)).first
        val parent = nomad.team.filter { Eggs.breedable(it.species) }.randomOrNull(rng)
        val gift = if (parent != null && s.eggs.size < Eggs.MAX_EGGS) {
            val egg = Eggs.gift(parent.species, rng, NOMAD_EGG_PERFECT)
            s = s.copy(eggs = s.eggs + egg)
            "an egg of ${parent.displayName}'s line (${NOMAD_EGG_PERFECT} perfect stats; walk ${"%.1f".format(egg.meters / 1000)} km to hatch)"
        } else {
            s = GameEngine.addItems(s, mapOf(Item.GREAT_ORB to 2)).first
            "2 Great Orbs" + if (parent != null) " (your egg slots are full)" else ""
        }
        return Update(s, "${nomad.name} tips their hat: \"Well fought!\" +$gold gold, 2 Hi-Potions and $gift.")
    }
}
