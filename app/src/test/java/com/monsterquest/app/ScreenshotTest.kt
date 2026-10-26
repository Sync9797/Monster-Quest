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
import com.monsterquest.app.ui.AppRoot
import com.monsterquest.app.ui.FoldInfo
import com.monsterquest.app.ui.LocalFold
import com.monsterquest.app.ui.MonsterQuestTheme
import com.monsterquest.app.ui.Tab
import com.monsterquest.core.Bestiary
import com.monsterquest.core.BoxRarity
import com.monsterquest.core.LootBox
import com.monsterquest.core.Difficulty
import com.monsterquest.core.GameState
import com.monsterquest.core.Hero
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.Item
import com.monsterquest.core.Monster
import com.monsterquest.core.QuestTask
import com.monsterquest.core.TaskSource
import com.monsterquest.core.Weapon
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
            Monster.create(Bestiary.get("toxbug"), 9, rng),
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
            ),
            gold = 1234,
            huntTokens = 6,
            pendingUpgrades = 1,
            starterChosen = true,
            monsters = monsters,
            party = monsters.take(3).map { it.uid },
            weapons = listOf(Weapon.IRON_SWORD, Weapon.TIDE_TRIDENT),
            lootBoxes = listOf(
                LootBox("b1", BoxRarity.RARE, 0, unlocking = true, walkedMeters = 2_000.0),
                LootBox("b2", BoxRarity.COMMON, 0, unlocking = true, walkedMeters = BoxRarity.COMMON.meters),
                LootBox("b3", BoxRarity.LEGENDARY, 0),
            ),
            equippedWeapon = Weapon.STORM_SPEAR,
            spellPages = mapOf("gale" to 3, "venom_cloud" to 1),
            weaponParts = 3,
            inventory = mapOf(Item.POTION to 4, Item.HI_POTION to 2, Item.ETHER to 3, Item.TAMING_ORB to 6, Item.GREAT_ORB to 2, Item.TREAT to 1),
            tasks = listOf(
                QuestTask("a", "Take out the recycling and the trash before the truck comes", Difficulty.EASY),
                QuestTask("b", "Finish quarterly report", Difficulty.HARD),
                QuestTask("c", "Dentist appointment", Difficulty.NORMAL, TaskSource.CALENDAR, now + 3_600_000, now + 5_400_000, "Personal"),
                QuestTask("d", "Gym", Difficulty.NORMAL, completedAtMs = now - 1000),
            ),
        )
    }

    private fun vm(state: GameState = sampleState()) = GameViewModel(app).also { it.debugLoad(state) }

    private fun shot(name: String, fold: FoldInfo? = null, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalFold provides fold) {
                MonsterQuestTheme { content() }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
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
