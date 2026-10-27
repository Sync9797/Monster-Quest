package com.monsterquest.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.core.Binding
import com.monsterquest.core.Glyph
import com.monsterquest.core.GlyphPoint
import com.monsterquest.core.TraceGrade

/** The colors of each grade, shown as the result flashes up. */
private fun TraceGrade.color(): Color = when (this) {
    TraceGrade.FAINT -> Color(0xFF9AA3A1)
    TraceGrade.GOOD -> Color(0xFF9CBFE4)
    TraceGrade.STRONG -> Color(0xFF7FD6C2)
    TraceGrade.PERFECT -> Color(0xFFEBC678)
}

/**
 * The Binding cast: the glyph glows faintly; put your finger on the bright
 * dot and trace it in one stroke. The timer starts at your first touch. Speed
 * and accuracy give the grade, which flashes up before the cast resolves.
 */
@Composable
fun TracePad(glyph: Glyph, modifier: Modifier = Modifier, onDone: (TraceGrade) -> Unit) {
    val done by rememberUpdatedState(onDone)
    val trace = remember(glyph) { mutableStateListOf<GlyphPoint>() }
    var startedAt by remember(glyph) { mutableLongStateOf(-1L) }
    var elapsed by remember(glyph) { mutableLongStateOf(0L) }
    var result by remember(glyph) { mutableStateOf<TraceGrade?>(null) }
    val gold = Gold
    val track = MaterialTheme.colorScheme.surfaceVariant

    fun finish(durationMs: Long) {
        if (result != null) return
        result = Binding.grade(glyph, trace.toList(), durationMs.coerceAtLeast(1))
    }

    // The clock runs once you start; at the limit the cast goes off with what you've traced.
    LaunchedEffect(startedAt) {
        if (startedAt < 0) return@LaunchedEffect
        val t0 = withFrameMillis { it }
        while (result == null) {
            val now = withFrameMillis { it }
            elapsed = now - t0
            if (elapsed >= glyph.timeLimitMs) finish(glyph.timeLimitMs)
        }
    }
    // Show the grade for a moment, then resolve the cast.
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        kotlinx.coroutines.delay(650)
        done(r)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Time left, once started.
        val left = if (startedAt < 0) 1f else (1f - elapsed.toFloat() / glyph.timeLimitMs).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
            Box(Modifier.fillMaxWidth(left).height(6.dp).background(if (left > 0.3f) gold else HpRed))
        }
        Spacer(Modifier.height(10.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics { contentDescription = "Binding glyph: ${glyph.name}" }
                    .pointerInput(glyph) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (result != null) return@awaitEachGesture
                            if (startedAt < 0) startedAt = down.uptimeMillis
                            val t0 = down.uptimeMillis
                            trace.clear()
                            trace += GlyphPoint(down.position.x / size.width, down.position.y / size.height)
                            var last = down.uptimeMillis
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull() ?: break
                                trace += GlyphPoint(ch.position.x / size.width, ch.position.y / size.height)
                                last = ch.uptimeMillis
                                ch.consume()
                                if (!ch.pressed) break
                            }
                            finish(last - t0)
                        }
                    },
            ) {
                fun toPx(p: GlyphPoint) = Offset(p.x * size.width, p.y * size.height)
                val w = size.minDimension
                // The glyph: a soft glow under a faint line, and the bright starting dot.
                val path = Path().apply {
                    glyph.points.forEachIndexed { i, p -> val o = toPx(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                }
                drawPath(path, gold.copy(alpha = 0.12f), style = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, gold.copy(alpha = 0.45f), style = Stroke(w * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(gold, w * 0.04f, toPx(glyph.points.first()))
                // Your trace.
                if (trace.size > 1) {
                    val t = Path().apply {
                        trace.forEachIndexed { i, p -> val o = toPx(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                    }
                    drawPath(t, (result?.color() ?: Color.White), style = Stroke(w * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            result?.let { r ->
                Text(
                    "${r.label}!",
                    color = r.color(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (startedAt < 0) "Start on the bright dot and trace the ${glyph.name} glyph in one stroke." else "Faster and closer to the lines is stronger.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The Binding cast as a full-screen overlay: what you're casting with and the
 * odds for each grade, the trace pad, and Cancel (which costs nothing).
 */
@Composable
fun BindingCast(
    title: String,
    glyph: Glyph,
    gradeOdds: Map<TraceGrade, Int>,
    onCancel: () -> Unit,
    onDone: (TraceGrade) -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GIcon(GameIcon.BOOK, Modifier.size(28.dp), Gold)
                Text(title, style = MaterialTheme.typography.titleLarge, color = Gold, textAlign = TextAlign.Center)
                if (gradeOdds.isNotEmpty()) {
                    Text(
                        gradeOdds.entries.joinToString("  •  ") { (g, pct) -> "${g.label} $pct%" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                }
                TracePad(glyph, Modifier.fillMaxWidth(), onDone)
                TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
            }
        }
    }
}

/**
 * The binding energy after a cast: a glowing core of swirling energy that
 * shakes once per [CastRoll.shakes] (a dot lights up for each), then either
 * seals with a bright flash ("Bound!") or bursts in a harmless blast of
 * energy ("Broke free!") before returning to the fight. [onFinished] runs
 * when it's done.
 */
@Composable
fun SealAnimation(
    roll: com.monsterquest.core.CastRoll,
    tint: Color,
    failNote: String = "The binding burst apart (no harm done). Back to the battle!",
    onFinished: () -> Unit,
) {
    val finished by rememberUpdatedState(onFinished)
    val wobble = remember { androidx.compose.animation.core.Animatable(0f) }
    val scale = remember { androidx.compose.animation.core.Animatable(0.6f) }
    val blast = remember { androidx.compose.animation.core.Animatable(0f) }
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    var shaken by remember { mutableStateOf(0) }
    var outcome by remember { mutableStateOf<Boolean?>(null) }
    // The energy swirls the whole time.
    val spin by androidx.compose.animation.core.rememberInfiniteTransition(label = "binding").animateFloat(
        0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "spin",
    )
    LaunchedEffect(roll) {
        scale.animateTo(1f, androidx.compose.animation.core.tween(300))
        repeat(roll.shakes) {
            wobble.animateTo(-1f, androidx.compose.animation.core.tween(110))
            wobble.animateTo(1f, androidx.compose.animation.core.tween(170))
            wobble.animateTo(0f, androidx.compose.animation.core.tween(110))
            shaken++
            kotlinx.coroutines.delay(380)
        }
        outcome = roll.success
        if (roll.success) {
            // Sealed: the energy pulls in tight and flashes.
            scale.animateTo(0.75f, androidx.compose.animation.core.tween(150))
            flash.animateTo(1f, androidx.compose.animation.core.tween(120))
            scale.animateTo(1.1f, androidx.compose.animation.core.tween(220))
            flash.animateTo(0f, androidx.compose.animation.core.tween(400))
            kotlinx.coroutines.delay(500)
        } else {
            // Broke free: the energy bursts outward. It's only light; nobody is hurt.
            blast.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            kotlinx.coroutines.delay(900)
        }
        finished()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (roll.critical) Text("Critical catch!", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Canvas(Modifier.size(220.dp).semantics { contentDescription = "Binding energy" }) {
            val c = center
            val r = size.minDimension * 0.2f
            val b = blast.value
            val core = if (b > 0f) (1f - b * 1.6f).coerceAtLeast(0f) else 1f
            // Shake: a quick side-to-side jolt.
            val dx = wobble.value * size.minDimension * 0.06f
            val o = androidx.compose.ui.geometry.Offset(c.x + dx, c.y)
            val rs = r * scale.value * core
            if (rs > 0.5f) {
                // Outer glow, bright core and a white-hot heart.
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0f)), o, rs * 2.6f), rs * 2.6f, o)
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White, tint, tint.copy(alpha = 0.6f)), o, rs), rs, o)
                // Swirling arcs of energy around the core.
                for (i in 0 until 3) {
                    val start = spin + i * 120f
                    val ring = rs * (1.35f + i * 0.22f)
                    drawArc(
                        tint.copy(alpha = 0.85f - i * 0.2f),
                        start, 110f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(o.x - ring, o.y - ring),
                        size = androidx.compose.ui.geometry.Size(ring * 2, ring * 2),
                        style = Stroke(size.minDimension * 0.018f, cap = StrokeCap.Round),
                    )
                }
                // Sparks orbiting the core.
                for (i in 0 until 6) {
                    val a = Math.toRadians((spin * 1.6f + i * 60f).toDouble())
                    val d = rs * 1.9f
                    drawCircle(Color.White.copy(alpha = 0.8f), size.minDimension * 0.012f, androidx.compose.ui.geometry.Offset(o.x + (d * kotlin.math.cos(a)).toFloat(), o.y + (d * kotlin.math.sin(a)).toFloat()))
                }
            }
            // The seal's flash.
            if (flash.value > 0f) drawCircle(Color.White.copy(alpha = 0.7f * flash.value), size.minDimension * 0.5f * flash.value, c)
            // The harmless blast: expanding rings and sparks flying out, fading as they go.
            if (b > 0f) {
                val fade = (1f - b).coerceIn(0f, 1f)
                for (k in 0 until 2) {
                    val rr = size.minDimension * (0.15f + 0.4f * b) * (1f + k * 0.25f)
                    drawCircle(tint.copy(alpha = fade * (0.8f - k * 0.3f)), rr, c, style = Stroke(size.minDimension * 0.03f * fade + 1f))
                }
                for (i in 0 until 14) {
                    val a = Math.toRadians((i * 360.0 / 14) + 9)
                    val d = size.minDimension * (0.12f + 0.42f * b)
                    val p = androidx.compose.ui.geometry.Offset(c.x + (d * kotlin.math.cos(a)).toFloat(), c.y + (d * kotlin.math.sin(a)).toFloat())
                    drawCircle(Color.White.copy(alpha = fade), size.minDimension * 0.014f, p)
                }
            }
        }
        // A dot for each shake, like a held breath.
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.size(12.dp).background(
                        if (i < shaken) tint else MaterialTheme.colorScheme.surfaceVariant,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when (outcome) {
                true -> "Bound!"
                false -> "Broke free!"
                null -> "…"
            },
            color = if (outcome == false) HpRed else Gold,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
        )
        if (outcome == false) {
            Text(
                failNote,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** [SealAnimation] full-screen over everything (battle or the den dialog); it can't be skipped. */
@Composable
fun BindingSeal(
    roll: com.monsterquest.core.CastRoll,
    tint: Color,
    failNote: String = "The binding burst apart (no harm done). Back to the battle!",
    onFinished: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
            SealAnimation(roll, tint, failNote, onFinished)
        }
    }
}
