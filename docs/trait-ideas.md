# Monster skill (trait) ideas

Monster skills are inspired by the early-generation (Gen 3) abilities, with our own
names and numbers. Each line of monsters rolls its skill from a weighted pool
(`MonsterTraits.pools` in `core/.../Classes.kt`), picked to suit its type and
look. Lines with Flight roll Levitate about 60% of the time.

When adding new monsters, pick 2–4 skills that fit how the creature looks and
its type, and add more from the backlog below when a new monster suits one.

## Built

| Skill | Inspired by | Effect (rank 1 → 5) |
|---|---|---|
| Intimidate | Intimidate | Foes' Attack drops to 67% at the start (more stages at higher ranks) |
| Levitate | Levitate | Earth moves can't touch it |
| Static / Flame Body / Poison Point | Static / Flame Body / Poison Point | 11–23% to shock / burn / poison an attacker |
| Insomnia | Insomnia, Vital Spirit | Wakes sooner; can't sleep at rank 5 |
| Oblivious | Oblivious, Hyper Cutter, Clear Body | Stats can't be lowered; can't be scared |
| Thick Hide | Thick Fat (general) | 7–15% less damage |
| Quick Step | Sand Veil (general) | 7–19% dodge |
| Regeneration | Regenerator, Shed Skin | Heals 3–7% HP a turn |
| Second Wind | (ours) | Once, heals 20–40% HP below a quarter HP |
| Pack Leader | (ours) | Team +2–10% stats |
| Last Stand | Blaze / Torrent / Overgrow / Swarm | Own-type moves +35–55% at ⅓ HP or less |
| Rain Runner | Swift Swim | Speed ×1.6–2.0 in Rain |
| Sun Chaser | Chlorophyll | Speed ×1.6–2.0 in Drought |
| Iron Skull | Rock Head | Recoil −20–100% |
| Overbearing | Pressure | 20–100% chance a foe's move costs an extra use |
| Momentum | Speed Boost | 20–100% chance each round to gain +1 Speed |
| Grit | Guts | +35–55% Attack while statused |
| Keen Sight | Compound Eyes, Keen Eye | +8–24% accuracy; can't be blinded from rank 3 |
| Unshaken | Inner Focus | Never flinches; can't be thrown off balance from rank 3 |
| Stalwart | Sturdy | Once, survives a knockout blow with 1 HP (at full HP; 80%+ HP by rank 5) |
| Lightning Eater | Volt Absorb, Lightning Rod | Electric hits heal 13–25% HP instead |
| Spring Soak | Water Absorb | Water hits heal 13–25% HP instead |
| Kindling | Flash Fire | Fire hits heal 13–25% HP and power up its Fire moves +35–55% |
| Barbed Hide | Rough Skin | Attackers lose 4–8% of their max HP |
| Scavenger | Pickup | 10–30% chance to find an item after a win |
| Snare Aura | Arena Trap, Shadow Tag | Pack members 20–100% less likely to run off when one is tamed |
| Plated | Battle Armor, Shell Armor | Can't be critically hit; 2–10% less from super-effective hits |
| Fortune | Serene Grace | Side-effect chances ×1.2–2.0 |
| Spore Coat | Effect Spore | 11–23% to put an attacker to sleep, poison or shock it |
| Hard Scales | Marvel Scale | +35–55% Defense while statused |
| Rain Drinker | Rain Dish | Heals 3–5% HP a turn in Rain |
| Insulated | Thick Fat | 26–50% less Fire and Electric damage |
| Stormcaller | Drizzle | Brings Rain to every battle (legendary signature: Abyss Kraken, Thunder Titan) |
| Sunbringer | Drought | Brings a Drought to every battle (legendary signature: Magma Behemoth, Worldroot Colossus) |

## Backlog (ready when new monsters fit them)

### Battle, easy
- **Reckless** (Hustle): +50% Attack, −20% accuracy.
- **Dust Coat** (Shield Dust): immune to foes' side effects.
- **Grounded / Purified / Fireproof / Warm Blood** (Limber / Immunity / Water Veil / Magma Armor): can't be shocked / poisoned / burned / frostbitten.
- **Self-Mend** (Natural Cure): status clears when swapped out.
- **Steadfast** (Clear Body / White Smoke): overlaps Oblivious; only if a line needs it under another name.

### Battle, needs a small new rule
- **Still Air** (Cloud Nine / Air Lock): cancels weather while out.
- **Mirror Bond** (Synchronize): a status inflicted on it passes back to the attacker.
- **Toxic Blood** (Liquid Ooze): drain moves hurt the drainer instead.
- **Twin Spark** (Plus / Minus): +50% when an ally also has Twin Spark.
- **Sluggard** (Truant): acts every other turn; a downside for very strong monsters.
- **Brute Force** (Huge Power / Pure Power): Attack ×1.3.

### On the map
- **Beacon** (Illuminate): in the party, more monsters spawn nearby.
- **Musk** (Stench): in the party, fewer wild fights and no ambushes.
- **Slippery** (Run Away): fleeing always works.
- **Forecast** (Forecast): one special monster changes type with the live weather.

### Skipped (no matching mechanic)
Damp, Soundproof, Suction Cups, Sticky Hold, Own Tempo, Cute Charm, Sand Stream,
Magnet Pull, Wonder Guard (too strong), Trace, Color Change.
