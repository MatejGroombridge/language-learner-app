package dev.matejgroombridge.triolingo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.matejgroombridge.triolingo.data.VocabItem
import dev.matejgroombridge.triolingo.domain.cjkOnly
import dev.matejgroombridge.triolingo.domain.similarity
import dev.matejgroombridge.triolingo.speech.ChineseRecognizer
import dev.matejgroombridge.triolingo.ui.AppViewModel
import dev.matejgroombridge.triolingo.ui.FbDetail
import dev.matejgroombridge.triolingo.ui.StepUi
import dev.matejgroombridge.triolingo.ui.components.BtnStyle
import dev.matejgroombridge.triolingo.ui.components.Chunky
import dev.matejgroombridge.triolingo.ui.components.ChunkyButton
import dev.matejgroombridge.triolingo.ui.components.IconBubble
import dev.matejgroombridge.triolingo.ui.components.triolingoShadow
import dev.matejgroombridge.triolingo.ui.theme.LocalTriolingo

@Composable
fun SessionScreen(vm: AppViewModel) {
    val p = LocalTriolingo.current
    val stepUi = vm.stepUi ?: return

    BackHandler { vm.quitSession() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 140.dp),
        ) {
            // ── Session top bar: quit ✕, progress, combo ──
            Row(
                Modifier.fillMaxWidth().padding(bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBubble("✕", size = 38.dp, fontSize = 16) { vm.quitSession() }
                val animPct by animateFloatAsState(vm.progress, tween(400), label = "pbar")
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(99.dp)).background(p.bgSoft)) {
                    if (animPct > 0f) Box(
                        Modifier
                            .fillMaxWidth(animPct)
                            .height(14.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Brush.horizontalGradient(listOf(p.gold, p.red)))
                    )
                }
                Text(
                    if (vm.combo > 1) "🔥${vm.combo}" else "",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.red),
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End,
                )
            }

            PromptKind(
                when (stepUi) {
                    is StepUi.Intro -> "New word"
                    is StepUi.Choice -> if (stepUi.en) "What does it mean?" else "How do you say it?"
                    is StepUi.Listen -> "What did you hear?"
                    is StepUi.SpeakQ -> "Say it out loud"
                    is StepUi.ToneQ -> "Which tone?"
                }
            )

            when (stepUi) {
                is StepUi.Intro -> IntroStep(vm, stepUi)
                is StepUi.Choice -> ChoiceStep(vm, stepUi)
                is StepUi.Listen -> ListenStep(vm, stepUi)
                is StepUi.ToneQ -> ToneStep(vm, stepUi)
                is StepUi.SpeakQ -> SpeakStep(vm, stepUi)
            }
        }

        FeedbackBar(vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PromptKind(label: String) {
    val p = LocalTriolingo.current
    Row(
        Modifier.padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(p.red))
        Text(
            label.uppercase(),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = p.ink3),
        )
    }
}

/** The white rounded exercise card (web .big-card). */
@Composable
private fun BigCard(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val p = LocalTriolingo.current
    Box(
        Modifier
            .fillMaxWidth()
            .triolingoShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .padding(horizontal = 24.dp, vertical = 30.dp),
        content = content,
    )
}

@Composable
private fun SlowButton(onClick: () -> Unit) {
    val p = LocalTriolingo.current
    Text(
        "🐢 hear it slowly",
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink3),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(6.dp),
    )
}

// ─── Intro ───

@Composable
private fun IntroStep(vm: AppViewModel, ui: StepUi.Intro) {
    val p = LocalTriolingo.current
    val item = ui.item
    BigCard {
        IconBubble("🔊", Modifier.align(Alignment.TopEnd)) { vm.audio.speak(item.hanzi) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (vm.state.settings.showHanzi) {
                Text(item.hanzi, style = TextStyle(fontSize = 30.sp, color = p.ink2), textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                item.pinyin,
                style = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, lineHeight = 50.sp, color = p.ink),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(item.en, style = TextStyle(fontSize = 18.sp, color = p.ink2), textAlign = TextAlign.Center)
            if (item.note != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.goldSoft).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("💡 ${item.note}", style = TextStyle(fontSize = 14.sp, color = p.ink, lineHeight = 20.sp), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(12.dp))
            SlowButton { vm.audio.speak(item.hanzi, slow = true) }
        }
    }
    Spacer(Modifier.height(20.dp))
    ChunkyButton("Got it — quiz me", BtnStyle.Primary, fill = true, modifier = Modifier.fillMaxWidth()) { vm.introDone() }
}

// ─── Options (shared by choice + listen) ───

private enum class OptState { Idle, Correct, Wrong, Faded }

@Composable
private fun OptionButton(
    state: OptState,
    keyNumber: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val p = LocalTriolingo.current
    val border: Color; val face: Color; val edge: Color
    when (state) {
        OptState.Correct -> { border = p.green; face = p.greenSoft; edge = p.green }
        OptState.Wrong -> { border = p.red; face = p.redSoft; edge = p.red }
        else -> { border = p.line; face = p.card; edge = p.line }
    }
    Chunky(
        face = face,
        edge = edge,
        border = border,
        shape = RoundedCornerShape(14.dp),
        edgeHeight = 3.dp,
        enabled = enabled,
        fill = true,
        onClick = if (enabled) onClick else null,
        modifier = Modifier.fillMaxWidth().alpha(if (state == OptState.Faded) 0.5f else 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (keyNumber != null) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, p.line, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$keyNumber", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink3))
                }
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun ChoiceStep(vm: AppViewModel, ui: StepUi.Choice) {
    val p = LocalTriolingo.current
    val item = ui.item
    var chosen by remember(ui) { mutableStateOf<Int?>(null) }

    if (ui.en) {
        BigCard {
            IconBubble("🔊", Modifier.align(Alignment.TopEnd)) { vm.audio.speak(item.hanzi) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.state.settings.showHanzi) {
                    Text(item.hanzi, style = TextStyle(fontSize = 30.sp, color = p.ink2), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    item.pinyin,
                    style = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, lineHeight = 50.sp, color = p.ink),
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        BigCard {
            Text(
                "“${item.en}”",
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, lineHeight = 38.sp, color = p.ink),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(20.dp))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.options.forEachIndexed { i, o ->
            val st = when {
                chosen == null -> OptState.Idle
                o.id == item.id -> OptState.Correct
                i == chosen -> OptState.Wrong
                else -> OptState.Faded
            }
            OptionButton(st, i + 1, enabled = chosen == null, onClick = { chosen = i; vm.answerChoice(o) }) {
                if (ui.en) {
                    Text(o.en, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ink))
                } else {
                    Column {
                        Text(o.pinyin, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ink))
                        if (vm.state.settings.showHanzi) {
                            Text(o.hanzi, style = TextStyle(fontSize = 13.sp, color = p.ink3))
                        }
                    }
                }
            }
        }
    }
}

// ─── Listen ───

@Composable
private fun ListenStep(vm: AppViewModel, ui: StepUi.Listen) {
    val p = LocalTriolingo.current
    var chosen by remember(ui) { mutableStateOf<Int?>(null) }

    BigCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AudioHero(playing = vm.listenPlaying, size = 96.dp) { vm.playListen(slow = false) }
            Spacer(Modifier.height(12.dp))
            SlowButton { vm.playListen(slow = true) }
        }
    }
    Spacer(Modifier.height(20.dp))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.options.forEachIndexed { i, o ->
            val st = when {
                chosen == null -> OptState.Idle
                o.id == ui.item.id -> OptState.Correct
                i == chosen -> OptState.Wrong
                else -> OptState.Faded
            }
            OptionButton(st, i + 1, enabled = chosen == null, onClick = { chosen = i; vm.answerListen(o) }) {
                Text(o.en, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ink))
            }
        }
    }
}

/** Big round red replay button (web .audio-hero), pulsing while audio plays. */
@Composable
private fun AudioHero(playing: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val p = LocalTriolingo.current
    val scale = if (playing) {
        val t = rememberInfiniteTransition(label = "hero-pulse")
        val s by t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "s")
        s
    } else 1f
    Chunky(
        face = p.red,
        edge = lerp(p.red, Color.Black, 0.3f),
        shape = CircleShape,
        edgeHeight = 6.dp,
        onClick = onClick,
        modifier = Modifier.width(size).height(size + 6.dp).scale(scale),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text("🔊", fontSize = 40.sp)
        }
    }
}

// ─── Tone ───

@Composable
private fun ToneStep(vm: AppViewModel, ui: StepUi.ToneQ) {
    val p = LocalTriolingo.current
    var chosen by remember(ui) { mutableStateOf<Int?>(null) }
    val tones = listOf(
        1 to ("ˉ" to "1st — high & flat"),
        2 to ("ˊ" to "2nd — rising"),
        3 to ("ˇ" to "3rd — dip down-up"),
        4 to ("ˋ" to "4th — sharp fall"),
    )

    BigCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AudioHero(playing = false, size = 96.dp) { vm.audio.speak(ui.item.hanzi) }
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    append("First syllable: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(ui.bare) }
                    if (ui.multi) append("…")
                },
                style = TextStyle(fontSize = 18.sp, color = p.ink2),
            )
            Spacer(Modifier.height(12.dp))
            SlowButton { vm.audio.speak(ui.item.hanzi, slow = true) }
        }
    }
    Spacer(Modifier.height(20.dp))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tones.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (n, tm) ->
                    val st = when {
                        chosen == null -> OptState.Idle
                        n == ui.tone -> OptState.Correct
                        n == chosen -> OptState.Wrong
                        else -> OptState.Faded
                    }
                    Box(Modifier.weight(1f)) {
                        OptionButton(st, null, enabled = chosen == null, onClick = { chosen = n; vm.answerTone(n) }) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(tm.first, style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, lineHeight = 34.sp))
                                Spacer(Modifier.height(4.dp))
                                Text(tm.second, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Speak ───

@Composable
private fun SpeakStep(vm: AppViewModel, ui: StepUi.SpeakQ) {
    val p = LocalTriolingo.current
    val item = ui.item
    val context = LocalContext.current
    val recognizer = remember { ChineseRecognizer(context) }
    val srAvailable = remember { recognizer.available }
    androidx.compose.runtime.DisposableEffect(ui) {
        onDispose { recognizer.cancel() } // release the mic if the step is left mid-listen
    }

    var listening by remember(ui) { mutableStateOf(false) }
    var settled by remember(ui) { mutableStateOf(false) }
    var hint by remember(ui) { mutableStateOf("Tap the mic, then say it in Chinese") }
    var transcript by remember(ui) { mutableStateOf("") }
    var scored by remember(ui) { mutableStateOf<AnnotatedString?>(null) }
    var micError by remember(ui) { mutableStateOf(false) }

    fun settle(ok: Boolean) {
        if (settled) return
        settled = true
        listening = false
        vm.settleSpeak(ok)
    }

    fun beginListening() {
        listening = true
        micError = false
        scored = null
        hint = "Listening… speak now"
        recognizer.start(
            onPartial = { transcript = it },
            onEnd = { finalText ->
                listening = false
                if (settled) return@start
                val said = if (finalText.isNotEmpty()) finalText else transcript
                if (cjkOnly(said).isEmpty()) {
                    hint = "Didn't catch any Chinese — tap to try again"
                    return@start
                }
                // per-char coloring of what was said vs the target
                val tgt = cjkOnly(item.hanzi)
                scored = buildAnnotatedString {
                    for (ch in cjkOnly(said)) {
                        withStyle(SpanStyle(color = if (ch in tgt) p.green else p.red)) { append(ch) }
                    }
                }
                settle(similarity(item.hanzi, said) >= 0.6)
            },
            onError = {
                listening = false
                hint = "Mic hiccup — tap to retry, or grade yourself honestly"
                micError = true
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening()
        else {
            hint = "Mic unavailable — check permissions"
            micError = true
        }
    }

    BigCard {
        IconBubble("🔊", Modifier.align(Alignment.TopEnd)) { vm.audio.speak(item.hanzi) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "“${item.en}”",
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, lineHeight = 38.sp, color = p.ink),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                item.pinyin + if (vm.state.settings.showHanzi) " · ${item.hanzi}" else "",
                style = TextStyle(fontSize = 18.sp, color = p.ink2),
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(20.dp))

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (srAvailable) {
            MicButton(listening = listening) {
                if (settled) return@MicButton
                if (listening) { recognizer.stop(); return@MicButton }
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) beginListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Spacer(Modifier.height(12.dp))
            Text(hint, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink2), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            if (micError) {
                ChunkyButton("✅ I said it", BtnStyle.Green, compact = true) { settle(true) }
            } else {
                val display = scored ?: AnnotatedString(transcript)
                Text(display, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.ink), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            ChunkyButton("Skip for now", BtnStyle.Ghost, compact = true) {
                recognizer.cancel()
                if (!settled) { settled = true; listening = false; vm.settleSpeak(false) }
            }
        } else {
            Text(
                "Say it out loud — really, out loud. Then be honest:",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink2),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyButton("✅ Nailed it", BtnStyle.Green, compact = true) {
                    if (!settled) { settled = true; vm.settleSpeak(true, selfGraded = true) }
                }
                ChunkyButton("😅 Struggled", BtnStyle.Ghost, compact = true) {
                    if (!settled) { settled = true; vm.settleSpeak(false, selfGraded = true) }
                }
            }
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val p = LocalTriolingo.current
    val color = if (listening) p.green else p.red
    val scale = if (listening) {
        val t = rememberInfiniteTransition(label = "mic-pulse")
        val s by t.animateFloat(1f, 1.05f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
        s
    } else 1f
    Chunky(
        face = color,
        edge = lerp(color, Color.Black, 0.3f),
        shape = CircleShape,
        edgeHeight = 6.dp,
        onClick = onClick,
        modifier = Modifier.width(110.dp).height(116.dp).scale(scale),
    ) {
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            Text("🎙️", fontSize = 44.sp)
        }
    }
}

// ─── Feedback bar ───

@Composable
private fun FeedbackBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = LocalTriolingo.current
    val fb = vm.feedback
    AnimatedVisibility(
        visible = fb != null,
        enter = slideInVertically(tween(300)) { it },
        exit = slideOutVertically(tween(300)) { it },
        modifier = modifier,
    ) {
        // Keep last non-null feedback for the exit animation
        var last by remember { mutableStateOf(fb) }
        if (fb != null) last = fb
        val f = last ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (f.ok) p.greenSoft else p.redSoft)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        f.headline,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (f.ok) p.green else p.red),
                    )
                    f.detail?.let { d ->
                        Spacer(Modifier.height(2.dp))
                        Text(detailText(d, p.ink, p.ink2), style = TextStyle(fontSize = 15.sp, color = p.ink2, lineHeight = 21.sp))
                    }
                    f.comboLine?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = TextStyle(fontSize = 15.sp, color = p.ink2))
                    }
                }
                ChunkyButton("Continue", if (f.ok) BtnStyle.Green else BtnStyle.Primary, compact = true) {
                    vm.continueFromFeedback()
                }
            }
        }
    }
}

private fun detailText(d: FbDetail, ink: Color, ink2: Color): AnnotatedString = buildAnnotatedString {
    fun hanziPinyin(item: VocabItem) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(item.hanzi) }
        append(" ${item.pinyin}")
    }
    when (d) {
        is FbDetail.Answer -> {
            d.prefix?.let { append(it) }
            hanziPinyin(d.item)
            append(" = ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(d.item.en) }
        }
        is FbDetail.ToneAnswer -> {
            hanziPinyin(d.item)
            append(" — tone ${d.tone}")
        }
        is FbDetail.Shadow -> {
            append("${d.prefix} ")
            hanziPinyin(d.item)
        }
    }
}
