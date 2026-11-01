# Monster skill (trait) ideas

Monster skills are our own. Each line of monsters rolls its skill from a weighted pool
(`MonsterTraits.pools` in `core/.../Classes.kt`), picked to suit its type and
look. Lines with Flight roll Airborne about 60% of the time.

When adding new monsters, pick 2–4 skills that fit how the creature looks and
its type, and add more from the backlog below when a new monster suits one.

## Built

| Skill | Effect (rank 1 → 5) |
|---|---|
| Menace | Foes' Attack drops to 67% at the start (more stages at higher ranks) |
| Airborne | Earth moves can't touch it |
| Crackle Skin / Ember Hide / Venom Barbs | 11–23% to shock / burn / poison an attacker |
| Wakeful | Wakes sooner; can't sleep at rank 5 |
| Unflappable | Stats can't be lowered; can't be scared |
| Thick Hide | 7–15% less damage |
| Quick Step | 7–19% dodge |
| Regeneration | Heals 3–7% HP a turn |
| Second Wind | Once, heals 20–40% HP below a quarter HP |
| Pack Leader | Team +2–10% stats |
| Last Stand | Own-type moves +35–55% at ⅓ HP or less |
| Rain Runner | Speed ×1.6–2.0 in Rain |
| Sun Chaser | Speed ×1.6–2.0 in Drought |
| Iron Skull | Recoil −20–100% |
| Overbearing | 20–100% chance a foe's move costs an extra use |
| Momentum | 20–100% chance each round to gain +1 Speed |
| Grit | +35–55% Attack while statused |
| Keen Sight | +8–24% accuracy; can't be blinded from rank 3 |
| Unshaken | Never flinches; can't be thrown off balance from rank 3 |
| Stalwart | Once, survives a knockout blow with 1 HP (at full HP; 80%+ HP by rank 5) |
| Lightning Eater | Electric hits heal 13–25% HP instead |
| Spring Soak | Water hits heal 13–25% HP instead |
| Kindling | Fire hits heal 13–25% HP and power up its Fire moves +35–55% |
| Barbed Hide | Attackers lose 4–8% of their max HP |
| Scavenger | 10–30% chance to find an item after a win |
| Snare Aura | Pack members 20–100% less likely to run off when one is tamed |
| Plated | Can't be critically hit; 2–10% less from super-effective hits |
| Fortune | Side-effect chances ×1.2–2.0 |
| Spore Coat | 11–23% to put an attacker to sleep, poison or shock it |
| Hard Scales | +35–55% Defense while statused |
| Rain Drinker | Heals 3–5% HP a turn in Rain |
| Insulated | 26–50% less Fire and Electric damage |
| Stormcaller | Brings Rain to every battle (legendary signature: Abyss Kraken, Thunder Titan) |
| Sunbringer | Brings a Drought to every battle (legendary signature: Magma Behemoth, Worldroot Colossus) |

## Backlog (ready when new monsters fit them)

### Battle, easy
- **Reckless**: +50% Attack, −20% accuracy.
- **Dust Coat**: immune to foes' side effects.
- **Grounded / Purified / Fireproof / Warm Blood**: can't be shocked / poisoned / burned / frostbitten.
- **Self-Mend**: status clears when swapped out.
- **Steadfast**: overlaps Unflappable; only if a line needs it under another name.

### Battle, needs a small new rule
- **Still Air**: cancels weather while out.
- **Mirror Bond**: a status inflicted on it passes back to the attacker.
- **Toxic Blood**: drain moves hurt the drainer instead.
- **Twin Spark**: +50% when an ally also has Twin Spark.
- **Sluggard**: acts every other turn; a downside for very strong monsters.
- **Brute Force**: Attack ×1.3.

### On the map
- **Slippery**: fleeing always works.
- **Forecast**: one special monster changes type with the live weather.
