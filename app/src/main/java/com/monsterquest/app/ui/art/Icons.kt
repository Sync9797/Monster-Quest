package com.monsterquest.app.ui.art

import android.graphics.Canvas

/**
 * Small 24x24 icons drawn from path data. FILL and LINE parts use the tint;
 * CUT parts are drawn in the background color to carve detail out of fills.
 */
enum class GameIcon(internal vararg val parts: IconPart) {
    // Navigation
    QUESTS(line("M6 3h11v15a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-1h11"), line("M9 8h5M9 12h5")),
    HERO(line("M12 3C8 6 6 9 6 14l1.5 6h9L18 14c0-5-2-8-6-11z"), line("M9.5 13.5q2.5-3 5 0")),
    MONSTERS(
        fill("M7.5 17a4.5 3.8 0 1 0 9 0a4.5 3.8 0 1 0 -9 0"),
        fill("M3.5 11a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"), fill("M7.5 6.5a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"),
        fill("M12.5 6.5a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"), fill("M16.5 11a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"),
    ),
    MAP(line("M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z"), line("M9 4v14M15 6v14")),
    BAG(line("M5 9h14l-1 11H6z"), line("M9 9V7a3 3 0 0 1 6 0v2"), line("M9 13h6")),

    // Resources & actions
    COIN(fill("M12 3a9 9 0 1 0 .01 0z"), cut("M12 7.5l3.2 4.5L12 16.5 8.8 12z")),
    TARGET(line("M12 4a8 8 0 1 0 .01 0z"), line("M12 8.5a3.5 3.5 0 1 0 .01 0z"), fill("M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0")),
    PLUS(line("M12 5v14M5 12h14")),
    CHECK(line("M5 12.5l4.5 4.5L19 7")),
    CLOSE(line("M6 6l12 12M18 6L6 18")),
    EDIT(fill("M4 20l1-4.5L16 4.5l3.5 3.5L8.5 19z")),
    TRASH(line("M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13")),
    CALENDAR(line("M4 5h16v15H4z"), line("M4 9.5h16M8 3v4M16 3v4"), fill("M7 12h3v3H7z")),
    PHONE_OFF(line("M8 2.5h8a1 1 0 0 1 1 1v17a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1v-17a1 1 0 0 1 1-1z"), line("M3 3l18 18")),
    COMPASS(line("M12 3a9 9 0 1 0 .01 0z"), fill("M15.5 8.5l-2 5.5-5.5 2 2-5.5z")),
    WALK(fill("M12.5 2.5a2 2 0 1 0 .01 0z"), line("M12 8l-2.5 5 3 2.5V21M12 8l4 4M9.5 13L6.5 17")),
    STAR(fill("M12 2.5l2.8 6.2 6.7.6-5.1 4.4 1.6 6.6L12 16.8l-6 3.5 1.6-6.6-5.1-4.4 6.7-.6z")),
    SHIELD(fill("M12 2l8 3v6c0 5-3.5 9-8 11-4.5-2-8-6-8-11V5z")),
    SPARK(fill("M12 2l2 8 8 2-8 2-2 8-2-8-8-2 8-2z")),
    FIGHT(line("M4 4l10 10M20 4L10 14M5 19l3-3M19 19l-3-3M7 14l3 3M17 14l-3 3")),
    RUN(line("M4 12h14M13 6l6 6-6 6")),
    PIN(fill("M12 22s7-7 7-12a7 7 0 0 0-14 0c0 5 7 12 7 12z"), cut("M12 7.5a2.5 2.5 0 1 0 .01 0z")),
    HAMMER(fill("M13 3l8 6-3 3-8-6z"), line("M11 10L4 20")),
    LOCK(fill("M6 11h12v10H6z"), line("M8.5 11V8a3.5 3.5 0 0 1 7 0v3")),
    HOURGLASS(line("M6 3h12M6 21h12"), line("M7.5 3c0 6 9 6 9 9s-9 3-9 9M16.5 3c0 6-9 6-9 9s9 3 9 9")),
    SLEEP(line("M4 6h6l-6 7h6M13 11h6l-6 7h6")),
    TROPHY(fill("M7 3h10v5a5 5 0 0 1-10 0z"), line("M7 5H4a3 3 0 0 0 3 4M17 5h3a3 3 0 0 1-3 4M12 13v4M8 21h8M9 17h6")),
    SKULL(fill("M12 3c-5 0-8 3-8 7 0 3 2 5 4 5.5V20h8v-4.5c2-.5 4-2.5 4-5.5 0-4-3-7-8-7z"), cut("M8.5 10a1.8 1.8 0 1 0 .01 0zM15.5 10a1.8 1.8 0 1 0 .01 0z")),
    MOVE(fill("M4 12l7-7v4h9v6h-9v4z")),

    // Types
    FLAME(fill("M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 3-6 0 2 1 3 2 3 0-3-1-6 1-9z")),
    BOLT(fill("M13 2L4 14h6l-1 8 9-12h-6z")),
    WING(fill("M2 15C8 15 14 10 17 3c2.5 5 1.5 11-3 14.5-4 3-9 2-12-2.5z"), cut("M6 15.5c3 0 6-2 8-5")),
    DROP(fill("M12 2c4 6 7 9 7 13a7 7 0 0 1-14 0c0-4 3-7 7-13z")),
    GAS(fill("M7 19a4.5 4.5 0 0 1-.6-9 5.5 5.5 0 0 1 10.6-1A4.5 4.5 0 0 1 17 19z"), cut("M8 15c2-2 5-2 8 0")),
    MOON(fill("M15 3a9 9 0 1 0 6 15A7 7 0 0 1 15 3z")),
    RING(line("M12 5a7 7 0 1 0 .01 0z")),
    RAIN(fill("M7 13a4 4 0 0 1-.5-8 5 5 0 0 1 9.5-1A4 4 0 0 1 17 13z"), line("M8 16l-1 3M12 16l-1 3M16 16l-1 3")),
    SUN(fill("M12 7.5a4.5 4.5 0 1 0 .01 0z"), line("M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8")),

    // Items
    POTION(fill("M10 2h4v4.5l4.5 6.5v5a4 4 0 0 1-4 4h-5a4 4 0 0 1-4-4v-5L10 6.5z"), cut("M7 14h10")),
    HI_POTION(fill("M10 2h4v6l3 3v9a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-9l3-3z"), cut("M8.5 14h7")),
    CRYSTAL(fill("M12 2l6 7-6 13-6-13z"), cut("M6 9h12M12 2v20")),
    FEATHER(fill("M20 3C12 4 6 10 5 19l-1 2 2-1c9-1 14-7 14-17z"), cut("M6 18l9-9")),
    RESTORE(fill("M12 21l-8.2-8.2a5 5 0 0 1 8.2-6 5 5 0 0 1 8.2 6z"), cut("M12 9v7M8.5 12.5h7")),
    ORB(fill("M12 3a9 9 0 1 0 .01 0z"), cut("M3 12h6.5M14.5 12H21"), cut("M12 9.5a2.5 2.5 0 1 0 .01 0z")),
    TREAT(fill("M15 3a6 6 0 0 1 0 12h-1.5l-4 4a2 2 0 1 1-2.5-2.5l4-4V11A6 6 0 0 1 15 3z")),
    CHEST(fill("M3 11h18v10H3z"), fill("M3.5 10Q12 2 20.5 10z"), cut("M3 13.5h18"), cut("M10.5 12h3v4h-3z")),
    PARTS(fill("M12 3l2 2.5 3-.5.5 3L20 10l-1.5 2.5L20 15l-2.5 2-.5 3-3-.5L12 21l-2-2.5-3 .5-.5-3L4 15l1.5-2.5L4 10l2.5-2 .5-3 3 .5z"), cut("M12 9a3 3 0 1 0 .01 0z")),
    PAGE(fill("M6 2h9l4 4v16H6z"), cut("M9 10h7M9 13.5h7M9 17h5")),

    // Weapons
    SWORD(fill("M20 2.5l-9.5 9.5 1.5 1.5L21.5 4V2.5z"), fill("M6.5 12.5l5 5-1.2 1.2-5-5z"), fill("M7 16l1.8 1.8-3.6 3.6-1.8-1.8z")),
    STAFF(line("M5 21L16 8"), fill("M18 3a3.2 3.2 0 1 0 .01 0z")),
    SPEAR(line("M6 18L17 7"), fill("M21 3l-2 6-4-4z")),
    BOW(line("M6 3c9 3 9 15 0 18"), line("M6 3v18"), line("M6 12h13M16 9l3 3-3 3")),
    TRIDENT(line("M12 22V7"), line("M7 3v4a5 5 0 0 0 10 0V3M12 2v5")),
    DAGGER(fill("M15 3l3 3-7.5 7.5-3-3z"), line("M8.5 15.5L4 20")),
    SCYTHE(line("M7 21L15 5"), fill("M15 5c4.5 0 7.5 2.5 7.5 6.5C19.5 8.5 16.5 8 13.5 8.5z")),

    // Shrines
    ITEM_SHRINE(fill("M12 2l8 8-8 12-8-12z"), cut("M4 10h16M8 10l4 12 4-12")),
    SILVER_SHRINE(fill("M8 22V9l4-7 4 7v13z"), cut("M10 13h4M10 17h4")),
    GOLD_SHRINE(fill("M4 22V12l4-3V5l4-3 4 3v4l4 3v10z"), cut("M10.5 22v-5h3v5M12 7.5a1 1 0 1 0 .01 0z")),
}

internal enum class PartKind { FILL, LINE, CUT }

class IconPart internal constructor(internal val d: String, internal val kind: PartKind)

private fun fill(d: String) = IconPart(d, PartKind.FILL)
private fun line(d: String) = IconPart(d, PartKind.LINE)
private fun cut(d: String) = IconPart(d, PartKind.CUT)

/** Draws [icon] in [tint] into a [w]x[h] area; CUT parts use [cutColor]. */
internal fun drawIcon(c: Canvas, w: Float, h: Float, icon: GameIcon, tint: Int, cutColor: Int) {
    c.save()
    ArtPainter.fit(c, w, h, 24f, 24f)
    for (p in icon.parts) {
        when (p.kind) {
            PartKind.FILL -> ArtPainter.fill(c, p.d, tint)
            PartKind.LINE -> ArtPainter.stroke(c, p.d, tint, 2f)
            PartKind.CUT -> ArtPainter.stroke(c, p.d, cutColor, 1.6f)
        }
    }
    c.restore()
}
