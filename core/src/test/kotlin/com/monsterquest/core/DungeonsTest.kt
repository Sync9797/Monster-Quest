package com.monsterquest.core

import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DungeonsTest {
    private val utc = ZoneOffset.UTC
    private val now = 1_800_000_000_000L
    private fun start(): GameState {
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val team = (1..3).map { Monster.create(Bestiary.get("emberkit"), 20, Random(it)) }
        return s.copy(monsters = team, party = team.map { it.uid }, torches = 10, hero = s.hero.copy(level = 20))
    }
    private fun some(s: GameState, stars: Int? = null): Dungeon =
        (0 until 60).asSequence().flatMap { Dungeons.near(s, 40.0 + it * 0.05, -74.0, now, utc) }.first { stars == null || it.stars == stars }

    @Test
    fun dungeonsVaryByDifficulty() {
        val s = start()
        val all = (0 until 40).flatMap { Dungeons.near(s, 40.0 + it * 0.05, -74.0, now, utc) }
        assertEquals((1..5).toSet(), all.map { it.stars }.toSet())
        val easy = all.first { it.stars == 1 }
        val hard = all.first { it.stars == 5 }
        assertTrue(hard.rooms > easy.rooms && hard.torchCost > easy.torchCost && hard.level > easy.level)
        assertTrue(Dungeons.residents(easy).isNotEmpty())
        assertTrue(Dungeons.bossRewards(hard).first().label.contains(hard.bossStat.label))
    }

    @Test
    fun doorsAreRandomButFixedAndFightsScale() {
        val s = start()
        val d = some(s, 1)
        val run = DungeonRun(d)
        assertEquals(Dungeons.doors(run), Dungeons.doors(run))
        val (l, r) = Dungeons.doors(run)
        assertTrue(l != r)
        // Easy monster rooms hold at most three.
        val sizes = (0 until 40).map { Dungeons.monstersFor(run.copy(step = it), RoomKind.MONSTERS).size }
        assertTrue(sizes.all { it in 1..3 } && sizes.max() == 3)
        val hard = DungeonRun(some(s, 5))
        assertTrue((0 until 40).map { Dungeons.monstersFor(hard.copy(step = it), RoomKind.MONSTERS).size }.max() == 4)
        // Guardians are cursed; Alphas are Alphas; the champion is a hero of the boss class.
        assertTrue(Dungeons.monstersFor(run, RoomKind.COMBAT).all { it.cursed })
        assertTrue(Dungeons.monstersFor(run, RoomKind.ELITE).single().alpha)
        assertEquals(d.bossClass, Dungeons.champion(d).cls)
    }

    @Test
    fun aRunFromEntranceToChampion() {
        var s = start()
        val d = some(s, 2)
        s = Dungeons.enter(s, d).state
        assertEquals(10 - d.torchCost, s.torches)
        assertTrue(s.dungeonRun != null)
        // Walk through, taking whichever door, and win every fight.
        var guard = 0
        while (s.dungeonRun?.atBoss == false && guard++ < 40) {
            val run = s.dungeonRun!!
            val (l, _) = Dungeons.doors(run)
            s = Dungeons.open(s, left = true, Random(guard)).state
            when {
                l.isFight -> {
                    assertEquals(l, s.dungeonRun!!.fighting)
                    s = Dungeons.afterFight(s, BattleOutcome.WON, now, Random(guard), utc).state
                }
                l == RoomKind.TRAP -> s = Dungeons.springTrap(s, Random(guard)).state
            }
            assertEquals(run.step + 1, s.dungeonRun!!.step)
        }
        assertTrue(s.dungeonRun!!.atBoss)
        val before = s.hero.attr(d.bossStat)
        s = Dungeons.open(s, true).state
        assertEquals(RoomKind.BOSS, s.dungeonRun!!.fighting)
        val goldBefore = s.gold
        val cleared = Dungeons.afterFight(s, BattleOutcome.WON, now, Random(1), utc)
        // The champion's prize comes with its icons: the stat, the gold and whatever the pool rolled.
        assertTrue(cleared.rewards.any { it is Reward.Stat && it.attribute == d.bossStat })
        assertTrue(cleared.rewards.any { it is Reward.Gold })
        assertEquals("${d.name} cleared", cleared.rewardTitle)
        s = cleared.state
        assertNull(s.dungeonRun)
        assertEquals(before + 1, s.hero.attr(d.bossStat))
        assertTrue(s.gold > goldBefore)
        assertTrue(d.id in s.dungeonsCleared)
        assertTrue(Dungeons.near(s, d.lat, d.lon, now, utc).none { it.id == d.id })
        // The stat only rises once a day.
        val d2 = some(s, 1)
        var t = Dungeons.enter(s, d2).state
        t = t.copy(dungeonRun = t.dungeonRun!!.copy(step = d2.rooms, fighting = RoomKind.BOSS))
        t = Dungeons.afterFight(t, BattleOutcome.WON, now, Random(2), utc).state
        assertEquals(before + 1, t.hero.attr(d.bossStat))
    }

    @Test
    fun trapsShowOddsAndHurtWhenTheyFail() {
        var s = start()
        val easy = some(s, 1)
        val hard = some(s, 5)
        assertTrue(Dungeons.trapChance(s, easy) > Dungeons.trapChance(s, hard))
        assertTrue(Dungeons.trapChance(s.copy(hero = s.hero.copy(heroClass = HeroClass.ROGUE)), hard) > Dungeons.trapChance(s, hard))
        s = Dungeons.enter(s, hard).state
        val hurt = (1..30).map { Dungeons.springTrap(s, Random(it)) }.first { it.state.heroDamage > 0 }
        assertTrue("!" in hurt.message)
        assertEquals(1, hurt.state.dungeonRun!!.step)
    }

    @Test
    fun fallingLosesHalfLeavingKeepsAll() {
        var s = start()
        s = Dungeons.enter(s, some(s, 3)).state
        s = s.copy(dungeonRun = s.dungeonRun!!.copy(gold = 100, torches = 4, fighting = RoomKind.MONSTERS))
        val lost = Dungeons.afterFight(s, BattleOutcome.LOST, now, Random(1), utc).state
        assertEquals(s.gold + 50, lost.gold)
        assertEquals(s.torches + 2, lost.torches)
        assertNull(lost.dungeonRun)
        val left = Dungeons.leave(s).state
        assertEquals(s.gold + 100, left.gold)
    }

    @Test
    fun fountainsRestoreAndNeedTorches() {
        var s = start().copy(torches = 0)
        val d = some(s)
        assertEquals(s, Dungeons.enter(s, d).state)
        s = Dungeons.enter(s.copy(torches = 5, heroDamage = 30), d).state
        // Force a fountain behind the left door by trying runs until one has it.
        val step = (0 until 30).first { Dungeons.doors(s.dungeonRun!!.copy(step = it)).first == RoomKind.FOUNTAIN }
        s = s.copy(dungeonRun = s.dungeonRun!!.copy(step = step))
        s = Dungeons.open(s, left = true).state
        assertEquals(0, s.heroDamage)
    }

    @Test
    fun rewardScreensListWhatTheyGive() {
        // A treasury chest lists its gold (and whatever else it held).
        var s = start()
        val d = some(s, 3)
        s = Dungeons.enter(s, d).state
        val run = s.dungeonRun!!
        val chest = Dungeons.open(s.copy(dungeonRun = run.copy(step = 0)).let { st ->
            // Force a treasury behind the left door.
            var t = st
            var seed = 0
            while (Dungeons.doors(t.dungeonRun!!).first != RoomKind.TREASURY && seed < 500) {
                seed++
                t = t.copy(dungeonRun = t.dungeonRun!!.copy(dungeon = t.dungeonRun!!.dungeon.copy(seed = seed.toLong())))
            }
            t
        }, left = true, Random(3))
        if (chest.rewardTitle == "Treasury") assertTrue(chest.rewards.first() is Reward.Gold)
        // Every entry in a champion's pool has something to show as its icon.
        Dungeons.bossRewards(d).forEach { r ->
            assertTrue(r.kind != BossReward.Kind.ITEM || r.item != null, r.label)
            assertTrue(r.kind != BossReward.Kind.STAT || r.stat != null, r.label)
        }
        // The academy hands out a tome.
        assertEquals(listOf<Reward>(Reward.Tome(Facilities.STUDY_PAGES)), Facilities.study(start(), now, utc).rewards)
    }
}
