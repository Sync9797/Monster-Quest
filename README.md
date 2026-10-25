# ⚔️ Monster Quest

An Android RPG to-do list: Final Fantasy-style turn-based battles meet
Pokémon GO-style monster taming and exploration. Your real-life tasks power
your hero.

## How to play

- **Quests:** add to-dos, or sync your phone's calendar so events become
  quests. Completing one gives **XP, gold and a hunt token**.
- **Hero:** each level gives 3 points to spend on **Attack, Energy, Magic,
  Defense and Speed**. New spells unlock as you level.
- **Storage upgrades:** every 5 levels you can choose **+5 bag slots** or
  **+3 monster storage**.
- **Battles:** turn-based, ordered by Speed. You fight alongside up to **3 party
  monsters** and can use up to **3 items per battle**. Elements have strengths
  and weaknesses (🔥 > 🌿 > 💧 > 🔥, ...).
- **Taming:** get a monster below 50% HP and throw an orb. Lower HP gives
  better odds. Monster storage is limited.
- **Losing** costs 10% of your gold.
- **Monsters** level up from battles (or 🍖 treats) and are each unique:
  - A random **personality** shifts their stats, e.g. a *Ferocious* wolf has
    high Attack and low Defense, while a *Stalwart* one has high Defense and
    low Speed.
  - Hidden **potential** (IVs) adds further variation.
- **Map (GPS):**
  - Real places near you (parks, landmarks, museums, places of worship,
    libraries) become **shrines**. Visit one within 60 m for loot.
  - 👹 **Boss shrines** hold legendary guardians that are 5 levels above you,
    have double HP and act twice per round. You must **defeat a guardian once**
    before you can try to tame it on a later visit.
  - Wild monsters roam the map and reshuffle every 15 minutes.
  - **Hunt tokens** let you battle from anywhere.

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
