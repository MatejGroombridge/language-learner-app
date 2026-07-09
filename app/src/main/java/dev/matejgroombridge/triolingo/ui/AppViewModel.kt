package dev.matejgroombridge.triolingo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.matejgroombridge.triolingo.audio.AudioService
import dev.matejgroombridge.triolingo.audio.SoundFx
import dev.matejgroombridge.triolingo.data.AppState
import dev.matejgroombridge.triolingo.data.StateStore
import dev.matejgroombridge.triolingo.data.VOCAB_BY_ID
import dev.matejgroombridge.triolingo.data.VocabItem
import dev.matejgroombridge.triolingo.domain.Kind
import dev.matejgroombridge.triolingo.domain.Session
import dev.matejgroombridge.triolingo.domain.Step
import dev.matejgroombridge.triolingo.domain.buildDrillSession
import dev.matejgroombridge.triolingo.domain.buildLearnSession
import dev.matejgroombridge.triolingo.domain.buildReviewSession
import dev.matejgroombridge.triolingo.domain.dailyXP
import dev.matejgroombridge.triolingo.domain.distractors
import dev.matejgroombridge.triolingo.domain.dueIds
import dev.matejgroombridge.triolingo.domain.firstTone
import dev.matejgroombridge.triolingo.domain.levelInfo
import dev.matejgroombridge.triolingo.domain.srsAnswer
import dev.matejgroombridge.triolingo.domain.srsInit
import dev.matejgroombridge.triolingo.domain.stripTones
import dev.matejgroombridge.triolingo.domain.todayKey
import dev.matejgroombridge.triolingo.domain.unlearnedItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

enum class Screen { Home, Session, Summary }

/** Everything a step's UI needs, precomputed so recomposition is stable.
 *  [ord] is the step's position in the session — it keeps two visually
 *  identical consecutive steps (a requeued retry) distinct. */
sealed interface StepUi {
    val ord: Int
    val item: VocabItem

    data class Intro(override val ord: Int, override val item: VocabItem) : StepUi
    data class Choice(override val ord: Int, override val item: VocabItem, val en: Boolean, val options: List<VocabItem>) : StepUi
    data class Listen(override val ord: Int, override val item: VocabItem, val options: List<VocabItem>) : StepUi
    data class ToneQ(override val ord: Int, override val item: VocabItem, val tone: Int, val bare: String, val multi: Boolean) : StepUi
    data class SpeakQ(override val ord: Int, override val item: VocabItem) : StepUi
}

sealed interface FbDetail {
    /** `汉字 pinyin = **en**`, optionally prefixed ("You heard: "). */
    data class Answer(val item: VocabItem, val prefix: String? = null) : FbDetail
    /** `汉字 pinyin — tone N` */
    data class ToneAnswer(val item: VocabItem, val tone: Int) : FbDetail
    /** `Prefix: 汉字 pinyin` */
    data class Shadow(val item: VocabItem, val prefix: String) : FbDetail
}

data class Feedback(val ok: Boolean, val headline: String, val detail: FbDetail?, val comboLine: String?)

data class SummaryData(val mode: String, val correct: Int, val wrong: Int, val xpEarned: Int)

private val PRAISE = listOf(
    "Nice!", "对了! Correct!", "Beautiful!", "You're on fire!",
    "太好了! Perfect!", "Locked in! 🔒", "That's fluent energy!", "很好! Great!",
)
private val OOPS = listOf("Not quite —", "Almost!", "Good try —", "Brains grow on mistakes:")

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StateStore(app)

    var state by mutableStateOf(store.load())
        private set

    val audio = AudioService(app) { state.settings.rate }
    val sfx = SoundFx(viewModelScope) { state.settings.sound }

    var screen by mutableStateOf(Screen.Home)
        private set
    var session: Session? = null
        private set
    var stepUi by mutableStateOf<StepUi?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var combo by mutableStateOf(0)
        private set
    var feedback by mutableStateOf<Feedback?>(null)
        private set
    var summary by mutableStateOf<SummaryData?>(null)
        private set

    // one-shot overlays
    var confettiTick by mutableStateOf(0)
        private set
    var levelUpLevel by mutableStateOf<Int?>(null)
        private set
    var listenPlaying by mutableStateOf(false)
        private set

    var showSettings by mutableStateOf(false)
    var showMethod by mutableStateOf(false)

    private var autoplayJob: Job? = null

    private fun persist(next: AppState) {
        state = next
        store.save(next)
    }

    // ─── Gamification ───

    fun comboMult(): Double = 1 + min(combo, 10) * 0.1

    fun comboMultLabel(): String = String.format(Locale.US, "%.1f", comboMult())

    private fun awardXP(base: Int): Int {
        val amt = (base * comboMult()).roundToInt()
        val before = state.levelInfo().lvl
        val k = todayKey()
        persist(state.copy(xp = state.xp + amt, daily = state.daily + (k to (state.daily[k] ?: 0) + amt)))
        val after = state.levelInfo().lvl
        if (after > before) viewModelScope.launch {
            delay(500)
            sfx.levelup()
            confettiTick++
            levelUpLevel = after
            delay(2600)
            if (levelUpLevel == after) levelUpLevel = null
        }
        return amt
    }

    // ─── Session flow ───

    fun startSession(mode: String) {
        val s = when (mode) {
            "learn" -> buildLearnSession(state)
            "review" -> buildReviewSession(state)
            "listen" -> buildDrillSession(state, Kind.Listen)
            "speak" -> buildDrillSession(state, Kind.Speak)
            "tone" -> buildDrillSession(state, Kind.Tone)
            else -> null
        }
        if (s == null) { goHome(); return }
        session = s
        combo = 0
        feedback = null
        screen = Screen.Session
        renderStep()
    }

    private fun renderStep() {
        val s = session ?: return
        val st = s.steps[s.i]
        val item = VOCAB_BY_ID.getValue(st.id)
        progress = s.i.toFloat() / s.steps.size
        listenPlaying = false
        stepUi = when (st.kind) {
            Kind.Intro -> StepUi.Intro(s.i, item)
            Kind.ChoiceEn -> StepUi.Choice(s.i, item, en = true, options = (listOf(item) + distractors(item, 3) { it.en }).shuffled())
            Kind.ChoiceZh -> StepUi.Choice(s.i, item, en = false, options = (listOf(item) + distractors(item, 3) { it.pinyin }).shuffled())
            Kind.Listen -> StepUi.Listen(s.i, item, options = (listOf(item) + distractors(item, 3) { it.en }).shuffled())
            Kind.Tone -> StepUi.ToneQ(
                s.i, item,
                tone = firstTone(item),
                bare = stripTones(item.pinyin.split(" ")[0]),
                multi = " " in item.pinyin,
            )
            Kind.Speak -> StepUi.SpeakQ(s.i, item)
        }
        // Listening-first: audio auto-plays shortly after the card appears.
        autoplayJob?.cancel()
        val delayMs = when (st.kind) {
            Kind.Intro, Kind.Listen, Kind.Tone -> 350L
            Kind.ChoiceEn -> 300L
            else -> null
        }
        if (delayMs != null) autoplayJob = viewModelScope.launch {
            delay(delayMs)
            if (st.kind == Kind.Listen) playListen(slow = false) else audio.speak(item.hanzi)
        }
    }

    fun playListen(slow: Boolean) {
        val item = stepUi?.item ?: return
        listenPlaying = true
        audio.speak(item.hanzi, slow) { listenPlaying = false }
    }

    private fun stepDone(correct: Boolean, skipSRS: Boolean = false) {
        val s = session ?: return
        val st = s.steps[s.i]
        val startXP = state.dailyXP()
        if (correct) {
            s.correct++
            combo++
            sfx.correct()
            s.xpEarned += awardXP(if (st.kind == Kind.Intro) 2 else 10)
        } else {
            s.wrong++
            combo = 0
            sfx.wrong()
            // requeue: test again a few steps later (mastery loop, capped at 2 retries)
            if ((s.retries[st.id] ?: 0) < 2) {
                s.retries[st.id] = (s.retries[st.id] ?: 0) + 1
                val requeue = Step(st.id, if (st.kind == Kind.Speak) Kind.ChoiceZh else st.kind, requeued = true)
                s.steps.add(min(s.i + 3, s.steps.size), requeue)
            }
        }
        // SRS: only the FIRST encounter of an item in a session counts
        if (!skipSRS && st.kind != Kind.Intro && !st.requeued && st.id !in s.graded) {
            s.graded.add(st.id)
            if (st.id in state.cards) persist(state.srsAnswer(st.id, correct))
        }
        // crossing the daily goal — celebrate
        if (startXP < state.dailyGoal && state.dailyXP() >= state.dailyGoal) {
            viewModelScope.launch { delay(300); confettiTick++ }
        }
        store.save(state)
    }

    private fun showFeedback(ok: Boolean, detail: FbDetail?, item: VocabItem?) {
        val comboLine = if (ok && combo > 2) "🔥 $combo in a row — ${comboMultLabel()}× XP" else null
        feedback = Feedback(ok, if (ok) PRAISE.random() else OOPS.random(), detail, comboLine)
        if (!ok && item != null) audio.speak(item.hanzi) // hear the right answer — corrective input
    }

    fun continueFromFeedback() {
        feedback = null
        nextStep()
    }

    private fun nextStep() {
        val s = session ?: return
        s.i++
        if (s.i >= s.steps.size) finishSession() else renderStep()
    }

    private fun finishSession() {
        val s = session ?: return
        autoplayJob?.cancel()
        // mark new items as learned (enter SRS)
        var st = state
        for (id in s.newIds) if (id !in st.cards) st = st.srsInit(id)
        persist(st)
        s.xpEarned += awardXP(25)
        persist(state.copy(activeDays = state.activeDays + (todayKey() to true), sessions = state.sessions + 1))
        sfx.fanfare()
        confettiTick++
        summary = SummaryData(s.mode, s.correct, s.wrong, s.xpEarned)
        feedback = null
        screen = Screen.Summary
    }

    fun quitSession() {
        audio.stop()
        autoplayJob?.cancel()
        val s = session ?: return
        if (s.i > 2) finishSession() else goHome()
    }

    fun goHome() {
        autoplayJob?.cancel()
        audio.stop()
        session = null
        stepUi = null
        feedback = null
        summary = null
        screen = Screen.Home
    }

    /** What the summary's big button should launch next. */
    fun nextRecommendedMode(): String = when {
        state.dueIds().isNotEmpty() -> "review"
        state.unlearnedItems().isNotEmpty() -> "learn"
        else -> "listen"
    }

    // ─── Per-exercise answers ───

    fun introDone() {
        stepDone(true, skipSRS = true)
        nextStep()
    }

    fun answerChoice(chosen: VocabItem) {
        val ui = stepUi as? StepUi.Choice ?: return
        val ok = chosen.id == ui.item.id
        stepDone(ok)
        showFeedback(ok, if (ok) null else FbDetail.Answer(ui.item), ui.item)
    }

    fun answerListen(chosen: VocabItem) {
        val ui = stepUi as? StepUi.Listen ?: return
        val ok = chosen.id == ui.item.id
        stepDone(ok)
        showFeedback(ok, if (ok) null else FbDetail.Answer(ui.item, prefix = "You heard: "), ui.item)
    }

    fun answerTone(n: Int) {
        val ui = stepUi as? StepUi.ToneQ ?: return
        val ok = n == ui.tone
        stepDone(ok, skipSRS = true)
        showFeedback(ok, if (ok) null else FbDetail.ToneAnswer(ui.item, ui.tone), ui.item)
    }

    /** Settle a speaking step. [selfGraded] switches the wrong-answer wording
     *  between the mic flow and the honest self-grading fallback. */
    fun settleSpeak(ok: Boolean, selfGraded: Boolean = false) {
        val ui = stepUi as? StepUi.SpeakQ ?: return
        stepDone(ok)
        val detail = if (ok) null else FbDetail.Shadow(ui.item, if (selfGraded) "Model answer:" else "Try shadowing it:")
        showFeedback(ok, detail, ui.item)
    }

    // ─── Settings ───

    fun setShowHanzi(b: Boolean) = persist(state.copy(settings = state.settings.copy(showHanzi = b)))
    fun setSound(b: Boolean) = persist(state.copy(settings = state.settings.copy(sound = b)))
    fun setRate(r: Float) = persist(state.copy(settings = state.settings.copy(rate = r)))
    fun previewVoice() = audio.speak("你好")
    fun setDailyGoal(g: Int) = persist(state.copy(dailyGoal = g))
    fun resetProgress() {
        persist(AppState())
        showSettings = false
        goHome()
    }

    override fun onCleared() {
        audio.release()
    }
}
