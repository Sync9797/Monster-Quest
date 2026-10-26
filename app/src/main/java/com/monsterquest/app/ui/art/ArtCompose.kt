package com.monsterquest.app.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.monsterquest.core.BoxRarity
import com.monsterquest.core.Element
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Item
import com.monsterquest.core.ShrineTier
import com.monsterquest.core.Species
import com.monsterquest.core.Status
import com.monsterquest.core.Weapon
import com.monsterquest.core.Weather

@Composable
private fun ArtCanvas(modifier: Modifier, draw: (android.graphics.Canvas, Float, Float) -> Unit) {
    // Clip so glows and halos never paint over neighboring UI.
    Canvas(modifier.clipToBounds()) {
        drawIntoCanvas { draw(it.nativeCanvas, size.width, size.height) }
    }
}

/** The hero. [portrait] adds the halo and sparkles. */
@Composable
fun HeroArt(cls: HeroClass, modifier: Modifier, portrait: Boolean = true) {
    ArtCanvas(modifier) { c, w, h -> drawHero(c, w, h, cls, portrait) }
}

/** A monster's artwork. [dim] grays it out (fainted). */
@Composable
fun MonsterArt(species: Species, modifier: Modifier, dim: Boolean = false) {
    ArtCanvas(modifier) { c, w, h -> drawMonster(c, w, h, species, dim) }
}

/** A small game icon. */
@Composable
fun GIcon(icon: GameIcon, modifier: Modifier = Modifier.size(18.dp), tint: Color = LocalContentColor.current) {
    val cut = MaterialTheme.colorScheme.background.toArgb()
    val t = tint.toArgb()
    ArtCanvas(modifier) { c, w, h -> drawIcon(c, w, h, icon, t, cut) }
}

/** Icon followed by text, e.g. a coin and "1234". */
@Composable
fun IconText(
    icon: GameIcon,
    text: String,
    tint: Color = LocalContentColor.current,
    textColor: Color = Color.Unspecified,
    size: Dp = 16.dp,
    fontSize: TextUnit = TextUnit.Unspecified,
    bold: Boolean = false,
    style: TextStyle = TextStyle.Default,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GIcon(icon, Modifier.size(size), tint)
        Text(
            text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (bold) FontWeight.Bold else null,
            style = style,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// What icon and color each game concept uses.
// ---------------------------------------------------------------------------

fun Element.icon(): GameIcon = when (this) {
    Element.FIRE -> GameIcon.FLAME
    Element.ELECTRIC -> GameIcon.BOLT
    Element.FLIGHT -> GameIcon.WING
    Element.WATER -> GameIcon.DROP
    Element.GAS -> GameIcon.GAS
    Element.CORRUPT -> GameIcon.MOON
    Element.NEUTRAL -> GameIcon.RING
}

fun Element.color(): Color = Color(palette(this).light)

fun Item.icon(): GameIcon = when (this) {
    Item.POTION -> GameIcon.POTION
    Item.HI_POTION -> GameIcon.HI_POTION
    Item.ETHER -> GameIcon.CRYSTAL
    Item.PHOENIX_DOWN -> GameIcon.FEATHER
    Item.RESTORE_POTION -> GameIcon.RESTORE
    Item.TAMING_ORB, Item.GREAT_ORB, Item.MASTER_ORB -> GameIcon.ORB
    Item.TREAT -> GameIcon.TREAT
}

fun Item.color(): Color = when (this) {
    Item.POTION, Item.HI_POTION -> Color(0xFF9CD2BD)
    Item.ETHER -> Color(0xFF9CBFE4)
    Item.PHOENIX_DOWN -> Color(0xFFF2A198)
    Item.RESTORE_POTION -> Color(0xFFF28B9B)
    Item.TAMING_ORB -> Color(0xFF9CBFE4)
    Item.GREAT_ORB -> Color(0xFFC3A6F0)
    Item.MASTER_ORB -> Color(0xFFEBC678)
    Item.TREAT -> Color(0xFFD9A07A)
}

fun com.monsterquest.core.WeaponType.icon(): GameIcon = when (this) {
    com.monsterquest.core.WeaponType.SWORD -> GameIcon.SWORD
    com.monsterquest.core.WeaponType.BROADSWORD -> GameIcon.BROADSWORD
    com.monsterquest.core.WeaponType.DAGGER -> GameIcon.DAGGER
    com.monsterquest.core.WeaponType.STAFF -> GameIcon.STAFF
    com.monsterquest.core.WeaponType.WAND -> GameIcon.WAND
}

fun com.monsterquest.core.GearRarity.color(): Color = when (this) {
    com.monsterquest.core.GearRarity.NORMAL -> Color(0xFFD7DDD9)
    com.monsterquest.core.GearRarity.RARE -> Color(0xFF9CBFE4)
    com.monsterquest.core.GearRarity.EPIC -> Color(0xFFC3A6F0)
    com.monsterquest.core.GearRarity.LEGENDARY -> Color(0xFFEBC678)
}

fun Weapon.icon(): GameIcon = when (this) {
    Weapon.WOODEN_SWORD, Weapon.IRON_SWORD, Weapon.FLAME_BLADE -> GameIcon.SWORD
    Weapon.APPRENTICE_STAFF, Weapon.ARCANE_STAFF -> GameIcon.STAFF
    Weapon.STORM_SPEAR -> GameIcon.SPEAR
    Weapon.WIND_BOW -> GameIcon.BOW
    Weapon.TIDE_TRIDENT -> GameIcon.TRIDENT
    Weapon.VENOM_DAGGER -> GameIcon.DAGGER
    Weapon.SHADOW_SCYTHE -> GameIcon.SCYTHE
}

fun Weapon.color(): Color = if (element == Element.NEUTRAL) Color(0xFFD7DDD9) else element.color()

fun BoxRarity.color(): Color = when (this) {
    BoxRarity.COMMON -> Color(0xFFB8C1BE)
    BoxRarity.RARE -> Color(0xFF9CBFE4)
    BoxRarity.EPIC -> Color(0xFFC3A6F0)
    BoxRarity.LEGENDARY -> Color(0xFFEBC678)
}

fun ShrineTier.icon(): GameIcon = when (this) {
    ShrineTier.ITEM -> GameIcon.ITEM_SHRINE
    ShrineTier.SILVER -> GameIcon.SILVER_SHRINE
    ShrineTier.GOLD -> GameIcon.GOLD_SHRINE
}

fun ShrineTier.color(): Color = when (this) {
    ShrineTier.ITEM -> Color(0xFF9CD2BD)
    ShrineTier.SILVER -> Color(0xFFC0C7D1)
    ShrineTier.GOLD -> Color(0xFFEBC678)
}

fun Status.icon(): GameIcon = when (this) {
    Status.SLEEP -> GameIcon.SLEEP
    Status.SHOCK -> GameIcon.BOLT
    Status.POISON -> GameIcon.SKULL
    Status.BURN -> GameIcon.FLAME
}

fun Weather.icon(): GameIcon? = when (this) {
    Weather.RAIN -> GameIcon.RAIN
    Weather.DROUGHT -> GameIcon.SUN
    Weather.CLEAR -> null
}

fun com.monsterquest.core.HeroAttribute.icon(): GameIcon = when (this) {
    com.monsterquest.core.HeroAttribute.ATTACK -> GameIcon.SWORD
    com.monsterquest.core.HeroAttribute.ENERGY -> GameIcon.BOLT
    com.monsterquest.core.HeroAttribute.MAGIC -> GameIcon.SPARK
    com.monsterquest.core.HeroAttribute.DEFENSE -> GameIcon.SHIELD
    com.monsterquest.core.HeroAttribute.SPEED -> GameIcon.WING
}

fun HeroClass.icon(): GameIcon = when (this) {
    HeroClass.NONE -> GameIcon.HERO
    HeroClass.WARRIOR -> GameIcon.SWORD
    HeroClass.MAGE -> GameIcon.STAFF
    HeroClass.ROGUE -> GameIcon.DAGGER
}

// ---------------------------------------------------------------------------
// Bitmaps for map markers (osmdroid needs Drawables).
// ---------------------------------------------------------------------------

/** A round marker: colored disc, then the artwork drawn by [draw]. */
fun markerDrawable(context: Context, sizeDp: Int, ring: Int, draw: (android.graphics.Canvas, Float, Float) -> Unit): BitmapDrawable {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    ArtPainter.circle(c, px / 2f, px / 2f, px / 2f, ring)
    ArtPainter.ringStroke(c, px / 2f, px / 2f, px / 2f - 1.5f, Argb.hex("0d171a"), 2f, 0.6f)
    val inset = px * 0.1f
    c.save()
    c.translate(inset, inset)
    draw(c, px - inset * 2, px - inset * 2)
    c.restore()
    return BitmapDrawable(context.resources, bmp)
}

fun drawMonsterOn(c: android.graphics.Canvas, w: Float, h: Float, sp: Species) = drawMonster(c, w, h, sp)

fun drawHeroOn(c: android.graphics.Canvas, w: Float, h: Float, cls: HeroClass) = drawHero(c, w, h, cls, portrait = false)

fun drawIconOn(c: android.graphics.Canvas, w: Float, h: Float, icon: GameIcon, tint: Int) =
    drawIcon(c, w, h, icon, tint, Argb.hex("0d171a"))
