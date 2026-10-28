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
        GameIcon.STAR, "What is Monster Quest?",
        listOf(
            "Monster Quest is an adventure RPG tied to the real world: you explore your own neighborhood to find, fight and tame monsters, visit shrines and raid dens.",
            "It's also your own to-do list and planner. Your tasks and calendar events are your quests, and finishing them powers your adventure: XP for your hero, gold, hunt tokens, and the Silver and Gold shrines that open when you keep up through the week.",
            "Living well pays off too. Walking levels you and your monsters up, heals them, opens loot boxes and draws monsters to you. Time away from your screen (phone-free sessions) earns loot boxes. A full night's sleep refills your Energy.",
            "So the more you get done, move and rest in real life, the stronger your hero and team grow in the game.",
            "Goals on Home: three for today and three for the week (win battles, tame a featured type, walk, finish quests...). Claim each for gold and an item; finish a whole set for a bonus. Achievements on the Hero tab track lifetime milestones with bigger rewards.",
            "New here? The First steps checklist on Home walks you through the basics (add and finish a quest, visit a shrine, win a battle, tame a monster, a phone-free session), with a reward for each step and a bonus for finishing them all. Hide it any time.",
            "The menu at the bottom: Quests, Map, Bag and Store, with the Hero button in the middle. Tap or hold it to choose your Hero or your Monsters.",
        ),
    ),
    HelpTopic(
        GameIcon.QUESTS, "Quests: get work done",
        listOf(
            "Your phone's calendar events become quests automatically. To add something with a date, add it in your calendar (the Calendar button opens it), then tap Sync if it hasn't shown up yet.",
            "Quest rewards are limited to keep things fair: a new task pays out 15 minutes after you add it (calendar quests once they start), the first 8 quests each day pay in full and the next 4 pay half, only 3 Hard quests a day pay the Hard rate, and doing the same quest twice in a day pays half. Everything still counts for your streak.",
            "Use + Task for things without a due date. Tap any quest to change its priority. The list is grouped like a planner: overdue, due within the hour, later today, tomorrow, this week and later, most important first.",
            "Anything due within the hour glows amber with a countdown and is listed in a banner at the top; overdue quests turn red.",
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
            "Long sessions pay extra. 60 minutes: your party trains (+10% of a level each). 90 minutes: no Common boxes, half the walk to open, a Scholar's Tome (3 pages for any spell you choose), +25% of a level for the party and +50% Energy. 120 minutes is the jackpot: always an Epic or Legendary box at half the walk, a 5-page Scholar's Tome (enough to learn a spell), +50% of a level for the party and full Energy.",
            "Read a Scholar's Tome on the Hero tab, under Magic: pick the spell its pages go to.",
            "After a two-hour session, a rare visitor (an Epic or rarer monster) waits beside you on the map for 15 minutes, and it's 1.5x easier to tame.",
            "Sessions of an hour or more can also give a Mood Crystal (25% at 60 minutes, 50% at 90, always at 120): use it on a monster's page to give it a new personality.",
            "Leaving for another app (and not coming back within 15 seconds) fails the session. Phone calls never count.",
            "Loot boxes open by walking: 1, 3, 5 or 10 miles depending on the box.",
        ),
    ),
    HelpTopic(
        GameIcon.WALK, "Walking: monsters come to you",
        listOf(
            "Every 100 steps gives you and your battle party 10 XP and heals 10 HP.",
            "Every 600 steps a wild monster follows your footsteps, even with the phone in your pocket. Up to 4 wait beside you on the map for 4 hours.",
            "Monsters hear you moving: while you walk, every monster nearby closes in on you much faster.",
            "Choose where steps come from in Settings: the phone's step counter, Samsung Health, Health Connect or GPS.",
        ),
    ),
    HelpTopic(
        GameIcon.HEART, "Health: walking, sleep and water",
        listOf(
            "The Health card at the top of Home shows today's steps by hour (tap a bar for its count), your water and your sleep. Tap its title to open or fold it.",
            "Eggs: two monsters of the same line at level 20+ can make an egg (on a monster's page, for gold and line candy). Walk 2 to 6 km to hatch it: it's the line's first stage, with at least 3 of its parents' best genes, maybe a parent's personality, and better shiny odds if a parent was shiny. Both parents rest for a day.",
            "Walking helps your adventure: every 100 steps gives you and your battle party 10 XP, heals 10 HP and restores 2 Energy. Steps also open loot boxes, bring monsters to follow you, and count toward the 5,000-step goal on the calendar.",
            "Sleep helps too: a full night (7 hours or more) that ended this morning refills your Energy, once a day. Sleep syncs from Health Connect (Samsung Health, Fitbit, Google Fit...) with Sync sleep, or tap Log sleep to enter it by hand. The card shows last night, its stages when your watch records them, and the week.",
            "Water is just for you: tap + for each glass and set a daily goal. It pays no rewards.",
            "Each morning, the first time you open the game, a recap shows what yesterday brought (steps, levels, monster XP, loot, HP restored and tasks done) and how you slept.",
            "Your monsters also rest on their own: everyone heals 5% of their HP each hour, and fainted monsters wake after about 2 hours.",
        ),
    ),
    HelpTopic(
        GameIcon.MAP, "The map: monsters, tracker and shrines",
        listOf(
            "The map stays centered on you and follows you as you walk. Pinch to zoom, double-tap to zoom in, or double-tap and hold, then slide down to zoom in or up to zoom out.",
            "Monsters wander all around you: some close by, more out at the edge of your range creeping toward you. Only the ones within about 650 ft (200 m) stand on the map; the rest show in the tracker.",
            "The tracker bar at the top (or the Nearby list beside the map on big screens) shows monsters farther out, with their distance (feet and miles, or meters) and the direction to walk. Tap one to see what landmark it is near and whether it is heading your way.",
            "Get within 330 ft (100 m) of a monster to battle it. The phone buzzes when one comes into range.",
            "You can zoom out to about a quarter mile around you, and the map never moves more than a mile away. The weather badge in its corner shows the real weather where you are: rain brings out Water and Electric types, heat Fire, fog Gas and Corrupt, wind Flight, storms Electric. Tap it to see the details. Rain and heat also carry into battles (Rain or Drought).",
            "Weather events: a real thunderstorm (Storm surge), heatwave, fog or snowfall surges its type and, once a day each, brings a rare visitor that waits on your map for 15 minutes. The weather badge turns gold during one.",
            "The time of day matters too: Dawn favors Flight and Earth, Day Fire and Electric, Dusk Gas and Water, Night Corrupt and Gas.",
            "Item shrines you keep visiting bond with you: Familiar at 5 visits (+1 loot), Trusted at 15 (+1 loot, 1.5x gold, recharges in 20 min), Bonded at 40 (+2 loot, 2x gold, 15 min, and more monsters gather there).",
            "Shrines are permanent: each stands at a real place and stays put, so your local routes stay the same. Lakes, rivers and the coast bring out Water types (x2.5 within about 250 m); far from any water they're rare.",
            "Monsters gather around shrines too: a fountain draws Water types, a park Earth, a cafe Fire. They stay by their shrine and never ambush. Walking makes more of them turn up everywhere.",
            "Dens (rocky caves with glowing eyes) hold one strong monster: a few levels above you with great stats, but no loot. Beat it, then you get 5 Binding casts to tame it, plus 1 for each of your monsters still standing (up to 10). Den casts don't use crystals. The den screen shows your catch rate and overall odds. Dens move every day.",
            "Every Binding cast has a 2% critical catch: the monster is tamed whatever the odds. The Tame button shows your best catch rate.",
            "Berries: feed a Sweet Berry (1.5x) or a rare Golden Berry (2.5x) before a cast to make it likelier. They're only found in the wild (item shrines and wild monsters). In battle they're on the Tame menu; at a den, feeding doesn't use a cast. One berry per cast.",
            "Some monsters are aggressive (more at night). If one reaches you it ambushes (never in your first 30 seconds on the map): the fight starts on its own and it strikes first. Turn ambushes off in Settings.",
            "Hold the phone upside down on the map for battery saver; turn it upright to return.",
            "Keep your eyes open while you walk with the map open: now and then a monster (sometimes 2 or 3 of the same kind, each weaker) leaps out in front of you. The phone gives a long buzz and the fight starts. Turn this off with Ambushes in Settings.",
            "Lures (one free each day) draw extra, rarer monsters to you for 30 minutes.",
        ),
    ),
    HelpTopic(
        GameIcon.ITEM_SHRINE, "Collecting items",
        listOf(
            "Blue crystal beacons are item shrines at real places (cafes, shops, parks, art, fountains) plus Waystones in between. Tap one to see the real place and street, e.g. Supply Cache: Barnes & Noble on Wards Road.",
            "Tap Walk here on any shrine, monster or villain for walking directions in your map app.",
            "Walk within 60 m and tap one to collect gold, potions, Binding Crystals, berries, spell pages, weapons, armor and lures. A pop-up shows what you got.",
            "A shrine recharges for 30 minutes (it turns purple). Your bag has limited space; it grows every 5 levels, and the Store sells expansions.",
        ),
    ),
    HelpTopic(
        GameIcon.FIGHT, "Battles",
        listOf(
            "Fights are turn-based in Speed order. The turn order strip shows who acts next; the fighter whose turn it is pulses gold.",
            "Your side matches theirs: one wild monster is your hero alone, a pack of 3 is your hero and 2 monsters. The rest of your party waits on the bench.",
            "Tap Swap to send a monster in from the bench. Your hero can step back too, and a monster fights in their place; if it falls, your hero steps back in.",
            "Dens are the exception: you bring your hero, all 5 party monsters and the 2 swap slots. On the Monsters tab, long-press a monster and drag it onto a party or swap slot.",
            "Each monster has 4 moves. Spells and typed moves throw bolts of their type; swords and Normal moves slash.",
            "Hold a move button to see its type, power, accuracy and side effects.",
            "Statuses: poison takes 8% of max HP a turn, a burn 6% and weakens physical attacks to 75%, a shock halves Speed and can stop a turn, sleep stops turns.",
            "When a monster with 4 moves learns a new one, the level-up after the battle asks right away: compare it with the 4 it knows and pick one to forget, or skip it for now (swap it in later on its page).",
            "Defend raises your shield: no damage at all until your next turn (piercing attacks get through). It can't hold twice in a row: a second Defend straight after only halves damage.",
            "Moves have uses per battle, shown on each button: Basic 12, Reliable 8, High risk 4, Support 4, Status & traps 3. Every turn a monster doesn't use a move, that move gets one use back, so sleep and shock can't be spammed. Out of everything, a monster can only Struggle (a weak hit that hurts itself). Wild monsters play by the same rules.",
            "When choosing moves on a monster's page, sort them by role (Basic, Reliable, High risk, Status & traps, Support), power, type, accuracy or the level learned.",
            "Bigger moves cost something: lower accuracy, recoil, a turn of rest, or a drop in the user's own stats. Clean, reliable attacks top out around 95 power; moves that hit every foe do 75% to each.",
            "Each type fights its own way: Fire hits hardest; Water is quick (some moves let it go first next round); Flight powers itself up; Earth builds Defense and some moves hit with Defense instead of Attack; Electric and Gas spread status and hit statused foes harder; Corrupt fights dirty and tears down stats.",
            "Scare (an attack) and Intimidate (a skill) both drop the foe's Attack to 67% for the battle. They don't stack.",
            "Seven types, each strong against two and weak to two: Water > Fire & Gas, Fire > Earth & Electric, Corrupt > Fire & Flight, Gas > Corrupt & Electric, Earth > Water & Corrupt, Flight > Earth & Gas, Electric > Water & Flight. Super-effective hits do 1.5x; the Type chart on the Monsters tab shows it all.",
            "Dual types: every starter's final form and some of the strongest final evolutions have two types (like Thunderbird, Electric / Flight). Both count: two weaknesses stack to 2.25x, and they get the same-type bonus and learn moves for both types.",
            "Monsters grow stronger the more fighters you bring, and pay more. A one-on-one wild fight pays x1.25; in a den, a full team of 6 faces a much tougher monster for up to x4.",
            "Packs: nearby monsters may team up (2, 3 or 4 of them), and you bring as many fighters. Tap a pack member to target it. Packs are tougher and pay up to 2.1x more. Not every monster joins in.",
            "Tame more than one: taming a pack member doesn't end the fight. Each weaker one (lower level than the one you tamed) may panic and run off (50%); the stronger ones fly into a rage, +1 to every stat, stacking with every tame. Everyone you tame is yours, even if you flee or fall afterwards.",
            "Everyone rests: each hour the hero and every monster heal 5% of their max HP. Walking heals too (10 HP per 100 steps).",
            "Knocked-out monsters rest back to 10% HP and wake up (about 2 hours), or use a Restore Potion to bring one back instantly. Losing a battle costs 10% of your gold.",
        ),
    ),
    HelpTopic(
        GameIcon.ORB, "Taming and candy",
        listOf(
            "Taming uses the Binding Tome, a capture spell that costs no Energy. Get a monster below 50% HP, press Tame, pick a Binding Crystal, then trace the glyph that appears in one stroke. Its shape follows the monster's type; rarer monsters have longer glyphs and less time.",
            "Your trace is graded on speed and accuracy: Faint (0.8x odds), Good (1x), Strong (1.3x) or Perfect (1.6x). The odds for each grade show before you trace. Better crystals tame more easily and give more time to trace. Sleep, shock, poison and burn make it easier too. Prefer tapping? Turn on Simple casting in Settings (always a Good trace).",
            "Taming gives 3 candy for that monster's line and hero XP (more than beating it); beating one gives 1 candy (3 for bosses and den monsters); releasing one gives more. Spend candy on a monster's page to level it up.",
            "Monsters don't evolve on their own. Once one reaches its evolution level, an Evolve button appears on its page: it costs 10 candy of its line for the first evolution, 25 for the second and 50 for the third.",
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
            "Bosses are built for a team of 4: they get easier with fewer fighters, and tougher with 5 or 6 (paying up to 1.45x more).",
        ),
    ),
    HelpTopic(
        GameIcon.SKULL, "Thugs and villains",
        listOf(
            "Dark banners on the map are thug crews and villains, changing daily. Their monsters carry the shadow curse and can't be tamed.",
            "Beat the whole team in a row for gold, loot and parts. Then use a Restore Potion to try to free one: 75% of the time it joins you.",
            "Battling with a cursed monster slowly lifts the curse; then you choose one stat to make perfect.",
            "Thugs also jump you while you walk, bringing one monster for each of yours and fighting alongside them: Brutes hit hard, Hexers cast dark magic and Cutthroats are quick and dodge. Their monsters can't be tamed.",
        ),
    ),
    HelpTopic(
        GameIcon.HERO, "Your hero",
        listOf(
            "Each level gives 3 attribute points. At level 5 choose Warrior, Mage, Rogue or stay an Adventurer; it comes with a Rare weapon.",
            "Hero magic copies monster magic: simpler, and 75% as strong at first, reaching full strength by level 25. One bolt per type, Mud Shot (weak, but each hit lowers the foe's accuracy 10%, stacking), Mend (heal), Ward (a shield on one ally or yourself) and Fortify (Defense up).",
            "Every level gives a small box of items that gets better as you level. Better Binding Crystals unlock in the Store at levels 10 (Resonant), 25 (Radiant), 50 (Prismatic) and 75 (Perfect: always tames with no trace, 2,000 gold; you also get one at level 100).",
            "Learn spells with 5 spell-book pages, then rank each up to 5 on the Hero tab with more pages and gold.",
            "Energy doesn't refill after a battle. It comes back slowly over time (all of it in 6 hours), faster by walking (2 for every 100 steps), at once with a Mana Draught, or with a full night's sleep (7 hours or more, synced or logged on the Health card, once a day).",
            "Weapons (sword, broadsword, dagger, staff, wand) and armor (plate, leather, robe) give a +20% bonus to their matching class.",
            "A weapon of your class also adds +2/4/6/8 to all stats (by rarity). One outside your class takes that much off until you've fought 10 battles with that particular weapon.",
            "Gear comes in wood or leather, bronze, iron, gold and diamond. Better materials get common at level milestones: bronze from 10, iron from 30 (the usual find at 40), gold from 50 (usual at 65). Diamond is always rare.",
            "At level 15 pick a subclass: three per class, each with its own perks. Switching costs gold.",
            "Skills open at levels 30, 45 and 60 (Intimidate, Hover, Kleptomaniac and more) and rank up every 10 levels; from rank 3 some cover your whole team. Monsters get one skill of their own at level 30.",
            "Each monster's skill is rolled when it's born from a few that suit its kind: winged and flying monsters usually Levitate, sharks get Barbed Hide, rams Iron Skull, spiders Snare Aura, trees Sun Chaser... Its page shows its skill and what others of its kind may have.",
            "The Store buys items at half price and gear by rarity and level. Smelt a weapon for 50 gold to get its parts back.",
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
                "An adventure RPG powered by your real life: get things done, get outside and rest well to grow stronger.",
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
