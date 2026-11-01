package com.monsterquest.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.monsterquest.core.DungeonTheme
import kotlin.random.Random

/** Where a battle or a dungeon room takes place, for its painted background. */
enum class Scene(val top: Color, val bottom: Color) {
    CAVE(Color(0xFF2B2018), Color(0xFF120D0A)),
    CRYPT(Color(0xFF231A30), Color(0xFF0D0A12)),
    FORGE(Color(0xFF4A1A0E), Color(0xFF160806)),
    GROTTO(Color(0xFF0F3440), Color(0xFF061418)),
    SPIRE(Color(0xFF1A2440), Color(0xFF080C18)),
    ROOST(Color(0xFF8FB8D8), Color(0xFF4F6E86)),
    MIRE(Color(0xFF26331F), Color(0xFF0E140B)),
    DAY(Color(0xFF8CC4E8), Color(0xFF5E9A5A)),
    DUSK(Color(0xFFE08A5A), Color(0xFF3A3050)),
    NIGHT(Color(0xFF141C38), Color(0xFF0A0E1C)),
    ;

    companion object {
        fun of(theme: DungeonTheme): Scene = when (theme) {
            DungeonTheme.CAVE -> CAVE
            DungeonTheme.CRYPT -> CRYPT
            DungeonTheme.FORGE -> FORGE
            DungeonTheme.GROTTO -> GROTTO
            DungeonTheme.SPIRE -> SPIRE
            DungeonTheme.ROOST -> ROOST
            DungeonTheme.MIRE -> MIRE
        }

        /** The outdoors at this hour: day, dusk or night. */
        fun outdoors(hour: Int): Scene = when (hour) {
            in 7..16 -> DAY
            in 17..19, 5, 6 -> DUSK
            else -> NIGHT
        }
    }
}

/**
 * A painted backdrop for [scene]: a sky or rock gradient with a few shapes
 * that set the place (stalactites, tombstones, forge fires, water, storm
 * spires, clouds, swamp trees, hills). Drawn behind the screen's content.
 */
@Composable
fun Backdrop(scene: Scene, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(scene.top, scene.bottom)))
        val r = Random(scene.ordinal * 97 + 13)
        when (scene) {
            Scene.CAVE, Scene.GROTTO -> {
                stalactites(r, if (scene == Scene.CAVE) Color(0xFF3E2E22) else Color(0xFF1C4A55))
                ground(Color(0xFF1A130E).takeIf { scene == Scene.CAVE } ?: Color(0xFF0B2830), r)
                if (scene == Scene.GROTTO) water(Color(0xFF3FA3B8))
            }
            Scene.CRYPT -> {
                arches(Color(0xFF2F2540))
                ground(Color(0xFF14101A), r)
                repeat(5) {
                    val x = size.width * (0.08f + it * 0.2f + r.nextFloat() * 0.05f)
                    val w = size.width * 0.07f
                    val h = size.height * (0.08f + r.nextFloat() * 0.05f)
                    val y = size.height * 0.86f
                    drawRoundRect(Color(0xFF3A3248), Offset(x, y - h), Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2))
                }
            }
            Scene.FORGE -> {
                stalactites(r, Color(0xFF3A1A12))
                ground(Color(0xFF1C0A06), r)
                repeat(6) {
                    val x = size.width * r.nextFloat()
                    glow(Offset(x, size.height * (0.82f + r.nextFloat() * 0.1f)), size.width * 0.12f, Color(0xFFFF7A3A))
                }
            }
            Scene.SPIRE -> {
                repeat(4) {
                    val x = size.width * (0.1f + it * 0.27f)
                    val w = size.width * 0.06f
                    drawRect(Color(0xFF232E4C), Offset(x, size.height * (0.35f + r.nextFloat() * 0.2f)), Size(w, size.height))
                }
                bolt(r)
                ground(Color(0xFF0A0F1E), r)
            }
            Scene.ROOST -> {
                clouds(r, Color.White.copy(alpha = 0.5f))
                repeat(4) {
                    val x = size.width * (0.05f + it * 0.28f)
                    drawRect(Color(0xFFB8C4CC), Offset(x, size.height * 0.62f), Size(size.width * 0.05f, size.height * 0.3f))
                }
                ground(Color(0xFF6E7F68), r)
            }
            Scene.MIRE -> {
                repeat(5) {
                    val x = size.width * (0.05f + it * 0.22f)
                    drawLine(Color(0xFF1B2416), Offset(x, size.height), Offset(x + 12f, size.height * 0.45f), strokeWidth = 14f)
                    drawLine(Color(0xFF1B2416), Offset(x + 10f, size.height * 0.55f), Offset(x + 50f, size.height * 0.48f), strokeWidth = 7f)
                }
                fog(r, Color(0xFF9DB58A))
                ground(Color(0xFF151D10), r)
            }
            Scene.DAY, Scene.DUSK, Scene.NIGHT -> {
                if (scene == Scene.NIGHT) {
                    repeat(40) { drawCircle(Color.White.copy(alpha = 0.6f), 1.5f + r.nextFloat() * 1.5f, Offset(size.width * r.nextFloat(), size.height * 0.6f * r.nextFloat())) }
                    drawCircle(Color(0xFFF2EBC8), size.width * 0.06f, Offset(size.width * 0.8f, size.height * 0.15f))
                } else {
                    glow(Offset(size.width * 0.8f, size.height * (if (scene == Scene.DAY) 0.15f else 0.55f)), size.width * 0.25f, Color(0xFFFFE9A8))
                    clouds(r, Color.White.copy(alpha = if (scene == Scene.DAY) 0.6f else 0.3f))
                }
                hills(scene)
            }
        }
    }
}

private fun DrawScope.ground(color: Color, r: Random) {
    val p = Path().apply {
        moveTo(0f, size.height * 0.85f)
        var x = 0f
        while (x < size.width) {
            x += size.width * 0.1f
            lineTo(x, size.height * (0.82f + r.nextFloat() * 0.05f))
        }
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }
    drawPath(p, color)
}

private fun DrawScope.stalactites(r: Random, color: Color) {
    var x = 0f
    while (x < size.width) {
        val w = size.width * (0.05f + r.nextFloat() * 0.06f)
        val h = size.height * (0.06f + r.nextFloat() * 0.16f)
        drawPath(Path().apply { moveTo(x, 0f); lineTo(x + w, 0f); lineTo(x + w / 2, h); close() }, color)
        x += w * 0.9f
    }
}

private fun DrawScope.arches(color: Color) {
    repeat(3) {
        val w = size.width / 3
        val left = it * w
        drawArc(color, 180f, 180f, false, Offset(left + w * 0.1f, size.height * 0.25f), Size(w * 0.8f, size.height * 0.5f), style = Stroke(width = w * 0.08f))
        drawRect(color, Offset(left + w * 0.06f, size.height * 0.5f), Size(w * 0.08f, size.height * 0.4f))
        drawRect(color, Offset(left + w * 0.86f, size.height * 0.5f), Size(w * 0.08f, size.height * 0.4f))
    }
}

private fun DrawScope.water(color: Color) {
    repeat(4) { i ->
        val y = size.height * (0.88f + i * 0.03f)
        val p = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x < size.width) { quadraticBezierTo(x + 20f, y - 8f, x + 40f, y); x += 40f }
        }
        drawPath(p, color.copy(alpha = 0.35f - i * 0.06f), style = Stroke(width = 3f))
    }
}

private fun DrawScope.glow(c: Offset, radius: Float, color: Color) =
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = c, radius = radius), radius, c)

private fun DrawScope.bolt(r: Random) {
    val x0 = size.width * (0.3f + r.nextFloat() * 0.4f)
    val p = Path().apply {
        moveTo(x0, 0f)
        lineTo(x0 - 30f, size.height * 0.15f); lineTo(x0 + 10f, size.height * 0.17f); lineTo(x0 - 25f, size.height * 0.35f)
    }
    drawPath(p, Color(0xFFBFD8FF).copy(alpha = 0.7f), style = Stroke(width = 5f))
}

private fun DrawScope.clouds(r: Random, color: Color) {
    repeat(4) {
        val c = Offset(size.width * r.nextFloat(), size.height * (0.1f + r.nextFloat() * 0.25f))
        val s = size.width * (0.06f + r.nextFloat() * 0.05f)
        drawCircle(color, s, c)
        drawCircle(color, s * 0.8f, c + Offset(s * 0.9f, s * 0.15f))
        drawCircle(color, s * 0.7f, c - Offset(s * 0.85f, -s * 0.2f))
    }
}

private fun DrawScope.fog(r: Random, color: Color) {
    repeat(5) {
        drawOval(color.copy(alpha = 0.12f), Offset(size.width * (r.nextFloat() - 0.2f), size.height * (0.6f + r.nextFloat() * 0.25f)), Size(size.width * 0.6f, size.height * 0.08f))
    }
}

private fun DrawScope.hills(scene: Scene) {
    val far = when (scene) { Scene.DAY -> Color(0xFF7FB27A); Scene.DUSK -> Color(0xFF5A4A66); else -> Color(0xFF1C2A3C) }
    val near = when (scene) { Scene.DAY -> Color(0xFF5E9A5A); Scene.DUSK -> Color(0xFF3A3050); else -> Color(0xFF111A28) }
    drawOval(far, Offset(-size.width * 0.2f, size.height * 0.62f), Size(size.width * 0.9f, size.height * 0.5f))
    drawOval(far, Offset(size.width * 0.4f, size.height * 0.66f), Size(size.width * 0.9f, size.height * 0.5f))
    drawOval(near, Offset(-size.width * 0.1f, size.height * 0.78f), Size(size.width * 1.2f, size.height * 0.5f))
}
