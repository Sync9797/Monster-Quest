package com.monsterquest.app.ui.art

import android.graphics.Canvas
import com.monsterquest.core.Armor
import com.monsterquest.core.ArmorType
import com.monsterquest.core.Element
import com.monsterquest.core.Gear
import com.monsterquest.core.GearRarity
import com.monsterquest.core.WeaponType
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Species
import java.util.Locale

/** Base, light and shadow colors per type. */
internal data class TypePalette(val base: Int, val light: Int, val dark: Int)

internal fun palette(e: Element): TypePalette = when (e) {
    Element.FIRE -> TypePalette(Argb.hex("d9734a"), Argb.hex("f3b27c"), Argb.hex("5a2418"))
    Element.ELECTRIC -> TypePalette(Argb.hex("d9b44a"), Argb.hex("f5e7a1"), Argb.hex("5a4a14"))
    Element.FLIGHT -> TypePalette(Argb.hex("86b2d4"), Argb.hex("d8ecf8"), Argb.hex("2a4660"))
    Element.WATER -> TypePalette(Argb.hex("4f8fc9"), Argb.hex("a9d3f2"), Argb.hex("173653"))
    Element.GAS -> TypePalette(Argb.hex("8fae5a"), Argb.hex("cfe3a2"), Argb.hex("33421c"))
    Element.CORRUPT -> TypePalette(Argb.hex("8a6bb8"), Argb.hex("cdb8ec"), Argb.hex("2d2142"))
    Element.NEUTRAL -> TypePalette(Argb.hex("9aa9a5"), Argb.hex("dfe6e3"), Argb.hex("2f3a38"))
}

private val GOLD = Argb.hex("ebc678")
private val EYE = Argb.hex("f9e9af")
private val GROUND = Argb.hex("071113")
/** Storybook ink for outlines and engraving. */
private val INK = Argb.hex("140d0a")
private val MIST = Argb.hex("cfe0dc")

// ---------------------------------------------------------------------------
// Hero: the hooded adventurer from Questbound, recolored per class.
// ---------------------------------------------------------------------------

private data class HeroLook(
    val capeTop: Int, val capeBottom: Int, val hood: Int, val arms: Int, val torso: Int, val hoodShade: Int,
)

private fun heroLook(cls: HeroClass) = when (cls) {
    HeroClass.NONE -> HeroLook(Argb.hex("52817a"), Argb.hex("163b3e"), Argb.hex("547f73"), Argb.hex("6e8c7b"), Argb.hex("35565a"), Argb.hex("152e32"))
    HeroClass.WARRIOR -> HeroLook(Argb.hex("9a4a3e"), Argb.hex("3a1513"), Argb.hex("6f7f86"), Argb.hex("8a979c"), Argb.hex("3c4a50"), Argb.hex("1c2427"))
    HeroClass.MAGE -> HeroLook(Argb.hex("6b5b95"), Argb.hex("2b2140"), Argb.hex("7c6aa6"), Argb.hex("8e7fb5"), Argb.hex("3b3157"), Argb.hex("1d1730"))
    HeroClass.ROGUE -> HeroLook(Argb.hex("3b4448"), Argb.hex("121719"), Argb.hex("2f3a3d"), Argb.hex("4b5a5c"), Argb.hex("23302f"), Argb.hex("0b1112"))
}

/** What the hero wears and holds; a null weapon or armor means nothing equipped. */
data class HeroOutfit(
    val weapon: WeaponType? = null,
    val weaponRarity: GearRarity = GearRarity.NORMAL,
    val weaponElement: Element = Element.NEUTRAL,
    val armor: ArmorType? = null,
    val armorRarity: GearRarity = GearRarity.NORMAL,
) {
    companion object {
        fun of(gear: Gear?, armor: Armor?) = HeroOutfit(
            gear?.type, gear?.rarity ?: GearRarity.NORMAL, gear?.element ?: Element.NEUTRAL,
            armor?.type, armor?.rarity ?: GearRarity.NORMAL,
        )

        /** The typical look of a class, for previews. */
        fun classic(cls: HeroClass) = when (cls) {
            HeroClass.NONE -> HeroOutfit(WeaponType.SWORD)
            HeroClass.WARRIOR -> HeroOutfit(WeaponType.BROADSWORD, armor = ArmorType.PLATE)
            HeroClass.MAGE -> HeroOutfit(WeaponType.STAFF, armor = ArmorType.ROBE)
            HeroClass.ROGUE -> HeroOutfit(WeaponType.DAGGER, armor = ArmorType.LEATHER)
        }
    }
}

private fun trim(r: GearRarity): Int = when (r) {
    GearRarity.NORMAL -> Argb.hex("b9c2c0")
    GearRarity.RARE -> Argb.hex("9cbfe4")
    GearRarity.EPIC -> Argb.hex("c3a6f0")
    GearRarity.LEGENDARY -> Argb.hex("ebc678")
}

private const val SKIN_HAND = "b29b79"

/** Draws the hero into [w]x[h] wearing [outfit]. [portrait] adds the halo, rings and sparkles. */
internal fun drawHero(c: Canvas, w: Float, h: Float, cls: HeroClass, portrait: Boolean, outfit: HeroOutfit = HeroOutfit.classic(cls)) {
    c.save()
    ArtPainter.fit(c, w, h, 300f, 280f)
    val look = heroLook(cls)
    if (portrait) {
        ArtPainter.glow(c, 150f, 140f, 125f, Argb.hex("d2b266"), 0.25f)
        ArtPainter.ringStroke(c, 150f, 135f, 100f, Argb.hex("bfa164"), 1.5f, 0.25f)
        ArtPainter.stroke(c, "M150 18V30M150 240V252M32 135H44M256 135H268", Argb.hex("bfa164"), 1.5f)
    }
    ArtPainter.fill(c, "M80 250A70 10 0 1 1 220 250A70 10 0 1 1 80 250Z", GROUND)
    if (outfit.armorRarity == GearRarity.LEGENDARY && outfit.armor != null) {
        ArtPainter.glow(c, 150f, 160f, 80f, trim(outfit.armorRarity), 0.3f)
    }
    if (portrait) ArtPainter.runeCircle(c, 150f, 250f, 92f, 12f, Argb.hex("bfa164"), 0.5f, glyphs = 16)
    val cape = "M120 118L91 233Q150 257 213 233L181 118Z"
    ArtPainter.fill(c, cape, ArtPainter.linear(91f, 118f, 213f, 245f, look.capeTop, look.capeBottom))
    ArtPainter.hatch(c, cape, look.capeBottom, 0.35f, 7f)
    // Cloak folds.
    ArtPainter.stroke(c, "M128 150L112 230M172 150L190 230M150 190V246", INK, 1.4f, 0.35f)
    ArtPainter.stroke(c, cape, INK, 2f, 0.75f)
    ArtPainter.fill(c, "M124 154L118 238L141 238L151 186L159 238L182 238L176 154Z", Argb.hex("14272d"))
    ArtPainter.fill(c, "M118 234H143V248H108Q106 239 118 234M159 234H184L194 248H159Z", Argb.hex("9a8061"))
    drawArmor(c, outfit, look)
    val hood = "M120 106Q111 66 151 47Q192 67 181 106L168 122H132Z"
    ArtPainter.fill(c, hood, look.hood)
    ArtPainter.hatch(c, hood, look.hoodShade, 0.3f, 7f)
    ArtPainter.stroke(c, hood, INK, 2f, 0.75f)
    ArtPainter.fill(c, "M130 91Q150 64 172 91L164 112H137Z", look.hoodShade)
    // A leaf-shaped brooch pins the cloak, in the style of old elven craft.
    ArtPainter.fill(c, "M150 112Q160 117 150 127Q140 117 150 112Z", Argb.hex("8fb58a"))
    ArtPainter.stroke(c, "M150 113V126", Argb.hex("e6c277"), 1.2f)
    ArtPainter.stroke(c, "M150 112Q160 117 150 127Q140 117 150 112Z", Argb.hex("e6c277"), 1.2f)
    ArtPainter.stroke(c, "M139 96H147M157 96H165", Argb.hex("edce83"), 3f)
    ArtPainter.fill(c, "M145 58L151 47L158 60L151 70Z", Argb.hex("e6c277"))
    drawWeapon(c, outfit)
    ArtPainter.fill(c, "M109 166L126 165L129 184L116 187Z", Argb.hex(SKIN_HAND))
    ArtPainter.fill(c, "M180 163L194 162L199 179L185 185Z", Argb.hex(SKIN_HAND))
    if (portrait) {
        val spark = Argb.hex("e9cb83")
        ArtPainter.fill(c, "M62 94l3 6 6 3-6 3-3 6-3-6-6-3 6-3z", spark)
        ArtPainter.fill(c, "M230 194l2 4 4 2-4 2-2 4-2-4-4-2 4-2z", spark)
        ArtPainter.circle(c, 218f, 65f, 2f, spark)
        ArtPainter.circle(c, 70f, 207f, 2f, spark)
    }
    c.restore()
}

private const val ARMS = "M116 121L106 165L126 177L139 144M179 121L194 164L177 176L164 142"
private const val TORSO = "M127 112L171 112L182 173L119 173Z"

private fun drawArmor(c: Canvas, o: HeroOutfit, look: HeroLook) {
    val t = trim(o.armorRarity)
    when (o.armor) {
        null -> {
            // Plain traveler's clothes.
            ArtPainter.fill(c, ARMS, look.arms)
            ArtPainter.fill(c, TORSO, look.torso)
            ArtPainter.stroke(c, TORSO, INK, 1.6f, 0.6f)
            ArtPainter.stroke(c, ARMS, INK, 1.4f, 0.5f)
            ArtPainter.fill(c, "M124 148H176V158H124Z", Argb.hex("b49b6d"))
            ArtPainter.fill(c, "M142 146H157V160H142Z", Argb.hex("eac774"))
        }
        ArmorType.PLATE -> {
            val (hi, lo) = when (o.armorRarity) {
                GearRarity.NORMAL -> Argb.hex("a3aeb2") to Argb.hex("3c4a50")
                GearRarity.RARE -> Argb.hex("a9c3d8") to Argb.hex("2e4258")
                GearRarity.EPIC -> Argb.hex("a594cf") to Argb.hex("2d2547")
                GearRarity.LEGENDARY -> Argb.hex("f0d58f") to Argb.hex("6b5126")
            }
            ArtPainter.fill(c, ARMS, ArtPainter.linear(106f, 121f, 194f, 177f, hi, lo))
            ArtPainter.fill(c, TORSO, ArtPainter.linear(127f, 112f, 182f, 173f, hi, lo))
            ArtPainter.stroke(c, TORSO, INK, 1.6f, 0.6f)
            ArtPainter.stroke(c, ARMS, INK, 1.4f, 0.5f)
            ArtPainter.stroke(c, "M150 116V170M132 128Q150 136 168 128", lo, 2f)
            // Pauldrons.
            ArtPainter.fill(c, "M104 128Q106 110 128 112L132 126Q118 122 104 128Z", hi)
            ArtPainter.fill(c, "M196 128Q194 110 172 112L168 126Q182 122 196 128Z", hi)
            ArtPainter.stroke(c, "M104 128Q106 110 128 112M196 128Q194 110 172 112", t, 2f)
            ArtPainter.fill(c, "M124 150H176V159H124Z", lo)
            ArtPainter.fill(c, "M143 148H157V161H143Z", t)
        }
        ArmorType.LEATHER -> {
            val hide = Argb.hex("7a5438")
            val dark = Argb.hex("3d2a1d")
            ArtPainter.fill(c, ARMS, look.arms)
            ArtPainter.fill(c, TORSO, ArtPainter.linear(127f, 112f, 182f, 173f, hide, dark))
            ArtPainter.stroke(c, TORSO, INK, 1.6f, 0.6f)
            ArtPainter.stroke(c, ARMS, INK, 1.4f, 0.5f)
            ArtPainter.stroke(c, "M129 116L176 168", dark, 5f)
            ArtPainter.stroke(c, "M129 116L176 168", t, 1.5f, 0.8f)
            // Bracers.
            ArtPainter.fill(c, "M107 156L125 160L124 170L106 166Z", hide)
            ArtPainter.fill(c, "M193 155L177 160L178 170L195 165Z", hide)
            ArtPainter.circle(c, 140f, 128f, 2.5f, t)
            ArtPainter.circle(c, 160f, 150f, 2.5f, t)
            ArtPainter.fill(c, "M124 152H176V159H124Z", dark)
        }
        ArmorType.ROBE -> {
            val (hi, lo) = when (o.armorRarity) {
                GearRarity.NORMAL -> Argb.hex("6f7f86") to Argb.hex("2e3a3e")
                GearRarity.RARE -> Argb.hex("4f7fb5") to Argb.hex("1c3150")
                GearRarity.EPIC -> Argb.hex("7c5fb3") to Argb.hex("2b1d47")
                GearRarity.LEGENDARY -> Argb.hex("efe3c2") to Argb.hex("9a7b45")
            }
            ArtPainter.fill(c, "M124 150L112 240Q150 250 188 240L176 150Z", ArtPainter.linear(150f, 150f, 150f, 245f, hi, lo))
            ArtPainter.stroke(c, "M150 160V244", t, 2f, 0.7f)
            ArtPainter.fill(c, ARMS, hi)
            ArtPainter.fill(c, TORSO, ArtPainter.linear(127f, 112f, 182f, 173f, hi, lo))
            ArtPainter.stroke(c, TORSO, INK, 1.6f, 0.6f)
            ArtPainter.stroke(c, ARMS, INK, 1.4f, 0.5f)
            ArtPainter.stroke(c, "M134 113L150 136L166 113", t, 2.5f)
            ArtPainter.fill(c, "M122 150H178V158H122Z", t)
        }
    }
}

private fun drawWeapon(c: Canvas, o: HeroOutfit) {
    val type = o.weapon ?: return // Bare-handed.
    val e = o.weaponElement
    val bladeHi = if (e == Element.NEUTRAL) Argb.hex("dcf5e3") else palette(e).light
    val bladeLo = if (e == Element.NEUTRAL) Argb.hex("96c8ba") else palette(e).base
    val guard = trim(o.weaponRarity).let { if (o.weaponRarity == GearRarity.NORMAL) Argb.hex("c9a86a") else it }
    val wood = Argb.hex("8c6951")
    if (o.weaponRarity >= GearRarity.EPIC) ArtPainter.glow(c, 196f, 120f, 46f, trim(o.weaponRarity), 0.4f)
    when (type) {
        WeaponType.SWORD -> {
            ArtPainter.fill(c, "M187 90L195 77L203 90L198 179H190Z", bladeHi)
            ArtPainter.fill(c, "M186 90L195 77V178H190Z", bladeLo)
            ArtPainter.fill(c, "M179 176H210V183H179Z", guard)
            ArtPainter.fill(c, "M191 183H198V203H191Z", wood)
        }
        WeaponType.BROADSWORD -> {
            ArtPainter.fill(c, "M183 66L196 46L209 66L205 179H187Z", bladeHi)
            ArtPainter.fill(c, "M183 66L196 46V179H187Z", bladeLo)
            ArtPainter.stroke(c, "M196 60V170", bladeLo, 1.5f, 0.6f)
            ArtPainter.fill(c, "M172 175H220L216 184H176Z", guard)
            ArtPainter.fill(c, "M192 184H200V207H192Z", wood)
            ArtPainter.circle(c, 196f, 210f, 4.5f, guard)
        }
        WeaponType.DAGGER -> {
            // A pair: one in each hand (daggers strike twice).
            for (dx in listOf(0f, -76f)) {
                c.save()
                c.translate(dx, 0f)
                ArtPainter.fill(c, "M189 140L195 128L201 140L198 176H192Z", bladeHi)
                ArtPainter.fill(c, "M189 140L195 128V176H192Z", bladeLo)
                ArtPainter.fill(c, "M181 176H209V182H181Z", guard)
                ArtPainter.fill(c, "M192 182H198V198H192Z", wood)
                c.restore()
            }
        }
        WeaponType.STAFF -> {
            ArtPainter.stroke(c, "M196 86V206", wood, 7f)
            val orb = if (e == Element.NEUTRAL) Argb.hex("9cd2bd") else palette(e).base
            val core = if (e == Element.NEUTRAL) Argb.hex("bfeedd") else palette(e).light
            ArtPainter.glow(c, 196f, 74f, 30f, orb, 0.55f)
            ArtPainter.circle(c, 196f, 74f, 11f, core)
            ArtPainter.circle(c, 193f, 71f, 4f, Argb.hex("ffffff"), 0.8f)
            ArtPainter.stroke(c, "M187 86Q196 92 205 86", guard, 3f)
        }
        WeaponType.WAND -> {
            ArtPainter.stroke(c, "M190 186L204 124", wood, 5f)
            ArtPainter.stroke(c, "M201 136L205 120", guard, 5f)
            val tip = if (e == Element.NEUTRAL) Argb.hex("f5e7a1") else palette(e).light
            ArtPainter.glow(c, 206f, 114f, 20f, tip, 0.6f)
            ArtPainter.fill(c, "M206 103l3 8 8 3-8 3-3 8-3-8-8-3 8-3z", tip)
        }
    }
}

// ---------------------------------------------------------------------------
// Monsters: one silhouette per evolution line, colored by type, with
// features added at each stage (gem, horns, halo) so evolutions look stronger.
// ---------------------------------------------------------------------------

private enum class Role { BODY, LIGHT, DARK, ACCENT, EYE, WOOD, TONGUE, SKIN, WING }

private class Part(val d: String, val role: Role, val stroke: Float = 0f)

private class Shape(val parts: List<Part>, val headX: Float, val headY: Float, val headR: Float)

private fun f(d: String, r: Role) = Part(d, r)
private fun s(d: String, r: Role, w: Float) = Part(d, r, w)

private val CAT = Shape(
    listOf(
        s("M138 172Q178 156 166 112", Role.BODY, 11f),
        f("M160 120Q170 104 176 116Q172 126 162 124Z", Role.LIGHT),
        f("M60 182Q54 124 100 114Q146 124 140 182Z", Role.BODY),
        f("M84 182Q86 142 100 136Q114 142 116 182Z", Role.LIGHT),
        f("M70 74L74 38L98 62Z", Role.BODY), f("M130 74L126 38L102 62Z", Role.BODY),
        f("M76 66L78 48L90 60Z", Role.DARK), f("M124 66L122 48L110 60Z", Role.DARK),
        f("M64 90A36 34 0 1 1 136 90A36 34 0 1 1 64 90Z", Role.BODY),
        f("M88 100Q100 94 112 100Q110 114 100 116Q90 114 88 100Z", Role.LIGHT),
        f("M96 100H104L100 105Z", Role.DARK),
        f("M78 86Q86 77 94 86Q86 92 78 86Z", Role.EYE), f("M106 86Q114 77 122 86Q114 92 106 86Z", Role.EYE),
        s("M86 82V89M114 82V89", Role.DARK, 3f),
        s("M70 102L84 104M70 110L84 108M130 102L116 104M130 110L116 108", Role.DARK, 2f),
        f("M72 180Q80 170 90 180Z", Role.LIGHT), f("M110 180Q120 170 128 180Z", Role.LIGHT),
    ),
    100f, 90f, 35f,
)

private val MOUSE = Shape(
    listOf(
        s("M140 170Q184 168 178 134Q174 116 188 104", Role.BODY, 5f),
        f("M58 182Q50 130 100 122Q150 130 142 182Z", Role.BODY),
        f("M82 182Q86 146 100 142Q114 146 118 182Z", Role.LIGHT),
        f("M50 64A20 20 0 1 1 90 64A20 20 0 1 1 50 64Z", Role.BODY),
        f("M110 64A20 20 0 1 1 150 64A20 20 0 1 1 110 64Z", Role.BODY),
        f("M58 64A12 12 0 1 1 82 64A12 12 0 1 1 58 64Z", Role.LIGHT),
        f("M118 64A12 12 0 1 1 142 64A12 12 0 1 1 118 64Z", Role.LIGHT),
        f("M68 98A32 30 0 1 1 132 98A32 30 0 1 1 68 98Z", Role.BODY),
        f("M82 94A6 6 0 1 1 94 94A6 6 0 1 1 82 94Z", Role.EYE),
        f("M106 94A6 6 0 1 1 118 94A6 6 0 1 1 106 94Z", Role.EYE),
        f("M96 108A4 4 0 1 1 104 108A4 4 0 1 1 96 108Z", Role.ACCENT),
        s("M74 108L88 110M74 116L88 114M126 108L112 110M126 116L112 114", Role.DARK, 2f),
    ),
    100f, 98f, 31f,
)

private val BIRD = Shape(
    listOf(
        f("M64 132Q26 138 36 172Q58 166 68 150Z", Role.LIGHT),
        f("M136 132Q174 138 164 172Q142 166 132 150Z", Role.LIGHT),
        f("M60 168Q50 104 100 92Q150 104 140 168Q100 190 60 168Z", Role.BODY),
        f("M78 170Q80 136 100 130Q120 136 122 170Q100 182 78 170Z", Role.LIGHT),
        f("M94 96Q88 74 100 64Q106 80 106 96Z", Role.ACCENT),
        f("M78 116A7 7 0 1 1 92 116A7 7 0 1 1 78 116Z", Role.EYE),
        f("M108 116A7 7 0 1 1 122 116A7 7 0 1 1 108 116Z", Role.EYE),
        f("M83 117A3 3 0 1 1 89 117A3 3 0 1 1 83 117Z", Role.DARK),
        f("M111 117A3 3 0 1 1 117 117A3 3 0 1 1 111 117Z", Role.DARK),
        f("M90 126L100 142L110 126Z", Role.ACCENT),
        s("M84 176V186M116 176V186", Role.ACCENT, 4f),
    ),
    100f, 116f, 34f,
)

private val FISH = Shape(
    listOf(
        f("M136 120L178 92L170 124L178 158Z", Role.LIGHT),
        f("M78 92Q98 60 124 98Z", Role.LIGHT),
        f("M86 150Q96 172 112 148Z", Role.LIGHT),
        f("M30 124Q72 70 138 104Q160 120 138 140Q72 176 30 124Z", Role.BODY),
        f("M44 134Q80 156 132 136Q90 168 44 134Z", Role.LIGHT),
        s("M70 104Q60 124 70 144", Role.DARK, 4f),
        s("M88 110Q96 118 88 126M104 110Q112 118 104 126M96 126Q104 134 96 142", Role.DARK, 2f),
        f("M44 116A8 8 0 1 1 60 116A8 8 0 1 1 44 116Z", Role.EYE),
        f("M49 117A3 3 0 1 1 55 117A3 3 0 1 1 49 117Z", Role.DARK),
    ),
    62f, 112f, 26f,
)

private val BUG = Shape(
    listOf(
        s("M60 140L40 132M58 156L36 158M62 170L44 180M140 140L160 132M142 156L164 158M138 170L156 180", Role.DARK, 5f),
        f("M56 178Q48 118 100 112Q152 118 144 178Z", Role.BODY),
        s("M58 140Q100 128 142 140M56 158Q100 148 144 158", Role.DARK, 5f),
        s("M88 70Q80 46 68 38M112 70Q120 46 132 38", Role.DARK, 4f),
        f("M63 38A5 5 0 1 1 73 38A5 5 0 1 1 63 38Z", Role.ACCENT),
        f("M127 38A5 5 0 1 1 137 38A5 5 0 1 1 127 38Z", Role.ACCENT),
        f("M70 92A30 28 0 1 1 130 92A30 28 0 1 1 70 92Z", Role.BODY),
        f("M79 90A9 9 0 1 1 97 90A9 9 0 1 1 79 90Z", Role.EYE),
        f("M103 90A9 9 0 1 1 121 90A9 9 0 1 1 103 90Z", Role.EYE),
        f("M84 91A4 4 0 1 1 92 91A4 4 0 1 1 84 91Z", Role.DARK),
        f("M108 91A4 4 0 1 1 116 91A4 4 0 1 1 108 91Z", Role.DARK),
        f("M92 110L96 120L100 112L104 120L108 110Z", Role.DARK),
    ),
    100f, 92f, 30f,
)

private val WISP = Shape(
    listOf(
        f("M40 120Q36 100 48 92Q46 108 54 116Z", Role.LIGHT),
        f("M160 120Q164 100 152 92Q154 108 146 116Z", Role.LIGHT),
        f("M58 174Q64 160 70 174Q78 160 86 176Q94 160 100 176Q106 160 114 176Q122 160 130 174Q136 160 142 174L142 102Q142 54 100 54Q58 54 58 102Z", Role.BODY),
        f("M70 96Q100 76 130 96Q128 128 100 132Q72 128 70 96Z", Role.DARK),
        f("M78 96L96 102L92 110L80 106Z", Role.EYE),
        f("M122 96L104 102L108 110L120 106Z", Role.EYE),
        s("M90 120Q100 126 110 120", Role.ACCENT, 3f),
    ),
    100f, 96f, 36f,
)

private val HOUND = Shape(
    listOf(
        s("M136 168Q172 158 166 122", Role.BODY, 12f),
        f("M62 184Q58 128 100 118Q142 128 138 184Z", Role.BODY),
        f("M86 128L100 156L114 128Z", Role.LIGHT),
        f("M70 78L62 38L92 62Z", Role.BODY), f("M130 78L138 38L108 62Z", Role.BODY),
        f("M72 70L68 50L86 62Z", Role.DARK), f("M128 70L132 50L114 62Z", Role.DARK),
        f("M66 92Q68 58 100 54Q132 58 134 92Q128 118 100 126Q72 118 66 92Z", Role.BODY),
        f("M86 100Q100 90 114 100Q110 118 100 120Q90 118 86 100Z", Role.LIGHT),
        f("M94 100Q100 96 106 100Q104 106 100 107Q96 106 94 100Z", Role.DARK),
        f("M76 84Q84 76 92 84Q84 90 76 84Z", Role.EYE), f("M108 84Q116 76 124 84Q116 90 108 84Z", Role.EYE),
        f("M70 182Q80 172 90 182Z", Role.LIGHT), f("M110 182Q120 172 130 182Z", Role.LIGHT),
    ),
    100f, 88f, 36f,
)

private val MIMIC = Shape(
    listOf(
        f("M48 120H152V182H48Z", Role.WOOD),
        f("M50 122H150V136H50Z", Role.DARK),
        f("M90 128Q100 152 112 128Z", Role.TONGUE),
        f("M52 122L58 132L64 122L70 132L76 122L82 132L88 122L94 132L100 122L106 132L112 122L118 132L124 122L130 132L136 122L142 132L148 122Z", Role.EYE),
        f("M46 116Q100 66 154 116V122H46Z", Role.WOOD),
        s("M70 86V182M130 86V182", Role.ACCENT, 6f),
        s("M48 152H152", Role.ACCENT, 5f),
        f("M94 142H106V158H94Z", Role.ACCENT),
        f("M76 100L92 104L88 110Z", Role.LIGHT),
        f("M124 100L108 104L112 110Z", Role.LIGHT),
    ),
    100f, 100f, 30f,
)

private val FAIRY = Shape(
    listOf(
        f("M96 110Q50 60 40 100Q50 128 96 118Z", Role.WING),
        f("M104 110Q150 60 160 100Q150 128 104 118Z", Role.WING),
        f("M96 122Q60 150 64 168Q84 166 98 128Z", Role.WING),
        f("M104 122Q140 150 136 168Q116 166 102 128Z", Role.WING),
        f("M84 120L68 178H132L116 120Z", Role.BODY),
        f("M88 104H112L116 124H84Z", Role.BODY),
        f("M84 86A16 16 0 1 1 116 86A16 16 0 1 1 84 86Z", Role.SKIN),
        f("M82 84Q84 64 100 64Q118 64 118 84Q110 74 100 76Q90 74 82 84Z", Role.ACCENT),
        f("M92 88A2 2 0 1 1 96 88A2 2 0 1 1 92 88Z", Role.DARK),
        f("M104 88A2 2 0 1 1 108 88A2 2 0 1 1 104 88Z", Role.DARK),
        s("M118 130L140 104", Role.ACCENT, 3f),
        f("M140 96L143 103L150 104L144 108L146 115L140 111L134 115L136 108L130 104L137 103Z", Role.EYE),
    ),
    100f, 86f, 16f,
)

private val SLIME = Shape(
    listOf(
        f("M60 176Q56 192 66 192Q74 188 72 176Z", Role.BODY),
        f("M128 178Q126 190 134 190Q140 186 138 176Z", Role.BODY),
        f("M44 180Q38 122 100 94Q162 122 156 180Q100 194 44 180Z", Role.BODY),
        f("M90 100Q100 70 110 100Z", Role.BODY),
        f("M62 140Q68 114 90 104Q76 122 72 146Z", Role.LIGHT),
        f("M126 152A5 5 0 1 1 136 152A5 5 0 1 1 126 152Z", Role.LIGHT),
        f("M116 170A3 3 0 1 1 122 170A3 3 0 1 1 116 170Z", Role.LIGHT),
        f("M72 134A10 12 0 1 1 92 134A10 12 0 1 1 72 134Z", Role.EYE),
        f("M108 134A10 12 0 1 1 128 134A10 12 0 1 1 108 134Z", Role.EYE),
        f("M78 136A4 5 0 1 1 86 136A4 5 0 1 1 78 136Z", Role.DARK),
        f("M114 136A4 5 0 1 1 122 136A4 5 0 1 1 114 136Z", Role.DARK),
        s("M90 156Q100 166 110 156", Role.DARK, 3f),
    ),
    100f, 126f, 34f,
)

private val GOLEM = Shape(
    listOf(
        f("M66 186L70 152H92L94 186Z", Role.DARK),
        f("M106 186L108 152H130L134 186Z", Role.DARK),
        f("M28 156L34 114L56 104L64 138L54 164L34 168Z", Role.BODY),
        f("M172 156L166 114L144 104L136 138L146 164L166 168Z", Role.BODY),
        f("M26 160L36 148L56 150L56 168L34 172Z", Role.DARK),
        f("M174 160L164 148L144 150L144 168L166 172Z", Role.DARK),
        f("M52 158L60 106L100 94L140 106L148 158L118 172H82Z", Role.BODY),
        s("M74 116L86 132L78 150M126 114L116 130L124 148", Role.DARK, 2.5f),
        f("M90 128L100 114L110 128L100 144Z", Role.LIGHT),
        f("M64 110L74 106L76 116Z", Role.LIGHT),
        f("M132 140L140 136L142 146Z", Role.LIGHT),
        f("M78 100L82 70L100 60L118 70L122 100L100 108Z", Role.BODY),
        f("M86 80H97V88H86Z", Role.EYE),
        f("M103 80H114V88H103Z", Role.EYE),
        s("M92 96H108", Role.DARK, 2.5f),
    ),
    100f, 84f, 22f,
)

private val THUNDERBIRD = Shape(
    listOf(
        f("M96 122Q60 84 22 80Q36 100 30 108Q46 108 50 118Q36 126 40 134Q66 128 90 142Z", Role.BODY),
        f("M104 122Q140 84 178 80Q164 100 170 108Q154 108 150 118Q164 126 160 134Q134 128 110 142Z", Role.BODY),
        s("M30 108L50 118M40 134L62 130M170 108L150 118M160 134L138 130", Role.DARK, 2.5f),
        f("M88 166L78 190L100 178L122 190L112 166Z", Role.DARK),
        f("M78 166Q72 122 100 106Q128 122 122 166Q100 178 78 166Z", Role.BODY),
        f("M88 164Q88 136 100 128Q112 136 112 164Q100 172 88 164Z", Role.LIGHT),
        f("M80 96A20 20 0 1 1 120 96A20 20 0 1 1 80 96Z", Role.BODY),
        f("M95 78L103 52L101 68L114 60L105 80Z", Role.ACCENT),
        f("M93 102L107 102L100 117Z", Role.ACCENT),
        f("M83 90L97 95L86 100Z", Role.EYE),
        f("M117 90L103 95L114 100Z", Role.EYE),
        s("M92 176V186M108 176V186", Role.ACCENT, 4f),
    ),
    100f, 94f, 20f,
)

private val TURTLE = Shape(
    listOf(
        f("M48 178Q42 160 58 156L72 170Q66 186 48 178Z", Role.LIGHT),
        f("M152 178Q158 160 142 156L128 170Q134 186 152 178Z", Role.LIGHT),
        f("M42 166Q46 102 100 94Q154 102 158 166Z", Role.BODY),
        s("M100 98V132M66 118L100 132L134 118M56 148L78 140M144 148L122 140", Role.DARK, 2.5f),
        f("M38 164H162Q162 176 150 178H50Q38 176 38 164Z", Role.DARK),
        f("M76 150A24 21 0 1 1 124 150A24 21 0 1 1 76 150Z", Role.LIGHT),
        f("M85 146A6 6 0 1 1 97 146A6 6 0 1 1 85 146Z", Role.EYE),
        f("M103 146A6 6 0 1 1 115 146A6 6 0 1 1 103 146Z", Role.EYE),
        f("M88 147A3 3 0 1 1 94 147A3 3 0 1 1 88 147Z", Role.DARK),
        f("M106 147A3 3 0 1 1 112 147A3 3 0 1 1 106 147Z", Role.DARK),
        s("M93 160Q100 165 107 160", Role.DARK, 2.5f),
    ),
    100f, 120f, 24f,
)

private val IMP = Shape(
    listOf(
        f("M78 124Q44 86 28 100Q40 108 34 118Q48 116 50 128Q60 122 72 136Z", Role.DARK),
        f("M122 124Q156 86 172 100Q160 108 166 118Q152 116 150 128Q140 122 128 136Z", Role.DARK),
        s("M120 172Q158 176 150 138", Role.DARK, 4f),
        f("M142 142L150 126L158 142L150 137Z", Role.DARK),
        f("M74 184Q70 138 100 128Q130 138 126 184Z", Role.BODY),
        f("M88 182Q88 152 100 146Q112 152 112 182Z", Role.LIGHT),
        f("M68 100L46 90L66 114Z", Role.BODY),
        f("M132 100L154 90L134 114Z", Role.BODY),
        f("M74 84L68 58L88 74Z", Role.DARK),
        f("M126 84L132 58L112 74Z", Role.DARK),
        f("M68 100A32 30 0 1 1 132 100A32 30 0 1 1 68 100Z", Role.BODY),
        f("M80 98Q88 89 97 100Q88 105 80 98Z", Role.EYE),
        f("M120 98Q112 89 103 100Q112 105 120 98Z", Role.EYE),
        f("M86 112Q100 126 114 112Q100 119 86 112Z", Role.DARK),
        f("M91 114L94 120L97 115Z", Role.EYE),
        f("M103 115L106 120L109 114Z", Role.EYE),
    ),
    100f, 100f, 30f,
)

private fun shapeFor(sp: Species): Shape = when {
    sp.id == "mimic" -> MIMIC
    sp.id == "prism_fairy" -> FAIRY
    sp.family.contains("Hound") -> HOUND
    sp.family.startsWith("Emberkit") -> CAT
    sp.family.startsWith("Zapmouse") -> MOUSE
    sp.family.startsWith("Fluffchick") -> BIRD
    sp.family.startsWith("Bubblefin") -> FISH
    sp.family.startsWith("Toxbug") -> BUG
    sp.family.startsWith("Gloomwisp") -> WISP
    sp.family.startsWith("Goopling") -> SLIME
    sp.family.startsWith("Pebblit") -> GOLEM
    sp.family.startsWith("Zipwing") -> THUNDERBIRD
    sp.family.startsWith("Shellsnap") -> TURTLE
    sp.family.startsWith("Impling") -> IMP
    else -> HOUND
}

private fun roleColor(r: Role, p: TypePalette): Int = when (r) {
    Role.BODY -> p.base
    Role.LIGHT -> p.light
    Role.DARK -> p.dark
    Role.ACCENT -> GOLD
    Role.EYE -> EYE
    Role.WOOD -> Argb.hex("7a5236")
    Role.TONGUE -> Argb.hex("c9536b")
    Role.SKIN -> Argb.hex("f2dcc2")
    Role.WING -> p.light
}

private fun fmt(v: Float) = String.format(Locale.US, "%.1f", v)

/** Draws a monster into [w]x[h]. [dim] grays it out (fainted / locked). */
internal fun drawMonster(c: Canvas, w: Float, h: Float, sp: Species, dim: Boolean = false) {
    if (sp.bossOnly) {
        drawLegendary(c, w, h, sp, dim)
        return
    }
    c.save()
    // Crop to where the creature actually is, so it fills its frame.
    ArtPainter.fitRegion(c, w, h, 22f, 28f, 156f, 166f)
    val p0 = palette(sp.element)
    val p = if (dim) TypePalette(Argb.hex("5d6866"), Argb.hex("8a9592"), Argb.hex("2a3331")) else p0
    val shape = shapeFor(sp)
    val final = sp.stages > 1 && sp.stage == sp.stages

    // How far along its line this form is: 0 = first form, 1 = final form.
    val power = if (sp.stages <= 1) 0f else (sp.stage - 1f) / (sp.stages - 1)
    if (!dim && sp.stage >= 2) {
        ArtPainter.glow(c, 100f, 115f, 60f + 25f * power, p.light, 0.2f + 0.2f * power)
        drawAura(c, sp.element, p, power)
    }
    if (!dim && sp.stage >= 3) drawSpiritWings(c, p, power, final)
    if (final && !dim) ArtPainter.ringStroke(c, 100f, 110f, 76f, GOLD, 2f, 0.4f)
    ArtPainter.fill(c, "M50 188A50 7 0 1 1 150 188A50 7 0 1 1 50 188Z", GROUND, 0.8f)
    if (!dim) ArtPainter.runeCircle(c, 100f, 188f, 66f, 10f, GOLD, 0.2f + 0.35f * power)

    // Bigger at each stage, anchored at the feet.
    val scale = if (sp.stages <= 1) 1f else 0.84f + 0.16f * (sp.stage - 1) / (sp.stages - 1)
    c.save()
    c.translate(100f, 186f)
    c.scale(scale, scale)
    c.translate(-100f, -186f)

    val bodyShader = ArtPainter.linear(0f, 40f, 0f, 190f, p.base, Argb.darken(p.base, 0.35f))
    for (part in shape.parts) {
        val color = roleColor(part.role, p)
        if (part.stroke > 0f) {
            ArtPainter.stroke(c, part.d, color, part.stroke)
        } else if (part.role == Role.BODY) {
            ArtPainter.fill(c, part.d, bodyShader)
            // Engraved shading, like an illustration in an old book of legends.
            ArtPainter.hatch(c, part.d, p.dark, 0.22f)
        } else {
            ArtPainter.fill(c, part.d, color, if (part.role == Role.WING) 0.7f else 1f)
        }
        if (part.stroke == 0f && part.role != Role.EYE) ArtPainter.stroke(c, part.d, INK, 1.6f, 0.75f)
    }
    // Evolved monsters' eyes burn with an inner light.
    if (!dim && sp.stage >= 2) {
        for (part in shape.parts) {
            if (part.role != Role.EYE || part.stroke > 0f) continue
            val b = ArtPainter.bounds(part.d)
            ArtPainter.glow(c, b.centerX(), b.centerY(), maxOf(b.width(), b.height()) * (1.1f + power), p.light, 0.45f)
        }
    }

    val hx = shape.headX
    val hy = shape.headY
    val r = shape.headR
    // Stage 3+: horns (birds and fish rely on their crest, gem and halo instead).
    if (sp.stage >= 3 && shape !== FAIRY && shape !== MIMIC && shape !== BIRD && shape !== FISH && shape !== THUNDERBIRD && shape !== SLIME) {
        val horn = { side: Float ->
            val bx = hx + side * r * 0.45f
            val by = hy - r * 0.75f
            "M${fmt(bx - 6)} ${fmt(by)}Q${fmt(bx + side * r * 0.5f)} ${fmt(by - r * 0.9f)} ${fmt(bx + side * r * 0.15f)} ${fmt(by - r * 1.35f)}" +
                "Q${fmt(bx + side * 2)} ${fmt(by - r * 0.6f)} ${fmt(bx + 6)} ${fmt(by)}Z"
        }
        ArtPainter.fill(c, horn(-1f), GOLD)
        ArtPainter.fill(c, horn(1f), GOLD)
    }
    // Stage 2+: a gem on the forehead.
    if (sp.stage >= 2 && shape !== FAIRY && shape !== MIMIC) {
        val gx = hx
        val gy = hy - r * 0.55f
        val g = 6f + sp.stage
        ArtPainter.fill(c, "M${fmt(gx)} ${fmt(gy - g)}L${fmt(gx + g * 0.7f)} ${fmt(gy)}L${fmt(gx)} ${fmt(gy + g)}L${fmt(gx - g * 0.7f)} ${fmt(gy)}Z", GOLD)
        ArtPainter.fill(c, "M${fmt(gx)} ${fmt(gy - g * 0.5f)}L${fmt(gx + g * 0.35f)} ${fmt(gy)}L${fmt(gx)} ${fmt(gy + g * 0.5f)}L${fmt(gx - g * 0.35f)} ${fmt(gy)}Z", p.light)
    }
    // Final forms: a crown of light (or a halo for winged, finned and spirit shapes).
    if (final && !dim && shape !== MIMIC) {
        val top = hy - r * 1.05f
        if (sp.stage >= 3 && shape !== FAIRY && shape !== BIRD && shape !== FISH && shape !== WISP && shape !== THUNDERBIRD && shape !== SLIME) {
            ArtPainter.glow(c, hx, top - 18f, 22f, GOLD, 0.35f)
            ArtPainter.ringStroke(c, hx, top - 22f, 7f, GOLD, 2f, 0.9f)
        } else {
            val cw = r * 0.75f
            val crown = "M${fmt(hx - cw)} ${fmt(top)}L${fmt(hx - cw)} ${fmt(top - 10)}L${fmt(hx - cw * 0.5f)} ${fmt(top - 4)}" +
                "L${fmt(hx)} ${fmt(top - 14)}L${fmt(hx + cw * 0.5f)} ${fmt(top - 4)}L${fmt(hx + cw)} ${fmt(top - 10)}L${fmt(hx + cw)} ${fmt(top)}Z"
            ArtPainter.glow(c, hx, top - 6f, 20f, GOLD, 0.35f)
            ArtPainter.fill(c, crown, GOLD)
            ArtPainter.circle(c, hx, top - 6f, 2.2f, p.light)
        }
    }
    c.restore()

    if (!dim) {
        // Low mist curling around the feet.
        ArtPainter.fill(c, "M34 192Q50 180 72 188Q90 178 108 188Q128 178 146 188Q160 182 168 192Z", MIST, 0.08f + 0.06f * power)
    }

    if (final && !dim) {
        ArtPainter.fill(c, "M36 60l2 5 5 2-5 2-2 5-2-5-5-2 5-2z", GOLD)
        ArtPainter.fill(c, "M162 44l2 4 4 2-4 2-2 4-2-4-4-2 4-2z", GOLD)
        ArtPainter.circle(c, 164f, 150f, 2.5f, GOLD)
    }
    c.restore()
}

/** Type-themed energy behind evolved monsters; grows with [power] (0..1). */
private fun drawAura(c: Canvas, e: Element, p: TypePalette, power: Float) {
    val a = 0.35f + 0.35f * power
    val spread = 1f + 0.25f * power
    c.save()
    c.translate(100f, 130f)
    c.scale(spread, spread)
    c.translate(-100f, -130f)
    when (e) {
        Element.FIRE -> {
            val flames = listOf(
                "M46 160Q34 120 56 92Q56 118 68 128Q62 108 74 86Q80 116 82 150Z",
                "M154 160Q166 120 144 92Q144 118 132 128Q138 108 126 86Q120 116 118 150Z",
                "M86 70Q92 48 100 34Q108 48 114 70Q106 62 100 72Q94 62 86 70Z",
            )
            flames.forEach { ArtPainter.fill(c, it, p.base, a) }
            flames.forEach { ArtPainter.fill(c, it, p.light, a * 0.4f) }
        }
        Element.ELECTRIC -> {
            ArtPainter.stroke(c, "M40 70L56 92L46 98L66 126M160 70L144 92L154 98L134 126M70 46L80 62L74 66L86 82M130 46L120 62L126 66L114 82", p.light, 3f, a + 0.2f)
            ArtPainter.stroke(c, "M40 70L56 92L46 98L66 126M160 70L144 92L154 98L134 126", p.base, 1.2f, a)
        }
        Element.WATER -> {
            ArtPainter.ringStroke(c, 100f, 120f, 62f, p.light, 2.5f, a)
            ArtPainter.ringStroke(c, 100f, 120f, 72f, p.base, 1.5f, a * 0.7f)
            listOf(40f to 90f, 160f to 96f, 58f to 56f, 146f to 52f).forEach { (x, y) ->
                ArtPainter.fill(c, "M$x ${y - 7}Q${x + 5} $y $x ${y + 5}Q${x - 5} $y $x ${y - 7}Z", p.light, a + 0.2f)
            }
        }
        Element.FLIGHT -> {
            ArtPainter.stroke(c, "M34 120Q40 80 80 66M166 120Q160 80 120 66M30 150Q46 140 54 150M170 150Q154 140 146 150", p.light, 3f, a + 0.1f)
            listOf(38f to 96f, 162f to 96f).forEach { (x, y) ->
                ArtPainter.fill(c, "M$x ${y - 10}Q${x + 6} $y $x ${y + 10}Q${x - 6} $y $x ${y - 10}Z", p.light, a)
            }
        }
        Element.GAS -> {
            listOf(Triple(44f, 140f, 14f), Triple(156f, 136f, 15f), Triple(58f, 96f, 10f), Triple(146f, 90f, 11f), Triple(100f, 52f, 9f)).forEach { (x, y, rr) ->
                ArtPainter.circle(c, x, y, rr, p.base, a * 0.6f)
                ArtPainter.circle(c, x + rr * 0.6f, y - rr * 0.3f, rr * 0.7f, p.light, a * 0.45f)
            }
        }
        Element.CORRUPT -> {
            ArtPainter.stroke(c, "M42 160Q30 120 52 100Q40 80 60 60M158 160Q170 120 148 100Q160 80 140 60", p.dark, 6f, a + 0.2f)
            ArtPainter.stroke(c, "M42 160Q30 120 52 100Q40 80 60 60M158 160Q170 120 148 100Q160 80 140 60", p.light, 1.5f, a)
            listOf(36f to 84f, 164f to 84f, 100f to 40f).forEach { (x, y) ->
                ArtPainter.fill(c, "M$x ${y - 6}L${x + 4} ${y}L$x ${y + 6}L${x - 4} ${y}Z", p.light, a + 0.2f)
            }
        }
        Element.NEUTRAL -> {
            ArtPainter.stroke(c, "M100 30V46M100 194V180M30 120H44M170 120H156M50 70L60 80M150 70L140 80", p.light, 2.5f, a)
        }
    }
    c.restore()
}

/** Translucent spirit wings behind stage 3+ monsters; larger with [power]. */
private fun drawSpiritWings(c: Canvas, p: TypePalette, power: Float, final: Boolean) {
    val wing = "M100 128Q66 70 26 56Q40 84 34 96Q50 96 54 106Q38 112 36 124Q58 120 64 128Q52 138 54 148Q76 136 100 140Z"
    val scale = 0.8f + 0.25f * power
    for (side in listOf(1f, -1f)) {
        c.save()
        c.translate(100f, 130f)
        c.scale(side * scale, scale)
        c.translate(-100f, -130f)
        ArtPainter.fill(c, wing, p.light, if (final) 0.55f else 0.4f)
        ArtPainter.stroke(c, wing, if (final) GOLD else p.base, 1.5f, 0.8f)
        ArtPainter.stroke(c, "M100 130Q70 96 40 74M100 134Q74 116 48 108M100 138Q80 132 60 136", p.base, 1f, 0.5f)
        c.restore()
    }
}

// ---------------------------------------------------------------------------
// Legendaries: the six Questbound bosses.
// ---------------------------------------------------------------------------

private class LPart(val d: String, val fill: Int? = null, val stroke: Int? = null, val w: Float = 0f, val alpha: Float = 1f)

/** C = the monster's type color. */
private const val C = 1

private fun bossBody(index: Int, color: Int): List<LPart> {
    fun k(v: Int?) = if (v == C) color else v
    val raw: List<LPart> = when (index) {
        0 -> listOf(
            LPart("M170 97L145 65L133 24L155 53L180 73M230 97L255 65L267 24L245 53L220 73", stroke = C, w = 9f),
            LPart("M163 104L130 129L111 191L135 204L160 161L159 224L180 237L200 215L221 237L242 223L239 162L265 204L289 190L269 129L238 104L221 80H180Z", fill = C),
            LPart("M164 113L178 92H219L234 114L218 144H180Z", fill = Argb.hex("163538")),
            LPart("M176 113L191 118M209 118L224 113", stroke = Argb.hex("f9e9af"), w = 5f),
            LPart("M180 160L200 146L221 160L213 186L200 199L187 186Z", fill = Argb.hex("244749")),
            LPart("M200 154L213 167L200 185L187 167Z", fill = Argb.hex("efd49a")),
            LPart("M128 143L144 152M121 171L136 180M256 145L273 154M266 172L280 182", stroke = Argb.hex("244749"), w = 6f),
        )
        1 -> listOf(
            LPart("M159 126L120 87L64 59L81 129L120 116L103 171L160 149M241 126L280 87L336 59L319 129L280 116L297 171L240 149", fill = Argb.hex("a34f3e"), stroke = C, w = 4f),
            LPart("M168 105L145 159L163 209L144 233H183L200 209L218 233H256L237 209L255 159L231 105L220 72H180Z", fill = C),
            LPart("M177 84L161 46L188 65M223 84L239 46L212 65", fill = C),
            LPart("M174 99L187 85H215L229 101L215 130H185Z", fill = Argb.hex("4b2c2a")),
            LPart("M179 102L193 107M208 107L222 102", stroke = Argb.hex("ffe8a1"), w = 5f),
            LPart("M179 151L200 137L222 151L213 189L200 206L186 188Z", fill = Argb.hex("f6cf7f")),
            LPart("M191 147L188 170L201 162L211 185L213 153Z", fill = Argb.hex("b6503b")),
        )
        2 -> listOf(
            LPart("M200 33L231 74L200 110L169 74Z", stroke = C, w = 3f),
            LPart("M185 80L200 60L216 80L209 105H191Z", fill = Argb.hex("d8e7ff")),
            LPart("M181 117L156 179L141 231L200 210L259 231L243 177L220 117Z", fill = C),
            LPart("M200 120L174 190L200 211L226 190Z", fill = Argb.hex("284563")),
            LPart("M155 123L134 145L145 169L160 150M245 123L266 145L255 169L240 150", fill = Argb.hex("d8e7ff")),
            LPart("M200 139L215 163L200 185L185 163Z", fill = Argb.hex("d8e7ff")),
            LPart("M101 97L116 119L101 142L86 119ZM299 97L314 119L299 142L284 119Z", fill = C, alpha = 0.7f),
            LPart("M114 193L124 207L114 221L104 207ZM286 193L296 207L286 221L276 207Z", fill = C, alpha = 0.6f),
        )
        3 -> listOf(
            LPart("M163 102L135 165L132 233H268L264 166L237 102Z", fill = Argb.hex("493d61")),
            LPart("M174 93L168 65L179 49L200 58L220 49L232 65L225 93L216 111H184Z", fill = C),
            LPart("M180 72H219V91H180Z", fill = Argb.hex("1e2032")),
            LPart("M186 81H194M207 81H215", stroke = Argb.hex("e6d1ff"), w = 3f),
            LPart("M177 117H224L233 172L215 187L200 172L183 187L166 171Z", fill = C),
            LPart("M180 184L168 232H191L201 207L209 232H234L220 184Z", fill = Argb.hex("8977b1")),
            LPart("M131 113L153 130V189L132 208L113 189V130Z", fill = Argb.hex("7a679e"), stroke = Argb.hex("d9c7f2"), w = 3f),
            LPart("M265 83L273 68L281 83L277 183H269Z", fill = Argb.hex("e5def0")),
            LPart("M254 179H292V186H254Z", fill = Argb.hex("b99c6b")),
            LPart("M270 186H277V211H270Z", fill = Argb.hex("846c54")),
        )
        4 -> listOf(
            LPart("M167 75L147 99L126 99L109 130L101 191L127 205L148 175L154 227H184L200 205L217 227H247L251 175L274 205L299 190L291 130L274 99H252L232 75Z", fill = C),
            LPart("M175 85L185 66H215L225 85L218 111H182Z", fill = Argb.hex("315d69")),
            LPart("M181 89H193M207 89H219", stroke = Argb.hex("eefbff"), w = 4f),
            LPart("M165 130L200 117L235 130L225 185L200 202L176 185Z", fill = Argb.hex("315d69")),
            LPart("M202 125L184 160H199L192 188L217 150H201L211 125Z", fill = Argb.hex("e5fbdf")),
            LPart("M99 57L80 93H96L78 125L113 84H95L111 57M301 57L320 93H304L322 125L287 84H305L289 57", fill = Argb.hex("c0f1e9")),
        )
        else -> listOf(
            LPart("M152 80A48 48 0 1 1 248 80A48 48 0 1 1 152 80Z", fill = Argb.hex("201b30"), stroke = C, w = 5f),
            LPart("M159 79Q199 14 241 79", stroke = Argb.hex("f4d9a0"), w = 4f),
            LPart("M173 130L141 184L122 232L164 220L185 238L200 221L218 238L239 220L278 232L258 184L227 130Z", fill = Argb.hex("62506f")),
            LPart("M186 129H215L226 188L200 213L174 188Z", fill = C),
            LPart("M187 77L196 81M205 81L215 77", stroke = Argb.hex("f6e5c1"), w = 4f),
            LPart("M145 136L112 154L131 176L154 160M255 136L288 154L269 176L246 160", fill = C),
            LPart("M187 163A13 13 0 1 1 213 163A13 13 0 1 1 187 163Z", fill = Argb.hex("f4d9a0")),
            LPart("M194 158A12 12 0 1 1 218 158A12 12 0 1 1 194 158Z", fill = Argb.hex("62506f")),
        )
    }
    return raw.map { LPart(it.d, k(it.fill), k(it.stroke), it.w, it.alpha) }
}

private fun legendaryIndex(id: String) = when (id) {
    "miasma_hydra" -> 0
    "magma_behemoth" -> 1
    "sky_serpent" -> 2
    "dread_lich" -> 3
    "thunder_titan" -> 4
    else -> 5 // abyss_kraken
}

private fun drawLegendary(c: Canvas, w: Float, h: Float, sp: Species, dim: Boolean) {
    c.save()
    ArtPainter.fitRegion(c, w, h, 62f, 10f, 276f, 240f)
    val color = if (dim) Argb.hex("5d6866") else palette(sp.element).base
    if (!dim) {
        ArtPainter.glow(c, 200f, 120f, 116f, color, 0.3f)
        ArtPainter.ringStroke(c, 200f, 110f, 77f, color, 2f, 0.3f)
    }
    ArtPainter.fill(c, "M120 237A80 10 0 1 1 280 237A80 10 0 1 1 120 237Z", Argb.hex("081315"))
    if (!dim) ArtPainter.runeCircle(c, 200f, 237f, 104f, 13f, GOLD, 0.55f, glyphs = 18)
    for (part in bossBody(legendaryIndex(sp.id), color)) {
        part.fill?.let { ArtPainter.fill(c, part.d, it, part.alpha) }
        part.stroke?.let { ArtPainter.stroke(c, part.d, it, part.w, part.alpha) }
    }
    if (!dim) {
        ArtPainter.circle(c, 73f, 108f, 2f, color, 0.55f)
        ArtPainter.circle(c, 310f, 69f, 3f, color, 0.55f)
        ArtPainter.circle(c, 90f, 181f, 2f, color, 0.55f)
        ArtPainter.circle(c, 301f, 181f, 2f, color, 0.55f)
    }
    c.restore()
}
