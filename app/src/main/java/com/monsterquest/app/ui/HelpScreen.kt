package com.monsterquest.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText

private class HelpTopic(val icon: GameIcon, val title: String, val points: List<String>)

private val topics = listOf(
    HelpTopic(
        GameIcon.QUESTS, "Quests: get work done",
        listOf(
            "Your phone's calendar events become quests automatically. Use + To-do for anything else.",
            "Completing a quest gives XP, gold and a hunt token (spend tokens on the Hero tab to find a monster anywhere).",
            "Finishing your weekday quests opens the Silver (Tue/Thu) and Gold (weekend) shrines.",
            "The dashboard under This week shows your steps, streak and a calendar of days you got things done.",
        ),
    ),
    HelpTopic(
        GameIcon.PHONE_OFF, "Phone-free sessions: stay off your phone",
        listOf(
            "Pick 15 to 120 minutes and put your phone down. The screen stays on and dims; just don't leave the game.",
            "Finish to win a loot box. Longer sessions give rarer boxes; 60+ minutes can give a Legendary box with a boss inside.",
            "Leaving for another app (and not coming back within 15 seconds) fails the session. Phone calls never count.",
            "Loot boxes open by walking: 1, 3, 5 or 10 miles depending on the box.",
        ),
    ),
    HelpTopic(
        GameIcon.WALK, "Walking: monsters come to you",
        listOf(
            "Every 100 steps gives you and your battle party 10 XP and heals 10 HP.",
            "Every 1,000 steps a wild monster follows your footsteps, even with the phone in your pocket. Up to 3 wait beside you on the map for 4 hours.",
            "Monsters hear you moving: while you walk, every monster nearby closes in on you much faster.",
            "Choose where steps come from in Settings: the phone's step counter, Samsung Health, Health Connect or GPS.",
        ),
    ),
    HelpTopic(
        GameIcon.MAP, "The map: monsters, tracker and shrines",
        listOf(
            "New monsters appear out at the edge of your range and creep toward you over time. Only the ones within about 200 m stand on the map.",
            "The tracker bar at the top shows monsters farther out: footprints for distance (1 close, 3 far) and the direction to walk. Tap one for details.",
            "Get within 100 m of a monster to battle it. The phone buzzes when one comes into range.",
            "Some monsters are aggressive (more at night). If one reaches you it ambushes: the fight starts on its own and it strikes first. Turn ambushes off in Settings.",
            "Hold the phone upside down on the map for battery saver; turn it upright to return.",
            "Lures (one free each day) draw extra, rarer monsters to you for 30 minutes.",
        ),
    ),
    HelpTopic(
        GameIcon.ITEM_SHRINE, "Collecting items",
        listOf(
            "Blue crystal beacons are item shrines at real places (cafes, parks, art, fountains) plus Waystones in between.",
            "Walk within 60 m and tap one to collect gold, potions, orbs, spell pages, weapons, armor and lures. A pop-up shows what you got.",
            "A shrine recharges for 30 minutes (it turns purple). Your bag has limited space; upgrade it every 5 levels or for 1,000 gold.",
        ),
    ),
    HelpTopic(
        GameIcon.FIGHT, "Battles",
        listOf(
            "Fights are turn-based in Speed order. The turn order strip shows who acts next; the fighter whose turn it is pulses gold.",
            "You fight with your hero plus up to 3 party monsters. On the Monsters tab, long-press a monster and drag it onto a party slot.",
            "Each monster has 4 moves. Spells and typed moves throw bolts of their type; swords and Normal moves slash.",
            "Types work like rock-paper-scissors: Electric > Water > Fire > Corrupt > Flight > Gas > Electric. Super-effective hits do 1.5x.",
            "Wild monsters grow stronger the more fighters you bring, and pay more: x0.6 alone up to x2.75 with a full team.",
            "Packs: nearby monsters may team up (2 vs 4, 3 vs 3, 4 vs 4...). Tap a pack member to target it. Packs are tougher and pay up to 2.1x more. Not every monster joins in.",
            "Everyone rests: each hour the hero and every monster heal 5% of their max HP. Walking heals too (10 HP per 100 steps).",
            "Knocked-out monsters rest back to 10% HP and wake up (about 2 hours), or use a Restore Potion to bring one back instantly. Losing a battle costs 10% of your gold.",
        ),
    ),
    HelpTopic(
        GameIcon.ORB, "Taming and candy",
        listOf(
            "Get a monster below 50% HP, then use Tame and throw an orb. Sleep, shock, poison and burn make it easier.",
            "Taming gives 3 candy for that monster's line; releasing one gives more. Spend candy on a monster's page to level it up.",
            "Leveling never changes a monster's stat points (0 to 15 per stat). Better ones come from taming more monsters.",
            "The Journal records every monster you've seen and caught.",
            "Shinies: about 1 in 200 wild monsters (1 in 100 with a lure) has rare colors and sparkles. They're marked in battle, on the map up close, and in your Journal.",
        ),
    ),
    HelpTopic(
        GameIcon.GOLD_SHRINE, "Boss raids",
        listOf(
            "Silver shrines (cinemas, arcades, sports centres) open Tuesday and Thursday when you've done 50% of this week's quests (at least 2). Their guardians are tough evolved monsters with 3 perfect stats.",
            "Gold shrines (museums, stadiums, landmarks) open on weekends when you've done 70% of your weekday quests (at least 5). They hold legendaries.",
            "Bosses act twice per round. Defeat a Gold guardian once, then come back to tame it.",
            "Bosses are built for a full team of 4 and get easier if you bring fewer fighters.",
        ),
    ),
    HelpTopic(
        GameIcon.SKULL, "Thugs and villains",
        listOf(
            "Dark banners on the map are thug crews and villains, changing daily. Their monsters carry the shadow curse and can't be tamed.",
            "Beat the whole team in a row for gold, loot and parts. Then use a Restore Potion to try to free one: 75% of the time it joins you.",
            "Battling with a cursed monster slowly lifts the curse; then you choose one stat to make perfect.",
        ),
    ),
    HelpTopic(
        GameIcon.HERO, "Your hero",
        listOf(
            "Each level gives 3 attribute points. At level 5 choose Warrior, Mage, Rogue or stay an Adventurer; it comes with a Rare weapon.",
            "Weapons (sword, broadsword, dagger, staff, wand) and armor (plate, leather, robe) give a +20% bonus to their matching class.",
            "Adventures (Monsters tab, 5+ monsters) send a team to real places for gold and items while you're away.",
        ),
    ),
)

/** How to play, in short topics you can open one at a time. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconText(GameIcon.BOOK, "How to play", tint = Gold, textColor = Gold, size = 22.dp, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text(
                "Monster Quest rewards getting things done, staying off your phone and getting out for a walk.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            topics.forEachIndexed { i, t ->
                val expanded = open == i
                GameCard(
                    Modifier.padding(vertical = 4.dp).animateContentSize().clickable { open = if (expanded) null else i },
                    border = if (expanded) Gold else null,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GIcon(t.icon, Modifier.size(22.dp), Gold)
                        Spacer(Modifier.width(10.dp))
                        Text(t.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (expanded) "-" else "+", color = Gold, fontSize = 18.sp)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        t.points.forEach { p ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("•  ", color = Gold, fontSize = 13.sp)
                                Text(p, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
