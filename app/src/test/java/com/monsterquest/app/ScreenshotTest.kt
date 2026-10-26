package com.monsterquest.app

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.monsterquest.app.ui.AdventuresTab
import com.monsterquest.app.ui.AppRoot
import androidx.compose.foundation.layout.padding
import com.monsterquest.app.ui.art.HeroArt
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.MonsterArt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.monsterquest.app.ui.FoldInfo
import com.monsterquest.app.ui.LocalFold
import com.monsterquest.app.ui.MonsterQuestTheme
import com.monsterquest.app.ui.Tab
import com.monsterquest.core.ActiveAdventure
import com.monsterquest.core.Adventure
import com.monsterquest.core.Bestiary
import com.monsterquest.core.BoxRarity
import com.monsterquest.core.LootBox
import com.monsterquest.core.Difficulty
import com.monsterquest.core.GameState
import com.monsterquest.core.Hero
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Item
import com.monsterquest.core.Monster
import com.monsterquest.core.QuestTask
import com.monsterquest.core.TaskSource
import com.monsterquest.core.Armory
import com.monsterquest.core.WeaponType
import com.monsterquest.core.World
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Renders every screen at foldable sizes and writes PNGs to app/build/screenshots.
 * CI publishes them to the `screenshots` branch so layouts can be reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val device: String) {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun sampleState(): GameState {
        val rng = Random(3)
        val monsters = listOf(
            Monster.create(Bestiary.get("ember_pup"), 20, rng).copy(nickname = "Sir Barksalot III"),
            Monster.create(Bestiary.get("zapmouse"), 12, rng),
            Monster.create(Bestiary.get("emberkit"), 30, rng),
            Monster.create(Bestiary.get("toxbug"), 9, rng).copy(fainted = true),
            Monster.create(Bestiary.get("dread_lich"), 15, rng),
        )
        val now = System.currentTimeMillis()
        return GameState(
            hero = Hero(
                name = "Aaronimus",
                level = 12,
                xp = 230,
                unspentPoints = 4,
                attributes = HeroAttribute.entries.associateWith { 14 },
                heroClass = HeroClass.ROGUE,
                classChosen = true,
            ),
            equippedArmor = com.monsterquest.core.Armory.rollArmor(12, Random(6), com.monsterquest.core.ArmorType.LEATHER, minimum = com.monsterquest.core.GearRarity.EPIC),
            armor = listOf(com.monsterquest.core.Armory.rollArmor(12, Random(7), com.monsterquest.core.ArmorType.PLATE)),
            seen = Bestiary.all.take(14).map { it.id }.toSet() + "dread_lich",
            stepsToday = 6_420,
            stepsDay = java.time.LocalDate.now().toEpochDay(),
            stepHistory = (0L..12L).associate { java.time.LocalDate.now().toEpochDay() - it to (if (it % 3 == 0L) 7_000 else 2_500) },
            questDays = listOf(0L, 1L, 2L, 4L, 5L, 8L, 9L, 15L).associate { java.time.LocalDate.now().toEpochDay() - it to 2 },
            lureUntilMs = now + 18 * 60_000L,
            gold = 1234,
            huntTokens = 6,
            pendingUpgrades = 1,
            starterChosen = true,
            monsters = monsters,
            party = monsters.take(3).map { it.uid },
            gear = listOf(Armory.roll(12, Random(3), WeaponType.SWORD, luck = 40), Armory.roll(12, Random(4), WeaponType.STAFF)),
            lootBoxes = listOf(
                LootBox("b1", BoxRarity.RARE, 0, unlocking = true, walkedMeters = 2_000.0),
                LootBox("b2", BoxRarity.COMMON, 0, unlocking = true, walkedMeters = BoxRarity.COMMON.meters),
                LootBox("b3", BoxRarity.LEGENDARY, 0),
            ),
            equippedGear = Armory.roll(12, Random(5), WeaponType.DAGGER, luck = 80),
            spellPages = mapOf("gale" to 3, "venom_cloud" to 1),
            parts = mapOf(WeaponType.DAGGER to 3, WeaponType.BROADSWORD to 5),
            inventory = mapOf(Item.LURE to 2, Item.POTION to 4, Item.HI_POTION to 2, Item.ETHER to 3, Item.TAMING_ORB to 6, Item.GREAT_ORB to 2, Item.TREAT to 1),
            tasks = listOf(
                QuestTask("a", "Take out the recycling and the trash before the truck comes", Difficulty.EASY),
                QuestTask("b", "Finish quarterly report", Difficulty.HARD),
                QuestTask("c", "Dentist appointment", Difficulty.NORMAL, TaskSource.CALENDAR, now + 3_600_000, now + 5_400_000, "Personal"),
                QuestTask("d", "Gym", Difficulty.NORMAL, completedAtMs = now - 1000),
            ),
        )
    }

    private fun vm(state: GameState = sampleState()) = GameViewModel(app).also { it.debugLoad(state) }

    private fun shot(name: String, fold: FoldInfo? = null, advanceMs: Long = 1_000, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalFold provides fold) {
                MonsterQuestTheme { content() }
            }
        }
        capture(name, advanceMs)
    }

    /** Lets time pass, then saves what's on screen. */
    private fun capture(name: String, advanceMs: Long) {
        rule.mainClock.advanceTimeBy(advanceMs)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "${device}_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A half-open horizontal hinge across the middle of the screen (tabletop posture). */
    private fun tabletop(): FoldInfo {
        val dm = app.resources.displayMetrics
        val mid = dm.heightPixels / 2
        return FoldInfo(isSeparating = true, isVertical = false, bounds = Rect(0, mid, dm.widthPixels, mid))
    }

    @Test fun starter() { val v = vm(GameState()); shot("01_starter") { AppRoot(v) } }
    @Test fun quests() { val v = vm(); shot("02_quests") { AppRoot(v, Tab.QUESTS) } }
    @Test fun hero() { val v = vm(); shot("03_hero") { AppRoot(v, Tab.HERO) } }
    @Test fun monsters() { val v = vm(); shot("04_monsters") { AppRoot(v, Tab.MONSTERS) } }
    @Test fun bag() { val v = vm(); shot("05_bag") { AppRoot(v, Tab.BAG) } }

    @Test fun map() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        shot("06_map") { AppRoot(v, Tab.MAP) }
    }

    @Test fun battle() {
        val v = vm()
        v.hunt()
        shot("07_battle") { AppRoot(v) }
    }

    @Test fun focus() {
        val v = vm()
        v.startFocus(30)
        shot("09_focus") { AppRoot(v) }
    }

    @Test fun adventures() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val base = sampleState()
        val now = System.currentTimeMillis()
        val running = ActiveAdventure(
            Adventure("demo", "Riverside Park", "Recover a lost relic near Riverside Park", 2, 12, 60 * 60_000L),
            base.monsters.drop(3).take(2).map { it.uid },
            now - 20 * 60_000L,
            0.72,
        )
        val v = vm(base.copy(adventures = listOf(running)))
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        shot("10_adventures") { AdventuresTab(v) }
    }

    /** Every monster's artwork, for reviewing the designs. */
    @Test fun bestiary() {
        shot("11_bestiary") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0D171A)),
            ) {
                Bestiary.all.chunked(6).forEach { row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEach { sp ->
                            MonsterArt(sp, androidx.compose.ui.Modifier.size(56.dp))
                        }
                    }
                }
                androidx.compose.foundation.layout.Row {
                    com.monsterquest.core.HeroClass.entries.forEach { HeroArt(it, androidx.compose.ui.Modifier.size(80.dp)) }
                }
            }
        }
    }

    private fun onBackground(content: @Composable () -> Unit) = @Composable {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0D171A)),
        ) { content() }
    }

    @Test fun dashboard() {
        val v = vm()
        shot("12_dashboard", content = onBackground {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.monsterquest.app.ui.WeeklyCard(v)
                com.monsterquest.app.ui.DashboardCard(v)
            }
        })
    }

    @Test fun journal() {
        val v = vm()
        shot("13_journal", content = onBackground { com.monsterquest.app.ui.JournalTab(v) })
    }

    /** The hero wearing each kind of gear, and bare-handed. */
    @Test fun outfits() {
        val legendary = com.monsterquest.core.GearRarity.LEGENDARY
        val outfits = listOf(
            HeroClass.NONE to HeroOutfit(),
            HeroClass.WARRIOR to HeroOutfit(WeaponType.BROADSWORD, armor = com.monsterquest.core.ArmorType.PLATE),
            HeroClass.WARRIOR to HeroOutfit(WeaponType.SWORD, legendary, com.monsterquest.core.Element.FIRE, com.monsterquest.core.ArmorType.PLATE, legendary),
            HeroClass.ROGUE to HeroOutfit(WeaponType.DAGGER, com.monsterquest.core.GearRarity.EPIC, com.monsterquest.core.Element.GAS, com.monsterquest.core.ArmorType.LEATHER, com.monsterquest.core.GearRarity.RARE),
            HeroClass.MAGE to HeroOutfit(WeaponType.STAFF, armor = com.monsterquest.core.ArmorType.ROBE, armorRarity = com.monsterquest.core.GearRarity.EPIC),
            HeroClass.MAGE to HeroOutfit(WeaponType.WAND, legendary, com.monsterquest.core.Element.ELECTRIC, com.monsterquest.core.ArmorType.ROBE, legendary),
        )
        shot("14_outfits", content = onBackground {
            androidx.compose.foundation.layout.Column {
                outfits.chunked(3).forEach { row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEach { (cls, o) -> HeroArt(cls, androidx.compose.ui.Modifier.size(110.dp), outfit = o) }
                    }
                }
                // An evolution line from first form to final form, plus a few silhouettes.
                androidx.compose.foundation.layout.Row {
                    Bestiary.lineOf(Bestiary.get("emberkit")).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(84.dp)) }
                }
                androidx.compose.foundation.layout.Row {
                    Bestiary.lineOf(Bestiary.get("bubblefin")).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(84.dp)) }
                }
                androidx.compose.foundation.layout.Row {
                    Bestiary.lineOf(Bestiary.get("toxbug")).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(84.dp), silhouette = it.stage > 1) }
                }
            }
        })
    }

    /** The three starters' lines and some shinies. */
    @Test fun starters() {
        shot("23_starters", content = onBackground {
            androidx.compose.foundation.layout.Column {
                com.monsterquest.core.GameEngine.starters.forEach { id ->
                    androidx.compose.foundation.layout.Row {
                        Bestiary.lineOf(Bestiary.get(id)).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(104.dp)) }
                    }
                }
                androidx.compose.foundation.layout.Row {
                    listOf("thunderhorn", "emberkit", "goopling").forEach { MonsterArt(Bestiary.get(it), androidx.compose.ui.Modifier.size(104.dp), shiny = true) }
                }
                androidx.compose.foundation.layout.Row {
                    listOf("magma_behemoth", "dread_lich").forEach { MonsterArt(Bestiary.get(it), androidx.compose.ui.Modifier.size(104.dp), shiny = true) }
                }
            }
        })
    }

    /** The five newest evolution lines, first form to final form. */
    @Test fun newMonsters() {
        shot("15_new_monsters", content = onBackground {
            androidx.compose.foundation.layout.Column {
                listOf("goopling", "pebblit", "zipwing", "shellsnap", "impling").forEach { id ->
                    androidx.compose.foundation.layout.Row {
                        Bestiary.lineOf(Bestiary.get(id)).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(104.dp)) }
                    }
                }
            }
        })
    }

    @Test fun settings() { val v = vm(); shot("16_settings") { AppRoot(v, initialSettings = true) } }

    @Test fun batterySaver() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        v.lastSpawnAlert = "A wild Zipwing appeared nearby!"
        v.batterySaver = true
        v.saverByFlip = true
        shot("17_battery_saver") { AppRoot(v, Tab.MAP) }
    }

    /** Mid-animation: attacks in flight, the attacker ringed, the turn order strip. */
    @Test fun battleFx() {
        val v = vm()
        v.hunt()
        v.battleAction(com.monsterquest.core.BattleAction.Attack)
        // Partway through: the bolt is in flight.
        shot("18_battle_fx", advanceMs = 330) { AppRoot(v) }
        capture("19_battle_hit", 300)
    }

    @Test fun lootPopup() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        v.lootPopup = LootPopup(
            "Corner Cafe",
            listOf(
                com.monsterquest.core.Reward.Gold(12),
                com.monsterquest.core.Reward.Items(Item.TAMING_ORB, 2),
                com.monsterquest.core.Reward.Items(Item.POTION, 1),
                com.monsterquest.core.Reward.WeaponDrop(Armory.roll(12, Random(9), WeaponType.STAFF, luck = 60)),
                com.monsterquest.core.Reward.Pages("gale", 1, learned = false),
            ),
        )
        shot("20_loot_popup", advanceMs = 900) { AppRoot(v, Tab.MAP) }
    }

    @Test fun help() {
        shot("21_help", content = onBackground { com.monsterquest.app.ui.HelpScreen {} })
    }

    /** A 4 vs 3 pack ambush. */
    @Test fun packBattle() {
        val v = vm()
        val r = Random(11)
        v.debugStartBattle(
            Monster.create(Bestiary.get("impling"), 13, r),
            listOf(Monster.create(Bestiary.get("goopling"), 12, r), Monster.create(Bestiary.get("zipwing"), 13, r)),
            ambush = true,
        )
        shot("22_pack_battle", advanceMs = 5_000) { AppRoot(v) }
    }

    @Test fun battleTabletop() {
        val v = vm()
        v.hunt()
        shot("08_battle_tabletop", tabletop()) { AppRoot(v) }
    }
}

/** Galaxy Z Fold cover screen (the narrowest common foldable). */
@Config(sdk = [34], qualifiers = "w344dp-h882dp-xxhdpi")
class CoverScreenshots : ScreenshotBase("cover")

/** Galaxy Z Fold inner screen, unfolded. */
@Config(sdk = [34], qualifiers = "w673dp-h841dp-xhdpi")
class InnerScreenshots : ScreenshotBase("inner")

/** Regular phone. */
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScreenshots : ScreenshotBase("phone")
