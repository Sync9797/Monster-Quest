package com.monsterquest.app.ui

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The device's fold/hinge, as reported by Jetpack WindowManager. Null on regular phones. */
data class FoldInfo(
    /** True when the fold splits the screen into two logical areas (half-open, or a physical gap). */
    val isSeparating: Boolean,
    /** True for a vertical hinge (book posture); false for horizontal (tabletop posture). */
    val isVertical: Boolean,
    /** Hinge bounds in window pixels. */
    val bounds: Rect,
)

val LocalFold = compositionLocalOf<FoldInfo?> { null }

/** Width-based layout classes, following Material's window size classes. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun widthClass(): WidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w < 600 -> WidthClass.COMPACT
        w < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}

/** True when there's room (or a hinge) for side-by-side panes. */
@Composable
fun useTwoPane(): Boolean {
    val fold = LocalFold.current
    return widthClass() != WidthClass.COMPACT || (fold != null && fold.isSeparating)
}

/**
 * Lays out two panes so that nothing is drawn across a hinge:
 * - Tabletop (horizontal hinge): [first] above the hinge, [second] below it.
 * - Book (vertical hinge): [first] left of the hinge, [second] right of it.
 * - Wide screen without a separating fold: side by side ([firstWeight] for the first pane).
 * - Narrow phone: [compact] is shown instead.
 */
@Composable
fun AdaptivePanes(
    modifier: Modifier = Modifier,
    firstWeight: Float = 0.5f,
    compact: @Composable () -> Unit,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val fold = LocalFold.current
    val separating = fold?.takeIf { it.isSeparating }
    if (separating == null && widthClass() == WidthClass.COMPACT) {
        Box(modifier.fillMaxSize()) { compact() }
    } else {
        SplitPanes(modifier, firstWeight, separating, first, second)
    }
}

@Composable
private fun SplitPanes(
    modifier: Modifier,
    firstWeight: Float,
    separating: FoldInfo?,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val minPanePx = with(density) { 120.dp.toPx() }

        val tabletopTop = separating?.takeIf { !it.isVertical }?.let { it.bounds.top - origin.y }
        val tabletopGap = separating?.bounds?.height()?.toFloat() ?: 0f
        val bookLeft = separating?.takeIf { it.isVertical }?.let { it.bounds.left - origin.x }
        val bookGap = separating?.bounds?.width()?.toFloat() ?: 0f
        val tabletopOk = tabletopTop != null && tabletopTop > minPanePx && maxH - tabletopTop - tabletopGap > minPanePx
        val bookOk = bookLeft != null && bookLeft > minPanePx && maxW - bookLeft - bookGap > minPanePx

        if (tabletopOk && tabletopTop != null) {
            // Tabletop: split at the horizontal hinge.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(with(density) { tabletopTop.toDp() }).graphicsLayer()) { first() }
                Spacer(Modifier.height(with(density) { tabletopGap.toDp() }))
                Box(Modifier.fillMaxWidth().weight(1f).graphicsLayer()) { second() }
            }
        } else if (bookOk && bookLeft != null) {
            // Book: split at the vertical hinge.
            Row(Modifier.fillMaxSize()) {
                // Each pane in its own layer: scrolling one doesn't redraw the other.
                Box(Modifier.fillMaxHeight().width(with(density) { bookLeft.toDp() }).graphicsLayer()) { first() }
                Spacer(Modifier.width(with(density) { bookGap.toDp() }))
                Box(Modifier.fillMaxHeight().weight(1f).graphicsLayer()) { second() }
            }
        } else {
            // Large screen, flat: simple side-by-side.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().weight(firstWeight).graphicsLayer()) { first() }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.fillMaxHeight().weight(1f - firstWeight).graphicsLayer()) { second() }
            }
        }
    }
}
