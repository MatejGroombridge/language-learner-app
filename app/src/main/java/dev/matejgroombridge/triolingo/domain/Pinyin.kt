package dev.matejgroombridge.triolingo.domain

import dev.matejgroombridge.triolingo.data.VocabItem
import java.text.Normalizer

// ─── Pinyin helpers ─── ported from app.js

private val TONE_MARKS = mapOf(
    1 to "āēīōūǖ", 2 to "áéíóúǘ", 3 to "ǎěǐǒǔǚ", 4 to "àèìòùǜ",
)

fun toneOfSyllable(syl: String): Int {
    for (t in 1..4) for (ch in TONE_MARKS.getValue(t)) if (ch in syl) return t
    return 5 // neutral
}

fun stripTones(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace("ü", "u")

fun firstTone(item: VocabItem): Int = toneOfSyllable(item.pinyin.split(" ")[0])

fun cjkOnly(s: String): String = s.filter { it in '一'..'鿿' }

/** Per-character hit ratio on CJK — how much of [target] appears in [said]. */
fun similarity(target: String, said: String): Double {
    val t = cjkOnly(target)
    val s = cjkOnly(said)
    if (t.isEmpty() || s.isEmpty()) return 0.0
    val bag = mutableMapOf<Char, Int>()
    for (ch in s) bag[ch] = (bag[ch] ?: 0) + 1
    var hits = 0
    for (ch in t) if ((bag[ch] ?: 0) > 0) { hits++; bag[ch] = bag.getValue(ch) - 1 }
    return hits.toDouble() / t.length
}
