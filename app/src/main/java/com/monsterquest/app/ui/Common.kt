package com.monsterquest.app.ui

import kotlin.math.roundToInt
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.GameIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, heightDp: Int = 8) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .height(heightDp.dp)
            .clip(RoundedCornerShape(50)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

fun hpColor(fraction: Float): Color = when {
    fraction > 0.5f -> HpGreen
    fraction > 0.2f -> HpYellow
    else -> HpRed
}

@Composable
fun LabeledBar(label: String, value: Int, max: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$value / $max", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Bar(value.toFloat() / max.coerceAtLeast(1), color, Modifier.fillMaxWidth())
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
fun GameCard(modifier: Modifier = Modifier, border: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border?.let { androidx.compose.foundation.BorderStroke(2.dp, it) },
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

/** A value over a label. Give it Modifier.weight(1f) in a Row so a row of pills always fits. */
@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A button with tight padding whose text ellipsizes instead of overflowing on narrow screens. */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    val content: @Composable RowScope.() -> Unit = {
        Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding, content = content)
    } else {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding, content = content)
    }
}

/** Header strip showing level, gold and hunt tokens. */
@Composable
fun ResourceHeader(level: Int, gold: Int, tokens: Int, modifier: Modifier = Modifier, onSettings: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = if (onSettings != null) 4.dp else 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onSettings != null) {
                androidx.compose.material3.IconButton(onClick = onSettings) {
                    com.monsterquest.app.ui.art.GIcon(com.monsterquest.app.ui.art.GameIcon.GEAR, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("LV $level", fontWeight = FontWeight.Bold, color = Gold, maxLines = 1, letterSpacing = 2.sp)
        }
        IconText(GameIcon.COIN, "$gold", tint = Gold, textColor = Gold, bold = true)
        IconText(GameIcon.TARGET, "$tokens hunts", tint = Mint, textColor = Mint, bold = true)
    }
}

/**
 * A distance for people: feet up close, then yards, then miles (or meters and
 * kilometers), rounded so it doesn't flicker as GPS wobbles.
 */
fun formatDistance(meters: Double, imperial: Boolean): String {
    if (!imperial) {
        return if (meters < 1000) "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} m"
        else String.format(java.util.Locale.US, "%.1f km", meters / 1000)
    }
    val feet = meters * 3.28084
    val yards = meters * 1.09361
    return when {
        feet < 300 -> "${((feet / 10).roundToInt() * 10).coerceAtLeast(10)} ft"
        yards < 1760 -> "${(yards / 10).roundToInt() * 10} yd"
        else -> String.format(java.util.Locale.US, "%.1f mi", yards / 1760)
    }
}

fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
