package dev.matejgroombridge.triolingo.domain

import dev.matejgroombridge.triolingo.data.AppState
import dev.matejgroombridge.triolingo.data.SrsCard
import dev.matejgroombridge.triolingo.data.VOCAB
import dev.matejgroombridge.triolingo.data.VocabItem
import java.time.Instant
import java.time.ZoneOffset

const val DAY_MS = 86_400_000L

// The web app keyed days by Date.toISOString() — a UTC date — so we keep the
// exact same convention for identical streak/goal behaviour.
fun todayKey(now: Long = System.currentTimeMillis()): String =
    Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate().toString()

fun AppState.dailyXP(): Int = daily[todayKey()] ?: 0

fun AppState.streak(): Int {
    var n = 0
    var t = System.currentTimeMillis()
    if (activeDays[todayKey(t)] != true) t -= DAY_MS // today not yet active: count from yesterday
    while (true) {
        if (activeDays[todayKey(t)] == true) { n++; t -= DAY_MS } else break
    }
    return n
}

data class LevelInfo(val lvl: Int, val pct: Double, val toNext: Int)

/** Level n starts at 60·n·(n−1) XP → fast early levels, slowing curve. */
fun AppState.levelInfo(): LevelInfo {
    var lvl = 1
    while (60 * (lvl + 1) * lvl <= xp) lvl++
    val base = 60 * lvl * (lvl - 1)
    val next = 60 * (lvl + 1) * lvl
    return LevelInfo(lvl, (xp - base).toDouble() / (next - base), next - xp)
}

fun AppState.learnedIds(): List<String> = cards.keys.toList()

fun AppState.dueIds(now: Long = System.currentTimeMillis()): List<String> =
    cards.entries.filter { it.value.due <= now }.map { it.key }

fun AppState.unlearnedItems(): List<VocabItem> = VOCAB.filter { it.id !in cards }

// ─── SRS (SM-2 lite) ───

/** Freshly learned → resurface in 30 min (same-day reinforcement). */
fun AppState.srsInit(id: String): AppState = copy(
    cards = cards + (id to SrsCard(reps = 0, ef = 2.3, ivl = 0.0, due = System.currentTimeMillis() + 30 * 60_000L, lapses = 0))
)

fun AppState.srsAnswer(id: String, correct: Boolean): AppState {
    val c = cards[id] ?: return this
    val updated = if (correct) {
        val ivl = if (c.ivl == 0.0) 1.0 else minOf(180.0, c.ivl * c.ef) // graduate → 1 day, then grow
        c.copy(reps = c.reps + 1, ivl = ivl, due = System.currentTimeMillis() + (ivl * DAY_MS).toLong())
    } else {
        c.copy(
            lapses = c.lapses + 1,
            ef = maxOf(1.3, c.ef - 0.2),
            ivl = 0.0,
            due = System.currentTimeMillis() + 10 * 60_000L, // come back in 10 min
        )
    }
    return copy(cards = cards + (id to updated))
}
