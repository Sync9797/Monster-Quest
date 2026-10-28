package com.monsterquest.app.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import com.monsterquest.core.ShrineTier

/** How a shrine beacon looks: lit and ready, recharging, or locked until its day. */
enum class BeaconState { OPEN, COOLING, LOCKED }

private val INK_MAP = Argb.hex("140d0a")

private fun tierColors(tier: ShrineTier): Pair<Int, Int> = when (tier) {
    ShrineTier.ITEM -> Argb.hex("6fb6e8") to Argb.hex("cdeaff")
    ShrineTier.SILVER -> Argb.hex("b7c0cc") to Argb.hex("f2f5fa")
    ShrineTier.GOLD -> Argb.hex("e3b44f") to Argb.hex("fff0c2")
}

/**
 * A shrine as a standing beacon (like a Pokémon GO stop): a carved stone
 * pillar with a floating crystal and, when it's ready, a beam of light.
 * Drawn in a 60x110 box with the base at the bottom center.
 */
internal fun drawShrineBeacon(c: Canvas, w: Float, h: Float, tier: ShrineTier, state: BeaconState) {
    c.save()
    ArtPainter.fit(c, w, h, 60f, 110f)
    val (base, light) = when (state) {
        BeaconState.OPEN -> tierColors(tier)
        BeaconState.COOLING -> Argb.hex("8a7fa6") to Argb.hex("c9bfe6")
        BeaconState.LOCKED -> Argb.hex("6b7477") to Argb.hex("a3abad")
    }
    val boss = tier != ShrineTier.ITEM
    ArtPainter.fill(c, "M12 104A18 5 0 1 1 48 104A18 5 0 1 1 12 104Z", Argb.hex("071113"), 0.7f)
    if (state == BeaconState.OPEN) {
        ArtPainter.runeCircle(c, 30f, 104f, 24f, 6f, base, 0.8f, glyphs = 8)
        // The beam of light rising from the crystal.
        val bw = if (boss) 9f else 6f
        ArtPainter.fill(
            c, "M${30 - bw} 40L${30 - bw * 0.4f} 0H${30 + bw * 0.4f}L${30 + bw} 40Z",
            ArtPainter.linear(0f, 0f, 0f, 40f, light and 0x00FFFFFF, (light and 0x00FFFFFF) or (0x99 shl 24)),
        )
    }
    // Stone pillar, darker for boss shrines.
    val stone = if (boss) Argb.hex("3c4547") else Argb.hex("56676a")
    val pillar = "M23 104L25 60H35L37 104Z"
    ArtPainter.fill(c, pillar, ArtPainter.linear(23f, 0f, 37f, 0f, Argb.mix(stone, 0xFFFFFFFF.toInt(), 0.2f), stone))
    ArtPainter.hatch(c, pillar, INK_MAP, 0.25f, 4f)
    ArtPainter.stroke(c, pillar, INK_MAP, 1.2f, 0.8f)
    ArtPainter.fill(c, "M19 56H41V61H19Z", stone)
    ArtPainter.stroke(c, "M19 56H41V61H19Z", INK_MAP, 1.2f, 0.8f)
    ArtPainter.fill(c, "M20 100H40V105H20Z", stone)
    ArtPainter.stroke(c, "M26 70H34M26 80H34M26 90H34", base, 1.4f, 0.8f)
    // Floating crystal: a gem for item shrines, an obelisk for silver, a crown-crystal for gold.
    if (state == BeaconState.OPEN) ArtPainter.glow(c, 30f, 38f, if (boss) 22f else 16f, light, 0.7f)
    val crystal = when (tier) {
        ShrineTier.ITEM -> "M30 26L39 38L30 50L21 38Z"
        ShrineTier.SILVER -> "M30 20L36 30L34 50H26L24 30Z"
        ShrineTier.GOLD -> "M18 32L24 24L30 30L36 24L42 32L36 50H24Z"
    }
    ArtPainter.fill(c, crystal, ArtPainter.linear(20f, 20f, 40f, 50f, light, base))
    ArtPainter.stroke(c, crystal, INK_MAP, 1.3f, 0.9f)
    ArtPainter.stroke(c, "M30 28V48", 0xFFFFFFFF.toInt(), 1f, 0.5f)
    when (state) {
        BeaconState.LOCKED -> {
            c.save()
            c.translate(21f, 29f)
            drawIcon(c, 18f, 18f, GameIcon.LOCK, Argb.hex("1c2427"), base)
            c.restore()
        }
        BeaconState.COOLING -> {
            c.save()
            c.translate(22f, 30f)
            drawIcon(c, 16f, 16f, GameIcon.HOURGLASS, Argb.hex("2b2140"), base)
            c.restore()
        }
        BeaconState.OPEN -> {}
    }
    c.restore()
}

/**
 * A thugs' or villain's camp: a tattered dark banner with a skull, and a red
 * glow for full villains. Drawn in a 60x90 box with the pole at bottom center.
 */
internal fun drawVillainBanner(c: Canvas, w: Float, h: Float, villain: Boolean) {
    c.save()
    ArtPainter.fit(c, w, h, 60f, 90f)
    val red = Argb.hex("b0303f")
    ArtPainter.fill(c, "M14 85A16 4 0 1 1 46 85A16 4 0 1 1 14 85Z", Argb.hex("071113"), 0.7f)
    if (villain) ArtPainter.glow(c, 30f, 40f, 30f, red, 0.5f)
    ArtPainter.stroke(c, "M22 86V10", Argb.hex("4a3426"), 3f)
    ArtPainter.circle(c, 22f, 9f, 3f, Argb.hex("c9a86a"))
    val flag = "M23 14H52L46 26L52 38L46 48H40L36 54L32 48H23Z"
    ArtPainter.fill(c, flag, ArtPainter.linear(23f, 14f, 52f, 50f, if (villain) Argb.hex("5a1a24") else Argb.hex("33302e"), Argb.hex("140d0a")))
    ArtPainter.hatch(c, flag, INK_MAP, 0.3f, 4f)
    ArtPainter.stroke(c, flag, if (villain) red else Argb.hex("8a7f74"), 1.4f)
    c.save()
    c.translate(28f, 20f)
    drawIcon(c, 18f, 18f, GameIcon.SKULL, Argb.hex("e8dccb"), Argb.hex("2a1416"))
    c.restore()
    c.restore()
}

/** A free-standing sprite bitmap (no badge), for things that stand on the map. */
fun spriteDrawable(context: Context, wDp: Int, hDp: Int, draw: (Canvas, Float, Float) -> Unit): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val bmp = Bitmap.createBitmap((wDp * d).toInt(), (hDp * d).toInt(), Bitmap.Config.ARGB_8888)
    draw(Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat())
    return BitmapDrawable(context.resources, bmp)
}

fun drawShrineOn(c: Canvas, w: Float, h: Float, tier: ShrineTier, state: BeaconState) = drawShrineBeacon(c, w, h, tier, state)

fun drawVillainOn(c: Canvas, w: Float, h: Float, villain: Boolean) = drawVillainBanner(c, w, h, villain)

/** A monster standing on the map; [lured] adds a pink lure glow at its feet, [alpha] a red one. */
fun drawMonsterStanding(c: Canvas, w: Float, h: Float, sp: com.monsterquest.core.Species, lured: Boolean, shiny: Boolean = false, alpha: Boolean = false) {
    if (alpha) {
        ArtPainter.glow(c, w / 2, h * 0.6f, w * 0.55f, Argb.hex("e0574a"), 0.75f)
    }
    if (lured) {
        ArtPainter.glow(c, w / 2, h * 0.9f, w * 0.45f, Argb.hex("f2a198"), 0.7f)
    }
    drawMonster(c, w, h, sp, shiny = shiny)
}

/** The player on the map: the hero in their gear, standing in a gold ring. */
fun drawPlayerOn(c: Canvas, w: Float, h: Float, cls: com.monsterquest.core.HeroClass, outfit: HeroOutfit) {
    val feetY = h * 0.86f
    ArtPainter.glow(c, w / 2, feetY, w * 0.42f, Argb.hex("ebc678"), 0.45f)
    c.drawOval(
        w * 0.14f, feetY - h * 0.07f, w * 0.86f, feetY + h * 0.07f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = w * 0.035f
            color = Argb.hex("ebc678")
        },
    )
    drawHero(c, w, h, cls, portrait = false, outfit = outfit)
}

/** A temporary map pin marking where a monster picked from the Nearby list is. */
fun drawFocusPin(c: Canvas, w: Float, h: Float) {
    c.save()
    ArtPainter.fit(c, w, h, 24f, 32f)
    ArtPainter.fill(c, "M12 31C12 31 3 19 3 12A9 9 0 1 1 21 12C21 19 12 31 12 31Z", Argb.hex("e0574a"))
    ArtPainter.stroke(c, "M12 31C12 31 3 19 3 12A9 9 0 1 1 21 12C21 19 12 31 12 31Z", Argb.hex("140d0a"), 1.2f, 0.8f)
    ArtPainter.circle(c, 12f, 12f, 3.5f, Argb.hex("ffffff"))
    c.restore()
}

/** A den: a rocky mound with a dark cave mouth and glowing eyes, tinted by its monster's type. Drawn in a 60x52 box. */
fun drawDenOn(c: Canvas, w: Float, h: Float, element: com.monsterquest.core.Element) {
    c.save()
    ArtPainter.fit(c, w, h, 60f, 52f)
    val tint = palette(element).base
    ArtPainter.fill(c, "M6 48A24 4 0 1 1 54 48A24 4 0 1 1 6 48Z", Argb.hex("071113"), 0.7f)
    ArtPainter.glow(c, 30f, 34f, 26f, tint, 0.45f)
    val rock = "M4 48L10 28L20 14L32 8L44 16L52 30L56 48Z"
    ArtPainter.fill(c, rock, ArtPainter.linear(0f, 8f, 0f, 48f, Argb.hex("7b766a"), Argb.hex("3a3630")))
    ArtPainter.hatch(c, rock, INK_MAP, 0.25f, 4f)
    ArtPainter.stroke(c, rock, INK_MAP, 1.4f, 0.85f)
    ArtPainter.fill(c, "M18 48Q18 26 30 24Q42 26 42 48Z", Argb.hex("0b0908"))
    ArtPainter.stroke(c, "M18 48Q18 26 30 24Q42 26 42 48", tint, 1.6f, 0.9f)
    ArtPainter.fill(c, "M23 36Q26 33 28 36Q26 38 23 36Z", palette(element).light)
    ArtPainter.fill(c, "M32 36Q34 33 37 36Q34 38 32 36Z", palette(element).light)
    // Moss and claw marks.
    ArtPainter.stroke(c, "M12 30L16 26M14 34L18 30M44 22L47 27M46 20L49 25", INK_MAP, 1.2f, 0.7f)
    c.restore()
}

/** A wandering trader: a cloaked figure in a green glow, with a coin above their pack. */
fun drawTraderOn(c: Canvas, w: Float, h: Float) {
    ArtPainter.glow(c, w / 2, h * 0.88f, w * 0.45f, Argb.hex("6fcf8a"), 0.55f)
    drawHero(c, w, h, com.monsterquest.core.HeroClass.ROGUE, portrait = false)
    // Their pack and a gold coin: something to sell.
    c.drawRoundRect(w * 0.62f, h * 0.42f, w * 0.88f, h * 0.68f, w * 0.05f, w * 0.05f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Argb.hex("8a5a33") })
    ArtPainter.circle(c, w * 0.78f, h * 0.2f, w * 0.12f, Argb.hex("ebc678"))
    ArtPainter.circle(c, w * 0.78f, h * 0.2f, w * 0.06f, Argb.hex("b8913f"))
}

/** A nomad adventurer: a hero in a blue glow, with a pennant to show they want a fair fight. */
fun drawNomadOn(c: Canvas, w: Float, h: Float) {
    ArtPainter.glow(c, w / 2, h * 0.88f, w * 0.45f, Argb.hex("6fa8e8"), 0.55f)
    drawHero(c, w, h, com.monsterquest.core.HeroClass.WARRIOR, portrait = false)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Argb.hex("e8e1d3"); strokeWidth = w * 0.03f }
    c.drawLine(w * 0.82f, h * 0.08f, w * 0.82f, h * 0.5f, p)
    val flag = android.graphics.Path().apply {
        moveTo(w * 0.82f, h * 0.08f); lineTo(w * 0.98f, h * 0.15f); lineTo(w * 0.82f, h * 0.22f); close()
    }
    ArtPainter.fill(c, flag, Argb.hex("3b82d6"))
}

/** A gold ring at a wanted monster's feet. */
fun drawWantedRing(c: Canvas, w: Float, h: Float) {
    ArtPainter.glow(c, w / 2, h / 2, w * 0.5f, Argb.hex("ffd34d"), 0.6f)
    c.drawOval(
        w * 0.08f, h * 0.3f, w * 0.92f, h * 0.7f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = w * 0.05f
            color = Argb.hex("ffd34d")
        },
    )
}
