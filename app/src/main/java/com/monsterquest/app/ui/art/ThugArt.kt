package com.monsterquest.app.ui.art

import android.graphics.Canvas
import com.monsterquest.core.HeroClass

private val INK = Argb.hex("0a0606")
private val EYE = Argb.hex("ff3b30")
private val EYE_CORE = Argb.hex("ffd2c8")

/** Glowing red eyes in a dark face. */
private fun eyes(c: Canvas, y: Float, gap: Float = 8f, color: Int = EYE) {
    for (x in listOf(150f - gap, 150f + gap)) {
        ArtPainter.glow(c, x, y, 11f, color, 0.65f)
        ArtPainter.circle(c, x, y, 3.2f, color)
        ArtPainter.circle(c, x, y, 1.3f, EYE_CORE)
    }
}

/** A ragged cape with a torn, jagged hem. */
private fun tatteredCape(c: Canvas, top: Int, bottom: Int) {
    val cape = "M118 116L86 236L100 226L108 244L122 230L132 248L150 234L168 248L178 230L192 244L200 226L214 236L182 116Z"
    ArtPainter.fill(c, cape, ArtPainter.linear(86f, 116f, 214f, 248f, top, bottom))
    ArtPainter.hatch(c, cape, bottom, 0.4f, 6f)
    ArtPainter.stroke(c, "M128 150L112 228M172 150L190 228M150 186V236", INK, 1.4f, 0.5f)
    ArtPainter.stroke(c, cape, INK, 2f, 0.8f)
}

private fun legsAndBoots(c: Canvas, cloth: Int, boot: Int) {
    ArtPainter.fill(c, "M124 154L118 238L141 238L151 186L159 238L182 238L176 154Z", cloth)
    val feet = "M117 228H143V248H105Q104 236 117 228ZM159 228H185L196 248H159Z"
    ArtPainter.fill(c, feet, boot)
    ArtPainter.stroke(c, feet, INK, 1.4f, 0.7f)
    // Spikes on the boots.
    ArtPainter.fill(c, "M118 230L112 222L124 228ZM184 230L190 222L178 228Z", Argb.hex("8a8f92"))
}

/**
 * A thug, drawn to look evil and different for each kind: a horned, spiked
 * Brute (warrior), a hooded Hexer with a skull staff (mage), and a masked
 * Cutthroat with twin curved daggers (rogue). [w]x[h], same proportions as the hero.
 */
internal fun drawThug(c: Canvas, w: Float, h: Float, cls: HeroClass) {
    c.save()
    ArtPainter.fit(c, w, h, 300f, 280f)
    // A shadow pooled in red under their feet.
    ArtPainter.glow(c, 150f, 248f, 70f, Argb.hex("8a1010"), 0.35f)
    ArtPainter.fill(c, "M80 250A70 10 0 1 1 220 250A70 10 0 1 1 80 250Z", Argb.hex("100506"))
    when (cls) {
        HeroClass.WARRIOR -> brute(c)
        HeroClass.MAGE -> hexer(c)
        else -> cutthroat(c)
    }
    c.restore()
}

private fun brute(c: Canvas) {
    tatteredCape(c, Argb.hex("4a1416"), Argb.hex("120506"))
    legsAndBoots(c, Argb.hex("1e1718"), Argb.hex("2c2422"))
    val ironHi = Argb.hex("5d6467")
    val ironLo = Argb.hex("16191a")
    // Thick arms and a broad, scarred breastplate.
    val arms = "M114 120L96 170L122 184L136 142M186 120L204 170L178 184L164 142"
    ArtPainter.fill(c, arms, ArtPainter.linear(96f, 120f, 204f, 184f, ironHi, ironLo))
    ArtPainter.stroke(c, arms, INK, 1.6f, 0.7f)
    val torso = "M120 110L180 110L194 178L106 178Z"
    ArtPainter.fill(c, torso, ArtPainter.linear(120f, 110f, 194f, 178f, ironHi, ironLo))
    ArtPainter.stroke(c, torso, INK, 2f, 0.8f)
    // A crude red skull daubed on the chest.
    ArtPainter.fill(c, "M141 128Q150 118 159 128Q160 138 155 141V146H145V141Q140 138 141 128Z", Argb.hex("9e1b1b"))
    ArtPainter.circle(c, 146f, 131f, 2.4f, INK)
    ArtPainter.circle(c, 154f, 131f, 2.4f, INK)
    ArtPainter.stroke(c, "M147 146V142M150 146V142M153 146V142", INK, 1f)
    // Chain belt.
    ArtPainter.fill(c, "M108 160H192V170H108Z", Argb.hex("2a2526"))
    for (i in 0..7) ArtPainter.ringStroke(c, 113f + i * 11f, 165f, 4f, Argb.hex("7c8285"), 1.6f)
    // Spiked pauldrons.
    ArtPainter.fill(c, "M94 134Q96 104 130 108L134 128Q114 122 94 134Z", ironHi)
    ArtPainter.fill(c, "M206 134Q204 104 170 108L166 128Q186 122 206 134Z", ironHi)
    ArtPainter.stroke(c, "M94 134Q96 104 130 108M206 134Q204 104 170 108", INK, 1.6f)
    ArtPainter.fill(c, "M98 122L82 102L108 114ZM114 110L108 88L124 106ZM202 122L218 102L192 114ZM186 110L192 88L176 106Z", Argb.hex("b9bec0"))
    // Horned helm with a black visor slit.
    val helm = "M122 110Q118 62 150 56Q182 62 178 110L166 120H134Z"
    ArtPainter.fill(c, helm, ArtPainter.linear(122f, 56f, 178f, 120f, Argb.hex("4b5154"), Argb.hex("121415")))
    ArtPainter.stroke(c, helm, INK, 2f, 0.85f)
    ArtPainter.fill(c, "M128 76Q100 68 94 38Q112 58 134 64Z", Argb.hex("cbbca4"))
    ArtPainter.fill(c, "M172 76Q200 68 206 38Q188 58 166 64Z", Argb.hex("cbbca4"))
    ArtPainter.stroke(c, "M128 76Q100 68 94 38M172 76Q200 68 206 38", INK, 1.4f, 0.8f)
    ArtPainter.fill(c, "M132 86H168L163 98H137Z", Argb.hex("040202"))
    eyes(c, 92f)
    // A spiked club, raised.
    ArtPainter.stroke(c, "M194 178L224 96", Argb.hex("4a3022"), 8f)
    ArtPainter.fill(c, "M214 64Q240 68 238 102L222 114Q206 96 214 64Z", ArtPainter.linear(206f, 64f, 240f, 114f, Argb.hex("6b5a4c"), Argb.hex("2a2019")))
    ArtPainter.fill(c, "M216 66L208 50L224 64ZM236 78L250 70L238 90ZM238 100L252 106L232 108ZM208 92L194 88L210 102Z", Argb.hex("c3c7c9"))
    ArtPainter.stroke(c, "M214 64Q240 68 238 102L222 114Q206 96 214 64Z", INK, 1.6f)
    // Gauntlets.
    ArtPainter.fill(c, "M104 168L124 166L128 188L112 190Z", Argb.hex("2e3233"))
    ArtPainter.fill(c, "M184 166L202 164L206 182L190 188Z", Argb.hex("2e3233"))
}

private fun hexer(c: Canvas) {
    val robeHi = Argb.hex("3a1b4d")
    val robeLo = Argb.hex("0b0510")
    val rune = Argb.hex("b64dff")
    // Floating dark orbs.
    for ((x, y) in listOf(70f to 120f, 232f to 150f, 84f to 190f)) {
        ArtPainter.glow(c, x, y, 16f, rune, 0.45f)
        ArtPainter.circle(c, x, y, 6f, Argb.hex("1a0a24"))
        ArtPainter.ringStroke(c, x, y, 6f, rune, 1.4f, 0.9f)
    }
    tatteredCape(c, Argb.hex("2a1236"), Argb.hex("07030a"))
    // A long robe down to the ground, scrawled with glowing runes.
    val robe = "M122 112L104 246Q150 258 196 246L178 112Z"
    ArtPainter.fill(c, robe, ArtPainter.linear(150f, 112f, 150f, 250f, robeHi, robeLo))
    ArtPainter.stroke(c, robe, INK, 2f, 0.8f)
    ArtPainter.glow(c, 150f, 200f, 30f, rune, 0.25f)
    ArtPainter.stroke(c, "M142 176L150 186L158 176M140 204L150 196L160 204L150 214Z M136 232H164", rune, 2f, 0.85f)
    ArtPainter.stroke(c, "M150 120V244", Argb.hex("5c2a7a"), 1.4f, 0.8f)
    // Wide, ragged sleeves.
    val sleeves = "M120 118L94 178L104 172L110 184L122 176L136 140M180 118L206 178L196 172L190 184L178 176L164 140"
    ArtPainter.fill(c, sleeves, robeHi)
    ArtPainter.stroke(c, sleeves, INK, 1.6f, 0.75f)
    // Bony fingers.
    ArtPainter.stroke(c, "M104 178L100 190M108 180L106 192M112 180L113 191", Argb.hex("c9c0b3"), 2f)
    ArtPainter.stroke(c, "M190 180L194 192M194 178L200 189", Argb.hex("c9c0b3"), 2f)
    // A tall, crooked hood; nothing inside but darkness and eyes.
    val hood = "M118 114Q110 62 146 20Q156 40 176 60Q192 90 184 114L168 126H132Z"
    ArtPainter.fill(c, hood, ArtPainter.linear(118f, 20f, 184f, 126f, Argb.hex("33174a"), Argb.hex("0d0614")))
    ArtPainter.hatch(c, hood, Argb.hex("07030a"), 0.35f, 6f)
    ArtPainter.stroke(c, hood, INK, 2f, 0.85f)
    ArtPainter.fill(c, "M131 94Q150 66 169 94L162 118H138Z", Argb.hex("030104"))
    eyes(c, 96f, color = Argb.hex("ff2bd6"))
    // Skull staff with a purple flame.
    ArtPainter.stroke(c, "M90 246L104 88", Argb.hex("3a2a22"), 6f)
    ArtPainter.stroke(c, "M92 220L98 214M96 180L102 176", Argb.hex("6d5644"), 2f)
    ArtPainter.glow(c, 104f, 62f, 26f, rune, 0.55f)
    ArtPainter.fill(c, "M104 30Q118 48 112 62Q104 56 104 66Q96 56 98 44Q102 50 104 30Z", Argb.hex("d48cff"))
    ArtPainter.fill(c, "M93 78Q92 64 104 64Q116 64 115 78Q114 86 110 88V92H98V88Q94 86 93 78Z", Argb.hex("ddd4c6"))
    ArtPainter.circle(c, 100f, 77f, 3f, INK)
    ArtPainter.circle(c, 108f, 77f, 3f, INK)
    ArtPainter.circle(c, 100f, 77f, 1.2f, rune)
    ArtPainter.circle(c, 108f, 77f, 1.2f, rune)
}

private fun cutthroat(c: Canvas) {
    val blood = Argb.hex("a3161a")
    tatteredCape(c, Argb.hex("2b1b1d"), Argb.hex("0a0606"))
    legsAndBoots(c, Argb.hex("191314"), Argb.hex("241a18"))
    val leather = Argb.hex("2e2224")
    val leatherLo = Argb.hex("0c0809")
    val arms = "M118 120L104 166L122 176L136 142M182 120L196 166L178 176L164 142"
    ArtPainter.fill(c, arms, leather)
    ArtPainter.stroke(c, arms, INK, 1.4f, 0.7f)
    // Arm wraps.
    ArtPainter.stroke(c, "M108 150L124 156M106 158L122 164M192 150L176 156M194 158L178 164", Argb.hex("5a4a44"), 2f)
    val torso = "M128 112L172 112L181 172L119 172Z"
    ArtPainter.fill(c, torso, ArtPainter.linear(128f, 112f, 181f, 172f, leather, leatherLo))
    ArtPainter.stroke(c, torso, INK, 1.8f, 0.8f)
    // A blood-red sash, and a belt of throwing knives.
    ArtPainter.stroke(c, "M128 116L178 168", blood, 7f)
    ArtPainter.fill(c, "M120 152H180V160H120Z", Argb.hex("1a1213"))
    for (i in 0..3) ArtPainter.fill(c, "M${128 + i * 13} 160L${131 + i * 13} 172L${134 + i * 13} 160Z", Argb.hex("b8bcbf"))
    // Low hood and a red mask over the face: only the eyes show.
    val hood = "M120 106Q111 64 151 46Q192 66 181 106L168 122H132Z"
    ArtPainter.fill(c, hood, ArtPainter.linear(120f, 46f, 181f, 122f, Argb.hex("2a2022"), Argb.hex("0a0707")))
    ArtPainter.hatch(c, hood, Argb.hex("050303"), 0.35f, 6f)
    ArtPainter.stroke(c, hood, INK, 2f, 0.85f)
    ArtPainter.fill(c, "M131 90Q150 66 170 90L165 112H136Z", Argb.hex("040202"))
    ArtPainter.fill(c, "M133 96H168L164 116H137Z", Argb.hex("6e1214"))
    ArtPainter.stroke(c, "M133 96H168", INK, 1.2f)
    eyes(c, 90f, gap = 9f)
    ArtPainter.stroke(c, "M137 86L146 89M163 86L154 89", INK, 2f)
    // Twin curved daggers, blades dripping red.
    val steel = Argb.hex("c3c8cb")
    ArtPainter.fill(c, "M108 172Q86 150 90 122Q100 148 116 166Z", steel)
    ArtPainter.fill(c, "M192 170Q214 148 210 120Q200 146 184 164Z", steel)
    ArtPainter.stroke(c, "M108 172Q86 150 90 122M192 170Q214 148 210 120", blood, 1.6f, 0.9f)
    ArtPainter.circle(c, 92f, 132f, 2f, blood)
    ArtPainter.circle(c, 208f, 130f, 2f, blood)
    ArtPainter.fill(c, "M106 166L122 164L126 182L110 184Z", Argb.hex("3a2c28"))
    ArtPainter.fill(c, "M178 164L194 162L198 178L182 182Z", Argb.hex("3a2c28"))
}
