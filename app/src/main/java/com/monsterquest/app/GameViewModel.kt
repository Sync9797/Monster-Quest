package com.monsterquest.app

import com.monsterquest.app.data.CalendarReader
import com.monsterquest.core.STEPS_PER_MILE
import com.monsterquest.core.METERS_PER_MILE
import com.monsterquest.core.Villains
import com.monsterquest.core.VillainCamp
import com.monsterquest.core.Stat
import com.monsterquest.core.WeaponType
import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsterquest.app.data.PlacesRepository
import com.monsterquest.app.data.SaveRepository
import com.monsterquest.app.focus.FocusService
import com.monsterquest.app.focus.FocusStore
import com.monsterquest.core.Adventure
import com.monsterquest.core.Adventures
import com.monsterquest.core.Battle
import com.monsterquest.core.BattleAction
import com.monsterquest.core.BattleOutcome
import com.monsterquest.core.Difficulty
import com.monsterquest.core.EncounterType
import com.monsterquest.core.GameEngine
import com.monsterquest.core.GameState
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Item
import com.monsterquest.core.LootBoxes
import com.monsterquest.core.Monster
import com.monsterquest.core.Shrine
import com.monsterquest.core.SpawnConditions
import com.monsterquest.core.Settings
import com.monsterquest.core.StepSource
import com.monsterquest.app.data.HealthSteps
import com.monsterquest.core.Update
import com.monsterquest.core.WildSpawn
import com.monsterquest.core.World
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a shrine just gave you. */
data class LootPopup(val title: String, val rewards: List<com.monsterquest.core.Reward>, val id: Long = System.nanoTime())

/** How close (meters) a new spawn must be to buzz the phone: battle range. */
const val ALERT_RANGE_M = World.SPAWN_RANGE_M

/** At most one ambush this often. */
const val AMBUSH_COOLDOWN_MS = 10 * 60_000L

/** No ambush in the first moments after opening the map. */
const val AMBUSH_GRACE_MS = 30_000L

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SaveRepository(app)

    var state by mutableStateOf(repo.load())
        private set

    /** One-off message for the snackbar. */
    var message by mutableStateOf<String?>(null)
        private set

    var battle by mutableStateOf<BattleView?>(null)
        private set
    private var activeBattle: Battle? = null

    var location by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var shrines by mutableStateOf<List<Shrine>>(emptyList())
        private set
    var loadingPlaces by mutableStateOf(false)
        private set
    private var placesFetchedAt: Pair<Double, Double>? = null


    // Saves happen in order on a background thread; rapid changes are conflated.
    private val pendingSave = MutableStateFlow(state)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            pendingSave.drop(1).collect { repo.save(it) }
        }
    }

    private fun set(s: GameState) {
        val walked = s.stats.totalSteps - state.stats.totalSteps
        state = s.withJournal()
        pendingSave.value = s
        if (walked > 0) rollSecretEncounter(walked.toInt())
    }

    // ---- secret walking encounters -------------------------------------------

    /** Steps toward the next secret-encounter roll (every [SecretEncounters.STEPS]). */
    private var secretBank = 0
    /** True while the map is on screen; surprise attacks only happen there. */
    var mapVisible = false
    private var lastBattleEndMs = 0L

    /** Monsters that just leapt out at you: the map shows "Ambushed!" and buzzes, then the fight starts. */
    var secretAmbush by mutableStateOf<List<Monster>?>(null)
        private set

    private fun rollSecretEncounter(steps: Int) {
        val (bank, hit) = com.monsterquest.core.SecretEncounters.roll(secretBank, steps, kotlin.random.Random.Default)
        secretBank = bank
        val now = System.currentTimeMillis()
        if (!hit || !mapVisible || !state.settings.ambushes || battle != null || secretAmbush != null) return
        if (state.focus != null || state.monsters.isEmpty() || now - lastBattleEndMs < com.monsterquest.core.SecretEncounters.COOLDOWN_MS) return
        val hour = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
        secretAmbush = com.monsterquest.core.SecretEncounters.encounter(state.hero.level, hour, kotlin.random.Random.Default)
    }

    /** The "Ambushed!" pop-up has shown: the monsters strike first. */
    fun startSecretAmbush() {
        val group = secretAmbush ?: return
        secretAmbush = null
        if (battle != null) return
        batterySaver = false
        startBattle(group.first(), EncounterType.WILD, null, group.drop(1), ambush = true)
    }

    /** Test hook: make a surprise attack happen now. */
    @VisibleForTesting
    fun debugSecretAmbush(group: List<Monster>) {
        secretAmbush = group
    }

    private fun apply(u: Update) {
        set(u.state)
        message = u.message
    }

    /** Test hook: replace the save without touching disk. */
    @VisibleForTesting
    fun debugLoad(s: GameState) = set(s)

    /** Test hook: pretend to be somewhere with the given shrines, skipping the network. */
    @VisibleForTesting
    fun debugSetLocation(lat: Double, lon: Double, nearby: List<Shrine>) {
        placesFetchedAt = lat to lon
        location = lat to lon
        shrines = nearby
    }

    fun clearMessage() {
        message = null
    }

    fun toast(text: String) {
        message = text
    }

    // ---- setup / hero ------------------------------------------------------

    fun chooseStarter(speciesId: String) = apply(GameEngine.chooseStarter(state, speciesId))

    /** The "new starters" gift was put off until next time the app opens. */
    var starterGiftLater by mutableStateOf(false)

    fun claimStarterGift(speciesId: String) = apply(GameEngine.claimStarterGift(state, speciesId))
    fun allocate(attr: HeroAttribute) = apply(GameEngine.allocatePoint(state, attr))
    fun chooseClass(cls: HeroClass) = apply(GameEngine.chooseClass(state, cls))
    fun upgrade() = apply(GameEngine.applyUpgrade(state))
    fun buyStorage(bag: Boolean) = apply(GameEngine.buyStorage(state, bag))
    fun renameHero(name: String) {
        val n = name.trim().take(16)
        if (n.isNotEmpty()) set(state.copy(hero = state.hero.copy(name = n)))
    }

    // ---- quests ------------------------------------------------------------

    fun addTask(title: String, difficulty: Difficulty, dueMs: Long? = null, priority: com.monsterquest.core.Priority = com.monsterquest.core.Priority.NORMAL) =
        apply(GameEngine.addTask(state, title, difficulty, dueMs = dueMs, priority = priority))
    fun editTask(id: String, title: String, difficulty: Difficulty, dueMs: Long?, priority: com.monsterquest.core.Priority) =
        apply(GameEngine.editTask(state, id, title, difficulty, dueMs, priority))
    fun deleteTask(id: String) = apply(GameEngine.deleteTask(state, id))
    fun completeTask(id: String) = apply(GameEngine.completeTask(state, id, System.currentTimeMillis()))

    var syncingCalendar by mutableStateOf(false)
        private set

    /** Pull events from the phone's calendar into quests. */
    /** A sync was asked for while one was running (e.g. quick calendar toggles); run once more after. */
    private var resyncPending = false

    fun syncCalendar(quiet: Boolean = false) {
        if (syncingCalendar) {
            resyncPending = true
            return
        }
        syncingCalendar = true
        viewModelScope.launch {
            try {
                val prefs = state.settings
                val events = withContext(Dispatchers.IO) {
                    CalendarReader.read(
                        getApplication(),
                        daysAhead = prefs.calendarDaysAhead,
                        calendarIds = if (prefs.syncAllCalendars) null else prefs.syncCalendars,
                    )
                }
                    .filter { prefs.includeAllDayEvents || !it.allDay }
                val u = GameEngine.syncCalendar(state, events)
                if (quiet) set(u.state) else apply(u)
            } catch (e: SecurityException) {
                if (!quiet) message = "Calendar permission is needed to sync."
            } catch (e: Exception) {
                if (!quiet) message = "Calendar sync failed: ${e.message}"
            } finally {
                syncingCalendar = false
                if (resyncPending) {
                    resyncPending = false
                    syncCalendar(quiet = true)
                }
            }
        }
    }


    // ---- monsters & items --------------------------------------------------

    // ---- phone-free sessions -----------------------------------------------

    /** Result of the last phone-free session, shown in a dialog. */
    var focusResult by mutableStateOf<String?>(null)
        private set
    var focusSucceeded by mutableStateOf(false)
        private set

    fun startFocus(minutes: Int) {
        val u = GameEngine.startFocus(state, minutes, System.currentTimeMillis())
        apply(u)
        val f = u.state.focus ?: return
        FocusStore.clear(getApplication())
        FocusService.start(getApplication(), f.id, f.startMs, f.endMs)
    }

    fun giveUpFocus() = endFocus(success = false)

    fun dismissFocusResult() {
        focusResult = null
    }

    /**
     * Called whenever the app comes to the foreground. A phone-free session
     * ends here if the service recorded an outcome (the player left the game,
     * or time ran out); otherwise it keeps running.
     */
    fun onAppResumed() {
        regenerate()
        GameEngine.dailyRecovery(state, System.currentTimeMillis())?.let(::apply)
        GameEngine.claimDailyLure(state, System.currentTimeMillis())?.let(::apply)
        val f = state.focus ?: return
        when (FocusStore.outcome(getApplication(), f.id)) {
            FocusStore.Outcome.FAILED -> endFocus(success = false)
            FocusStore.Outcome.SUCCESS -> endFocus(success = true)
            null -> if (System.currentTimeMillis() >= f.endMs) endFocus(success = true)
        }
    }

    /** The session's timer ran out while the game was open. */
    fun onFocusTimerDone() {
        val f = state.focus ?: return
        if (System.currentTimeMillis() >= f.endMs) endFocus(success = true)
    }

    private fun endFocus(success: Boolean) {
        if (state.focus == null) return
        val u = GameEngine.resolveFocus(state, success)
        set(u.state)
        focusSucceeded = success
        focusResult = u.message
        FocusService.stop(getApplication())
        FocusStore.clear(getApplication())
    }

    // ---- loot boxes ---------------------------------------------------------

    /** Result of opening a loot box, shown in a dialog. */
    var boxResult by mutableStateOf<String?>(null)
        private set

    /** True when the phone's step counter is usable; otherwise GPS distance is used. */
    var stepSensorAvailable by mutableStateOf(false)

    private var lastWalkPoint: Pair<Double, Double>? = null

    /** Step counter reading: unlocks loot boxes and pays XP/healing every 100 steps. */
    fun onStepCount(totalSteps: Long) {
        if (state.settings.stepSource != StepSource.PHONE) return
        val u = GameEngine.onStepCount(state, totalSteps)
        set(u.state)
        if (u.message.isNotEmpty()) message = u.message
    }

    fun startUnlocking(id: String) = apply(LootBoxes.startUnlocking(state, id))
    fun discardBox(id: String) = apply(LootBoxes.discard(state, id))

    fun openBox(id: String) {
        val ready = state.lootBoxes.find { it.id == id }?.ready == true
        val opening = LootBoxes.open(state, id)
        set(opening.update.state)
        if (ready) {
            boxResult = opening.update.message
            boxBoss = opening.boss
        } else {
            message = opening.update.message
        }
    }

    /** Boss that came out of a Level 4 box; the result dialog's button starts the fight. */
    var boxBoss by mutableStateOf<Monster?>(null)
        private set

    fun dismissBoxResult() {
        boxResult = null
        boxBoss?.let { startBattle(it, EncounterType.BOX_BOSS, null) }
        boxBoss = null
    }

    fun forge(type: WeaponType) = apply(GameEngine.forge(state, type))
    fun upgradeSpell(id: String) = apply(GameEngine.upgradeSpell(state, id))
    fun usePotion(item: Item, target: String) = apply(GameEngine.usePotion(state, item, target))
    fun perfectStat(uid: String, stat: Stat) = apply(GameEngine.perfectStat(state, uid, stat))

    /** GPS fallback for phones without a step counter: count distance moved while the app is open. */
    // ---- settings ------------------------------------------------------------

    fun updateSettings(change: (Settings) -> Settings) = set(state.copy(settings = change(state.settings)))

    fun clearCalendarQuests() = apply(GameEngine.clearCalendarQuests(state))

    /** The phone's calendars, for choosing which to sync. */
    var phoneCalendars by mutableStateOf<List<com.monsterquest.app.data.PhoneCalendar>>(emptyList())
        private set

    fun loadCalendars() {
        viewModelScope.launch {
            phoneCalendars = runCatching { withContext(Dispatchers.IO) { CalendarReader.calendars(getApplication()) } }.getOrDefault(emptyList())
        }
    }

    /**
     * Turns one calendar on or off for syncing, then re-syncs so its events
     * appear (or unfinished ones disappear) right away.
     */
    fun toggleCalendar(id: Long) {
        val prefs = state.settings
        // Switching from "all": start from every calendar that included.
        val current = if (prefs.syncAllCalendars) phoneCalendars.filter { !it.isNoise }.map { it.id }.toSet() else prefs.syncCalendars
        val next = if (id in current) current - id else current + id
        updateSettings { it.copy(syncAllCalendars = false, syncCalendars = next) }
        if (next.isEmpty()) message = "No calendars chosen: nothing will sync until you pick one."
        syncCalendar(quiet = true)
    }

    fun syncAllCalendars() {
        updateSettings { it.copy(syncAllCalendars = true, syncCalendars = emptySet()) }
        syncCalendar(quiet = true)
    }

    fun setStepSource(source: StepSource) {
        set(GameEngine.setStepSource(state, source))
        message = "Steps now come from ${source.label}."
        refreshHealthStatus()
        refreshHealthSteps()
    }

    /** Whether Health Connect is installed and the game may read steps. */
    var healthStatus by mutableStateOf<HealthSteps.Status?>(null)
        private set

    fun refreshHealthStatus() {
        viewModelScope.launch { healthStatus = runCatching { HealthSteps.status(getApplication()) }.getOrNull() }
    }

    /** Reads today's steps from Health Connect when that's the chosen source. */
    fun refreshHealthSteps() {
        val source = state.settings.stepSource
        if (!source.usesHealthConnect) return
        viewModelScope.launch {
            val total = HealthSteps.stepsToday(getApplication(), samsungOnly = source == StepSource.SAMSUNG_HEALTH) ?: return@launch
            val u = GameEngine.onDailyStepTotal(state, GameEngine.epochDay(System.currentTimeMillis()), total)
            set(u.state)
            if (u.message.isNotEmpty()) message = u.message
        }
    }

    private fun walkFromGps(lat: Double, lon: Double) {
        val source = state.settings.stepSource
        if (!(source == StepSource.GPS || (source == StepSource.PHONE && !stepSensorAvailable))) return
        val last = lastWalkPoint
        if (last != null) {
            val d = World.distanceMeters(last.first, last.second, lat, lon)
            // Ignore GPS jitter and big jumps (e.g. a fix after being in a car or tunnel).
            if (d < 5) return
            if (d < 300) {
                val u = GameEngine.walk(state, (d / METERS_PER_MILE * STEPS_PER_MILE).toInt())
                set(u.state)
                if (u.message.isNotEmpty()) message = u.message
            }
        }
        lastWalkPoint = lat to lon
    }

    // ---- adventures ---------------------------------------------------------

    /** Result of a returning adventure, shown in a dialog. */
    var adventureResult by mutableStateOf<String?>(null)
        private set
    var adventureSucceeded by mutableStateOf(false)
        private set

    /** Today's adventures at real places near you, closest first. */
    fun adventureOffers(nowMs: Long): List<Adventure> {
        val here = location ?: return emptyList()
        val nearest = shrines.sortedBy { World.distanceMeters(here.first, here.second, it.lat, it.lon) }
        return Adventures.offersNear(state, nearest, nowMs)
    }

    fun sendAdventure(adventure: Adventure, uids: List<String>) =
        apply(Adventures.send(state, adventure, uids, System.currentTimeMillis()))

    fun claimAdventure(id: String) {
        val u = Adventures.claim(state, id, System.currentTimeMillis())
        set(u.state)
        adventureSucceeded = u.message.contains("success!")
        adventureResult = u.message
    }

    fun dismissAdventureResult() {
        adventureResult = null
    }

    // ---- weapons -----------------------------------------------------------

    fun equip(id: String) = apply(GameEngine.equip(state, id))
    fun unequip() = apply(GameEngine.unequip(state))
    fun dropWeapon(id: String) = apply(GameEngine.dropWeapon(state, id))
    fun equipArmor(id: String) = apply(GameEngine.equipArmor(state, id))
    fun unequipArmor(slot: com.monsterquest.core.ArmorSlot = com.monsterquest.core.ArmorSlot.BODY) = apply(GameEngine.unequipArmor(state, slot))
    fun dropArmor(id: String) = apply(GameEngine.dropArmor(state, id))

    fun restore(uid: String) = apply(GameEngine.restore(state, uid))
    fun setMoves(uid: String, moveIds: List<String>) = apply(GameEngine.setMoves(state, uid, moveIds))
    fun resetMoves(uid: String) =
        set(state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(moves = null) else it }))

    /** Hourly rest healing for the hero and every monster (knocked-out ones wake at 10%). */
    fun regenerate() {
        val u = GameEngine.regenerate(state, System.currentTimeMillis())
        if (u.state != state) set(u.state)
        if (u.message.isNotEmpty()) message = u.message
    }

    fun toggleParty(uid: String) = apply(GameEngine.toggleParty(state, uid))
    fun setPartySlot(uid: String, slot: Int) {
        val u = GameEngine.setPartySlot(state, uid, slot)
        set(u.state)
        if (u.message.isNotEmpty()) message = u.message
    }
    fun release(uid: String) = apply(GameEngine.release(state, uid))
    fun candyLevelUp(uid: String) = apply(GameEngine.candyLevelUp(state, uid))
    fun rename(uid: String, name: String) = apply(GameEngine.rename(state, uid, name))
    fun feedTreat(uid: String) = apply(GameEngine.feedTreat(state, uid))
    fun buy(item: Item) = apply(GameEngine.buy(state, item))
    fun discard(item: Item) = apply(GameEngine.discard(state, item))

    // ---- map ---------------------------------------------------------------

    fun onLocation(lat: Double, lon: Double) {
        location = lat to lon
        walkFromGps(lat, lon)
        val last = placesFetchedAt
        if (!loadingPlaces && (last == null || World.distanceMeters(last.first, last.second, lat, lon) > 500)) {
            loadingPlaces = true
            placesFetchedAt = lat to lon
            viewModelScope.launch {
                val ua = "MonsterQuest/0.1 (${getApplication<Application>().packageName})"
                shrines = withContext(Dispatchers.IO) { PlacesRepository.shrinesNear(lat, lon, ua) }
                loadingPlaces = false
            }
        }
    }

    /** Where you were when this spawn window began: new monsters appear in a ring around it. */
    private var spawnAnchor: Pair<Double, Double>? = null
    private var anchorWindow = -1L

    /**
     * Every wild monster around you right now, at its current (approaching)
     * position, plus monsters that followed you from walking.
     */
    fun spawnsNear(nowMs: Long): List<WildSpawn> {
        val (lat, lon) = location ?: return emptyList()
        val window = World.spawnWindow(nowMs)
        val anchor = spawnAnchor
        // A new window, or a big jump (a drive), starts a fresh ring around where you are.
        if (anchor == null || window != anchorWindow ||
            World.distanceMeters(anchor.first, anchor.second, lat, lon) > World.SPAWN_OUTER_M
        ) {
            spawnAnchor = lat to lon
            anchorWindow = window
        }
        val (aLat, aLon) = spawnAnchor ?: (lat to lon)
        val wild = World.spawnsNear(lat, lon, nowMs, state.hero.level, spawnConditions(nowMs), aLat, aLon)
            .filter { it.id !in state.usedSpawns }
        val guards = World.shrineSpawns(shrines, lat, lon, nowMs, state.hero.level, spawnConditions(nowMs))
            .filter { it.id !in state.usedSpawns }
        val followers = GameEngine.activeFollowers(state, nowMs).mapIndexed { i, f -> GameEngine.followerSpawn(f, lat, lon, i) }
        return followers + guards + wild
    }

    /**
     * The nearest landmark to a spot: a real named place (cafe, park, museum...)
     * within 250 m, or any shrine within 150 m. Returns it and how far it is from the spot.
     */
    fun landmarkNear(lat: Double, lon: Double): Pair<Shrine, Double>? {
        val withDistance = shrines.map { it to World.distanceMeters(lat, lon, it.lat, it.lon) }
        return withDistance.filter { (s, d) -> s.id.startsWith("osm:") && d <= 250 }.minByOrNull { it.second }
            ?: withDistance.filter { it.second <= 150 }.minByOrNull { it.second }
    }

    /** A distance in the player's chosen units. */
    private fun dist(meters: Double) = com.monsterquest.app.ui.formatDistance(meters, state.settings.imperialUnits)

    /** Monsters close enough to show on the map. */
    fun visibleSpawns(nowMs: Long): List<WildSpawn> =
        spawnsNear(nowMs).filter { (distanceTo(it.lat, it.lon) ?: Double.MAX_VALUE) <= World.REVEAL_M }

    /** Monsters sensed farther out (shown only in the tracker), nearest first. */
    fun trackedSpawns(nowMs: Long): List<WildSpawn> =
        spawnsNear(nowMs)
            .map { it to (distanceTo(it.lat, it.lon) ?: Double.MAX_VALUE) }
            .filter { (_, d) -> d > World.REVEAL_M && d <= World.TRACK_M }
            .sortedBy { it.second }
            .map { it.first }

    fun spawnConditions(nowMs: Long): SpawnConditions = SpawnConditions(
        hour = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).hour,
        activity = GameEngine.activityLevel(state, nowMs),
        lure = state.lureActive(nowMs),
    )

    fun useLure() = apply(GameEngine.useLure(state, System.currentTimeMillis()))

    // ---- battery saver -------------------------------------------------------

    /** The screen is blacked out while the game keeps tracking walking and spawns. */
    var batterySaver by mutableStateOf(false)

    /** The battery saver was turned on by turning the phone upside down (so turning it upright ends it). */
    var saverByFlip by mutableStateOf(false)

    /** The latest monster to appear nearby, shown on the battery saver screen. */
    var lastSpawnAlert by mutableStateOf<String?>(null)

    /** Spawns already announced, so each one buzzes only once. */
    private val announcedSpawns = mutableSetOf<String>()
    private var spawnsPrimed = false

    /**
     * Called with the current nearby spawns. New ones within [ALERT_RANGE_M]
     * are announced (the first call only records what's already there).
     * Returns the newly appeared spawns.
     */
    fun newSpawnsNearby(spawns: List<WildSpawn>): List<WildSpawn> {
        val close = spawns.filter { (distanceTo(it.lat, it.lon) ?: Double.MAX_VALUE) <= ALERT_RANGE_M }
        val fresh = close.filter { it.id !in announcedSpawns }
        announcedSpawns += close.map { it.id }
        if (!spawnsPrimed) {
            spawnsPrimed = true
            return emptyList()
        }
        (fresh.firstOrNull { it.shiny } ?: fresh.firstOrNull())?.let {
            lastSpawnAlert = if (it.shiny) "A shiny ${it.species.name} is in range!" else "A wild ${it.species.name} is in range!"
            if (it.shiny) message = lastSpawnAlert
        }
        return fresh
    }

    fun distanceTo(lat: Double, lon: Double): Double? =
        location?.let { World.distanceMeters(it.first, it.second, lat, lon) }

    fun visitShrine(shrine: Shrine) {
        val d = distanceTo(shrine.lat, shrine.lon)
        if (d == null || d > World.SHRINE_RANGE_M) {
            message = "Get closer to ${shrine.name} (within ${dist(World.SHRINE_RANGE_M)})."
            return
        }
        val u = GameEngine.visitShrine(state, shrine, System.currentTimeMillis())
        set(u.state)
        if (u.rewards.isEmpty()) {
            message = u.message
        } else {
            lootPopup = LootPopup(shrine.name, u.rewards)
            // Only the bag-full warning still needs a text message.
            if (u.message.contains("Bag full")) message = "Bag full: some loot was left behind."
        }
    }

    /** Loot just collected, shown briefly over the map. */
    var lootPopup by mutableStateOf<LootPopup?>(null)

    fun challengeBoss(shrine: Shrine) {
        val d = distanceTo(shrine.lat, shrine.lon)
        if (d == null || d > World.SHRINE_RANGE_M) {
            message = "Get closer to ${shrine.name} (within ${dist(World.SHRINE_RANGE_M)})."
            return
        }
        val (boss, mode) = GameEngine.bossEncounter(state, shrine, System.currentTimeMillis())
        if (boss == null) {
            message = mode
            return
        }
        val type = when (mode) {
            "silver" -> EncounterType.SILVER_BOSS
            "taming" -> EncounterType.BOSS_TAMING
            else -> EncounterType.BOSS_CHALLENGE
        }
        startBattle(boss, type, shrine.id)
    }

    fun battleSpawn(spawn: WildSpawn) {
        val d = distanceTo(spawn.lat, spawn.lon)
        if (d == null || d > World.SPAWN_RANGE_M) {
            message = "Get closer to the ${spawn.species.name} (within ${dist(World.SPAWN_RANGE_M)})."
            return
        }
        if (state.monsters.isEmpty()) {
            message = "You need a monster first."
            return
        }
        engage(spawn, ambush = false)
    }

    /** The monsters near this one that will fight alongside it. */
    fun packFor(spawn: WildSpawn, nowMs: Long = System.currentTimeMillis()): List<WildSpawn> =
        World.packFor(spawn, visibleSpawns(nowMs))

    /** Starts a fight with [spawn] and any pack mates; all of them are used up. */
    private fun engage(spawn: WildSpawn, ambush: Boolean) {
        val now = System.currentTimeMillis()
        val pack = packFor(spawn, now)
        // Mark them used now so fleeing can't be abused to re-roll the fight.
        var s = state
        for (sp in listOf(spawn) + pack) {
            s = if (sp.follower) GameEngine.removeFollower(s, sp.id) else GameEngine.markSpawnUsed(s, sp.id, now)
        }
        if (ambush) s = s.copy(lastAmbushMs = now)
        set(s)
        startBattle(World.spawnMonster(spawn), EncounterType.WILD, null, pack.map { World.spawnMonster(it) }, ambush)
    }

    /**
     * An aggressive monster that has reached you attacks first (at most every
     * [AMBUSH_COOLDOWN_MS], and only while the map is open and you're not busy).
     * Returns true if an ambush started.
     */
    fun checkAmbush(spawns: List<WildSpawn>, nowMs: Long): Boolean {
        if (!state.settings.ambushes || battle != null || batterySaver || state.focus != null) return false
        // Give the player a moment to look around after opening the map.
        if (nowMs - mapOpenedAtMs < AMBUSH_GRACE_MS) return false
        if (nowMs - state.lastAmbushMs < AMBUSH_COOLDOWN_MS || state.monsters.isEmpty()) return false
        val hour = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).hour
        val attacker = spawns.firstOrNull {
            World.isAggressive(it, hour) && (distanceTo(it.lat, it.lon) ?: Double.MAX_VALUE) <= World.AMBUSH_M
        } ?: return false
        engage(attacker, ambush = true)
        return true
    }

    /** Test hook: start a fight against [enemy] and its [pack]. */
    @VisibleForTesting
    fun debugStartBattle(enemy: Monster, pack: List<Monster> = emptyList(), ambush: Boolean = false) =
        startBattle(enemy, EncounterType.WILD, null, pack, ambush)

    /** When the map was last opened (ambushes wait [AMBUSH_GRACE_MS] after that). */
    private var mapOpenedAtMs = 0L

    fun onMapOpened() {
        mapOpenedAtMs = System.currentTimeMillis()
    }

    fun selectTarget(key: String) {
        val b = activeBattle ?: return
        b.selectTarget(key)
        battle = battle?.let { BattleView.of(b, it.resultText, it.nextOpponent) }
    }

    fun hunt() {
        if (state.monsters.isEmpty()) {
            message = "You need a monster first."
            return
        }
        val (s, enemy) = GameEngine.startHunt(state)
        if (enemy == null) {
            message = "No hunt tokens. Complete quests or visit shrines to earn more."
            return
        }
        set(s)
        startBattle(enemy, EncounterType.WILD, null)
    }

    // ---- villains -----------------------------------------------------------

    /** A fight against a villain's team in progress: which monster we're on. */
    private data class VillainRun(val camp: VillainCamp, val index: Int)
    private var villainRun: VillainRun? = null

    fun densNear(nowMs: Long): List<com.monsterquest.core.Den> {
        val (lat, lon) = location ?: return emptyList()
        return com.monsterquest.core.Dens.near(state, lat, lon, nowMs)
    }

    fun challengeDen(den: com.monsterquest.core.Den) {
        val d = distanceTo(den.lat, den.lon)
        if (d == null || d > com.monsterquest.core.Dens.RANGE_M) {
            message = "Get closer to the ${den.name} (within ${dist(com.monsterquest.core.Dens.RANGE_M)})."
            return
        }
        if (state.monsters.isEmpty()) {
            message = "You need a monster first."
            return
        }
        startBattle(den.monster, EncounterType.DEN, den.id)
    }

    fun denThrow() = apply(com.monsterquest.core.Dens.attempt(state))
    fun denGiveUp() = apply(com.monsterquest.core.Dens.giveUp(state))

    fun villainsNear(nowMs: Long): List<VillainCamp> {
        val (lat, lon) = location ?: return emptyList()
        return Villains.near(state, lat, lon, nowMs)
    }

    fun challengeVillain(camp: VillainCamp) {
        val d = distanceTo(camp.lat, camp.lon)
        if (d == null || d > Villains.RANGE_M) {
            message = "Get closer to ${camp.leader} (within ${dist(Villains.RANGE_M)})."
            return
        }
        villainRun = VillainRun(camp, 0)
        startBattle(camp.team.first(), EncounterType.VILLAIN, null)
    }

    /** After beating one of a villain's monsters, face the next. */
    fun nextVillainMonster() {
        val run = villainRun ?: return
        val next = run.copy(index = run.index + 1)
        val enemy = run.camp.team.getOrNull(next.index) ?: return
        villainRun = next
        startBattle(enemy, EncounterType.VILLAIN, null)
    }

    fun redeemVillainMonster(index: Int) = apply(Villains.redeem(state, index))
    fun dismissSpoils() = set(Villains.dismissSpoils(state))

    // ---- battle ------------------------------------------------------------

    private fun startBattle(
        enemy: Monster,
        type: EncounterType,
        shrineId: String?,
        pack: List<Monster> = emptyList(),
        ambush: Boolean = false,
    ) {
        var s = state
        for (m in listOf(enemy) + pack) s = GameEngine.markSeen(s, m.speciesId)
        set(s)
        val b = GameEngine.newBattle(state, enemy, type, shrineId, pack = pack, ambush = ambush)
        activeBattle = b
        battle = BattleView.of(b, null)
    }

    fun battleAction(action: BattleAction) {
        val b = activeBattle ?: return
        if (b.outcome != BattleOutcome.ONGOING) return
        b.act(action)
        var resultText: String? = null
        var nextOpponent: String? = null
        var levelUps: List<com.monsterquest.core.LevelUp>? = null
        if (b.outcome != BattleOutcome.ONGOING) {
            val u = GameEngine.applyBattleResult(state, b.result(), System.currentTimeMillis())
            set(u.state)
            resultText = u.message
            levelUps = u.levelUps
            val run = villainRun
            if (run != null) {
                val next = run.camp.team.getOrNull(run.index + 1)
                if (b.outcome == BattleOutcome.WON && next != null) {
                    nextOpponent = next.displayName
                } else if (b.outcome == BattleOutcome.WON) {
                    val reward = Villains.reward(state, run.camp, System.currentTimeMillis())
                    set(reward.state)
                    resultText += " " + reward.message
                    villainRun = null
                } else {
                    resultText += " ${run.camp.leader}'s crew drives you off."
                    villainRun = null
                }
            }
        }
        battle = BattleView.of(
            b, resultText ?: battle?.resultText, nextOpponent ?: battle?.nextOpponent,
            levelUps ?: battle?.levelUps.orEmpty(),
        )
    }

    fun closeBattle() {
        lastBattleEndMs = System.currentTimeMillis()
        activeBattle = null
        battle = null
        villainRun = null
    }
}
