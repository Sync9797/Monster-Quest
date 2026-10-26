# ⚔️ Monster Quest

An Android RPG to-do list: Final Fantasy-style turn-based battles meet
Pokémon GO-style monster taming and exploration. Your real-life tasks power
your hero.

## How to play

- **Quests:** add to-dos, or sync your phone's calendar so events become
  quests. Completing one gives **XP, gold and a hunt token**.
- **📵 Phone-free challenge:** pick 15–120 minutes and lock your phone.
  - Keep it locked until time's up to win a **loot box**. Longer sessions give
    better odds of rare boxes.
  - Unlocking early, or not locking within a minute, costs gold and a hunt token.
- **Loot boxes** open by walking, tracked with the phone's step counter
  (GPS if there isn't one). You can hold **6** and unlock **2 at a time**.

  | Level | Walk | Contains |
  |---|---|---|
  | 1 📦 | 1 mile | Gold and potions |
  | 2 🎁 | 3 miles | + weapon parts (5 forge a weapon) |
  | 3 💎 | 5 miles | + spell sheets (5 of a spell teach it) |
  | 4 👑 | 10 miles | + a boss fight, 20% chance of a legendary |

  Level 4 boxes only come from sessions of 60 minutes or more.
- **Hero:** each level gives 3 points to spend on **Attack, Energy, Magic,
  Defense and Speed**.
  - Equip **weapons** for bonus stats and a typed basic attack.
  - Learn spells by collecting **5 spell-book pages**.
- **Storage upgrades:** every 5 levels you can choose **+5 bag slots** or
  **+3 monster storage**.
- **Types** work like rock-paper-scissors. Each beats exactly one other:
  ⚡ Electric > 💧 Water > 🔥 Fire > 🌑 Corrupt > 🕊️ Flight > 💨 Gas > ⚡ Electric.
- **50 monsters:**
  - 6 four-stage lines (3 evolutions)
  - 6 hound breeds with three stages (2 evolutions)
  - 2 rare mythics
  - 6 legendary bosses

  Monsters evolve automatically at set levels, and longer lines end in
  stronger final forms. Each also rolls a **personality** and hidden
  **potential**, so no two are alike.
- **Monster stat pages:** tap a monster to see its stats and compare it with
  another monster. Monsters use HP, Attack, Defense and Speed, and run well
  above the hero's stats.
- **Moves:**
  - Every monster has **4 move slots**. It learns its own type's moves plus
    **Normal** moves as it levels up (no monster is Normal type).
  - Moves can inflict **sleep, shock, poison and burn**, lower stats, set
    **rain or drought**, guard, dive out of reach, or reverse turn order.
    Stat drops and weather last one battle.
  - Status conditions make monsters easier to tame.
- **Fainting:** a monster knocked out in battle stays down until healed with
  a rare **Restore Potion**. If all your monsters are down, your strongest
  recovers once a day.
- **Battles:** turn-based, ordered by Speed. You fight alongside up to **3 party
  monsters** and can use up to **3 items per battle**. Losing costs 10% of
  your gold.
- **Taming:** get a monster below 50% HP and throw an orb.
- **Map (GPS):**
  - 🎁 **Item shrines** are at cafes, restaurants, parks and libraries. They
    give weapons, spell pages, orbs and potions, and are always open.
  - 🥈 **Silver shrines** are at cinemas, arcades, bowling alleys, sports
    centres and escape rooms.
    - Open **Tuesday and Thursday** if you've done 50% of this week's quests
      (at least 2).
    - Guardians are tough evolved monsters (never legendary). They're harder
      to tame but always have at least 3 perfect stats.
  - 🥇 **Gold shrines** are at museums, stadiums, theatres, zoos and landmarks.
    - Open **Saturday and Sunday** if you completed 70% of your Monday–Friday
      quests (at least 5).
    - Legendary-class guardians. Defeat one once, then tame it on a later visit.

## Foldable and large-screen support

- **Folded / regular phone:** single column with a bottom navigation bar.
- **Unfolded (or tablet):** side navigation rail and two-pane layouts. Quests
  and monsters are shown in a grid; hero, bag and map get side-by-side panes.
- **Book posture** (half-open, vertical hinge): panes split exactly at the
  hinge, so nothing is drawn across the fold.
- **Tabletop posture** (half-open, horizontal hinge, including flip phones):
  - Battles show the enemy and battle log on the top half and your party and
    commands on the bottom half.
  - The map sits on top with the nearby list below.
- Folding or unfolding mid-battle keeps all game state.

## Install on your phone

Every push to `main` builds the app on GitHub Actions and publishes it as the
[`latest` release](../../releases/tag/latest).
1. On your phone, open the release page and download `MonsterQuest.apk`.
2. Open the file and allow installing from your browser if Android asks.

Updates install over the previous version and keep your save.

## Project layout

- `core/`: the pure-Kotlin game engine (battles, stats, leveling, rewards,
  GPS spawns). It has unit tests and builds without the Android SDK:
  `CORE_ONLY=1 ./gradlew :core:test`.
- `app/`: the Android app (Jetpack Compose UI, calendar sync, osmdroid map).

Map data © OpenStreetMap contributors.
