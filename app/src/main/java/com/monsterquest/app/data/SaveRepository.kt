package com.monsterquest.app.data

import android.content.Context
import android.util.Log
import com.monsterquest.core.GameState
import java.io.File

/** Saves the whole game as one JSON file in app-private storage. */
class SaveRepository(context: Context) {
    private val file = File(context.filesDir, "save.json")
    private val tmp = File(context.filesDir, "save.json.tmp")

    fun load(): GameState = try {
        if (file.exists()) GameState.fromJson(file.readText()) else GameState()
    } catch (e: Exception) {
        Log.e("MonsterQuest", "Failed to load save, backing it up and starting fresh", e)
        file.copyTo(File(file.parentFile, "save.broken.json"), overwrite = true)
        GameState()
    }

    fun save(state: GameState) {
        // Write to a temp file first so a crash mid-write can't corrupt the save.
        tmp.writeText(state.toJson())
        tmp.renameTo(file)
    }
}
