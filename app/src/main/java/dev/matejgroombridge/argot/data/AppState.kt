package dev.matejgroombridge.argot.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Whole-app persisted state — a direct port of the web app's localStorage blob
 * (key "liuli-v1"). Kept as one JSON document in SharedPreferences so load is
 * synchronous at startup, exactly like localStorage was.
 */
@Serializable
data class SrsCard(
    val reps: Int = 0,
    val ef: Double = 2.3,
    val ivl: Double = 0.0,   // interval in days; 0 = learning/relearning
    val due: Long = 0,       // epoch millis
    val lapses: Int = 0,
)

@Serializable
data class Settings(
    val showHanzi: Boolean = true,
    val rate: Float = 0.9f,
    val sound: Boolean = true,
)

@Serializable
data class AppState(
    val xp: Int = 0,
    val dailyGoal: Int = 100,
    val daily: Map<String, Int> = emptyMap(),          // "2026-07-09" -> xp
    val activeDays: Map<String, Boolean> = emptyMap(), // "2026-07-09" -> true
    val cards: Map<String, SrsCard> = emptyMap(),      // vocab id -> SRS card
    val settings: Settings = Settings(),
    val sessions: Int = 0,
)

class StateStore(context: Context) {
    private val prefs = context.getSharedPreferences("liuli", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppState = try {
        prefs.getString(KEY, null)?.let { json.decodeFromString<AppState>(it) } ?: AppState()
    } catch (_: Exception) {
        AppState()
    }

    fun save(state: AppState) {
        prefs.edit().putString(KEY, json.encodeToString(state)).apply()
    }

    private companion object {
        const val KEY = "liuli-v1"
    }
}
