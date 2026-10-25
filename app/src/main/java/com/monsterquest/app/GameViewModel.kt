package com.monsterquest.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsterquest.app.data.CalendarReader
import com.monsterquest.app.data.PlacesRepository
import com.monsterquest.app.data.SaveRepository
import com.monsterquest.core.Battle
import com.monsterquest.core.BattleAction
import com.monsterquest.core.BattleOutcome
import com.monsterquest.core.Difficulty
import com.monsterquest.core.EncounterType
import com.monsterquest.core.GameEngine
import com.monsterquest.core.GameState
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.Item
import com.monsterquest.core.Monster
import com.monsterquest.core.Shrine
import com.monsterquest.core.Update
import com.monsterquest.core.WildSpawn
import com.monsterquest.core.World
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var syncingCalendar by mutableStateOf(false)
        private set

    // Saves happen in order on a background thread; rapid changes are conflated.
    private val pendingSave = MutableStateFlow(state)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            pendingSave.drop(1).collect { repo.save(it) }
        }
    }

    private fun set(s: GameState) {
        state = s
        pendingSave.value = s
    }

    private fun apply(u: Update) {
        set(u.state)
        message = u.message
    }

    fun clearMessage() {
        message = null
    }

    fun toast(text: String) {
        message = text
    }

    // ---- setup / hero ------------------------------------------------------

    fun chooseStarter(speciesId: String) = apply(GameEngine.chooseStarter(state, speciesId))
    fun allocate(attr: HeroAttribute) = apply(GameEngine.allocatePoint(state, attr))
    fun upgrade(bag: Boolean) = apply(GameEngine.applyUpgrade(state, bag))
    fun renameHero(name: String) {
        val n = name.trim().take(16)
        if (n.isNotEmpty()) set(state.copy(hero = state.hero.copy(name = n)))
    }

    // ---- quests ------------------------------------------------------------

    fun addTask(title: String, difficulty: Difficulty) = apply(GameEngine.addTask(state, title, difficulty))
    fun deleteTask(id: String) = apply(GameEngine.deleteTask(state, id))
    fun completeTask(id: String) = apply(GameEngine.completeTask(state, id, System.currentTimeMillis()))

    fun syncCalendar(quiet: Boolean = false) {
        if (syncingCalendar) return
        syncingCalendar = true
        viewModelScope.launch {
            try {
                val events = withContext(Dispatchers.IO) { CalendarReader.read(getApplication()) }
                val u = GameEngine.syncCalendar(state, events)
                if (quiet) set(u.state) else apply(u)
            } catch (e: SecurityException) {
                if (!quiet) message = "Calendar permission is needed to sync."
            } catch (e: Exception) {
                if (!quiet) message = "Calendar sync failed: ${e.message}"
            } finally {
                syncingCalendar = false
            }
        }
    }

    // ---- monsters & items --------------------------------------------------

    fun toggleParty(uid: String) = apply(GameEngine.toggleParty(state, uid))
    fun release(uid: String) = apply(GameEngine.release(state, uid))
    fun rename(uid: String, name: String) = apply(GameEngine.rename(state, uid, name))
    fun feedTreat(uid: String) = apply(GameEngine.feedTreat(state, uid))
    fun buy(item: Item) = apply(GameEngine.buy(state, item))
    fun discard(item: Item) = apply(GameEngine.discard(state, item))

    // ---- map ---------------------------------------------------------------

    fun onLocation(lat: Double, lon: Double) {
        location = lat to lon
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

    fun spawnsNear(nowMs: Long): List<WildSpawn> {
        val (lat, lon) = location ?: return emptyList()
        return World.spawnsNear(lat, lon, nowMs, state.hero.level).filter { it.id !in state.usedSpawns }
    }

    fun distanceTo(lat: Double, lon: Double): Double? =
        location?.let { World.distanceMeters(it.first, it.second, lat, lon) }

    fun visitShrine(shrine: Shrine) {
        val d = distanceTo(shrine.lat, shrine.lon)
        if (d == null || d > World.SHRINE_RANGE_M) {
            message = "Get closer to ${shrine.name} (within ${World.SHRINE_RANGE_M.toInt()} m)."
            return
        }
        apply(GameEngine.visitShrine(state, shrine, System.currentTimeMillis()))
    }

    fun challengeBoss(shrine: Shrine) {
        val d = distanceTo(shrine.lat, shrine.lon)
        if (d == null || d > World.SHRINE_RANGE_M) {
            message = "Get closer to ${shrine.name} (within ${World.SHRINE_RANGE_M.toInt()} m)."
            return
        }
        val (boss, mode) = GameEngine.bossEncounter(state, shrine, System.currentTimeMillis())
        if (boss == null) {
            message = mode
            return
        }
        val type = if (mode == "taming") EncounterType.BOSS_TAMING else EncounterType.BOSS_CHALLENGE
        startBattle(boss, type, shrine.id)
    }

    fun battleSpawn(spawn: WildSpawn) {
        val d = distanceTo(spawn.lat, spawn.lon)
        if (d == null || d > World.SPAWN_RANGE_M) {
            message = "Get closer to the ${spawn.species.name} (within ${World.SPAWN_RANGE_M.toInt()} m)."
            return
        }
        if (state.monsters.isEmpty()) {
            message = "You need a monster first."
            return
        }
        // Mark it used now so fleeing can't be abused to re-roll the fight.
        set(GameEngine.markSpawnUsed(state, spawn.id, System.currentTimeMillis()))
        startBattle(World.spawnMonster(spawn), EncounterType.WILD, null)
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

    // ---- battle ------------------------------------------------------------

    private fun startBattle(enemy: Monster, type: EncounterType, shrineId: String?) {
        val b = GameEngine.newBattle(state, enemy, type, shrineId)
        activeBattle = b
        battle = BattleView.of(b, null)
    }

    fun battleAction(action: BattleAction) {
        val b = activeBattle ?: return
        if (b.outcome != BattleOutcome.ONGOING) return
        b.act(action)
        var resultText: String? = null
        if (b.outcome != BattleOutcome.ONGOING) {
            val u = GameEngine.applyBattleResult(state, b.result(), System.currentTimeMillis())
            set(u.state)
            resultText = u.message
        }
        battle = BattleView.of(b, resultText ?: battle?.resultText)
    }

    fun closeBattle() {
        activeBattle = null
        battle = null
    }
}
