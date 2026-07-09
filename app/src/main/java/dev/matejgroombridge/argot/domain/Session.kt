package dev.matejgroombridge.argot.domain

import dev.matejgroombridge.argot.data.AppState
import dev.matejgroombridge.argot.data.VOCAB
import dev.matejgroombridge.argot.data.VOCAB_BY_ID
import dev.matejgroombridge.argot.data.VocabItem
import kotlin.random.Random

// ─── Session engine ─── ported from app.js

enum class Kind { Intro, ChoiceEn, ChoiceZh, Listen, Speak, Tone }

data class Step(val id: String, val kind: Kind, val requeued: Boolean = false)

/** Mutable session, mirroring the JS `session` object. */
class Session(
    val steps: MutableList<Step>,
    val mode: String, // learn | review | practice | listen | speak | tone
    val newIds: List<String>,
) {
    var i = 0
    var correct = 0
    var wrong = 0
    var xpEarned = 0
    val graded = mutableSetOf<String>()
    val retries = mutableMapOf<String, Int>()
}

fun <T> sample(list: List<T>, n: Int): List<T> = list.shuffled().take(n)

fun distractors(item: VocabItem, n: Int, field: (VocabItem) -> String): List<VocabItem> {
    val sameUnit = VOCAB.filter { it.id != item.id && it.unit == item.unit && field(it) != field(item) }
    val others = VOCAB.filter { it.id != item.id && it.unit != item.unit && field(it) != field(item) }
    val pool = sample(sameUnit, minOf(n - 1, sameUnit.size)) + sample(others, n)
    val seen = mutableSetOf(field(item))
    val out = mutableListOf<VocabItem>()
    for (d in pool) {
        if (out.size >= n) break
        if (field(d) in seen) continue
        seen.add(field(d)); out.add(d)
    }
    return out
}

fun buildLearnSession(state: AppState): Session? {
    val fresh = state.unlearnedItems().take(4)
    if (fresh.isEmpty()) return null
    val steps = mutableListOf<Step>()
    // intro + immediate test (testing effect), then interleaved rounds
    fresh.forEach { steps.add(Step(it.id, Kind.Intro)); steps.add(Step(it.id, Kind.ChoiceEn)) }
    for (kind in listOf(Kind.Listen, Kind.ChoiceZh, Kind.Speak)) {
        for (it in fresh.shuffled()) steps.add(Step(it.id, kind))
    }
    return Session(steps, "learn", fresh.map { it.id })
}

fun buildReviewSession(state: AppState): Session? {
    val due = state.dueIds()
    val practice: Boolean
    val ids: List<String>
    if (due.isNotEmpty()) { ids = sample(due, 12); practice = false }
    else { ids = sample(state.learnedIds(), 12); practice = true }
    if (ids.isEmpty()) return null
    val steps = ids.map { id ->
        val c = state.cards.getValue(id)
        val kind = when {
            c.ivl == 0.0 -> Kind.ChoiceEn
            c.ivl < 4 -> if (Random.nextDouble() < 0.5) Kind.Listen else Kind.ChoiceZh
            else -> if (Random.nextDouble() < 0.5) Kind.Speak else Kind.Listen
        }
        Step(id, kind)
    }
    return Session(steps.shuffled().toMutableList(), if (practice) "practice" else "review", emptyList())
}

fun buildDrillSession(state: AppState, kind: Kind): Session? {
    var pool = state.learnedIds().mapNotNull { VOCAB_BY_ID[it] }
    if (pool.size < 6) pool = VOCAB.filter { it.unit <= 2 }
    if (kind == Kind.Tone) {
        pool = pool.filter { v ->
            val syls = v.pinyin.split(" ")
            val t1 = toneOfSyllable(syls[0])
            when {
                t1 == 5 -> false
                t1 == 3 && syls.size > 1 && toneOfSyllable(syls[1]) == 3 -> false // avoid sandhi confusion
                else -> syls.size <= 2
            }
        }
    }
    if (kind == Kind.Speak) pool = pool.filter { cjkOnly(it.hanzi).isNotEmpty() }
    val ids = sample(pool, minOf(10, pool.size)).map { it.id }
    if (ids.isEmpty()) return null
    val mode = when (kind) { Kind.Listen -> "listen"; Kind.Speak -> "speak"; else -> "tone" }
    return Session(ids.map { Step(it, kind) }.toMutableList(), mode, emptyList())
}
