package com.monsterquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.LootPopup
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.core.Reward
import com.monsterquest.core.Spells
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class RewardLook(val icon: GameIcon, val text: String, val color: Color)

private fun look(r: Reward): RewardLook = when (r) {
    is Reward.Gold -> RewardLook(GameIcon.COIN, "+${r.amount} gold", Gold)
    is Reward.Items -> RewardLook(r.item.icon(), if (r.count > 1) "${r.item.label} x${r.count}" else r.item.label, r.item.color())
    is Reward.WeaponDrop -> RewardLook(r.gear.type.icon(), "${r.gear.rarity.label} ${r.gear.name}", r.gear.rarity.color())
    is Reward.ArmorDrop -> RewardLook(r.armor.type.icon(), "${r.armor.rarity.label} ${r.armor.name}", r.armor.rarity.color())
    is Reward.Pages -> {
        val spell = Spells.byId(r.spellId)
        RewardLook(GameIcon.PAGE, (spell?.name ?: "Spell") + if (r.learned) " learned!" else " page", spell?.spellColor() ?: XpPurple)
    }
    is Reward.Parts -> RewardLook(r.type.icon(), "${r.type.label} part" + if (r.count > 1) " x${r.count}" else "", SilverColor)
    is Reward.ArmorParts -> RewardLook(r.type.icon(), "${r.type.label} scrap" + if (r.count > 1) " x${r.count}" else "", SilverColor)
    is Reward.SpellBook -> {
        val spell = Spells.byId(r.spellId)
        RewardLook(GameIcon.PAGE, "Spell book: ${spell?.name ?: "Spell"}" + if (r.rank > 1) " rank ${r.rank}" else " learned!", Gold)
    }
}

/**
 * A short pop-up of what a shrine gave you: it springs in, shows each reward
 * with its icon one after another, then fades away on its own (tap to close).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LootPopupCard(popup: LootPopup, onDone: () -> Unit) {
    val scale = remember(popup.id) { Animatable(0.6f) }
    val alpha = remember(popup.id) { Animatable(0f) }
    val shown = remember(popup.id) { Animatable(0f) }
    LaunchedEffect(popup.id) {
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        alpha.animateTo(1f, tween(150))
        // Reveal the rewards one by one.
        shown.animateTo(popup.rewards.size.toFloat(), tween(110 * popup.rewards.size))
        delay(1_800)
        alpha.animateTo(0f, tween(350))
        onDone()
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
        Card(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 24.dp)
                .scale(scale.value)
                .alpha(alpha.value)
                .clickable(onClick = onDone),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(2.dp, Gold),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(popup.title, fontWeight = FontWeight.Bold, color = Gold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("You found", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    popup.rewards.forEachIndexed { i, r ->
                        val l = look(r)
                        val a = (shown.value - i).coerceIn(0f, 1f)
                        Column(
                            Modifier.widthIn(max = 92.dp).alpha(a).scale(0.7f + 0.3f * a),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GIcon(l.icon, Modifier.size(34.dp), l.color)
                            Text(l.text, fontSize = 11.sp, color = l.color, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
