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
