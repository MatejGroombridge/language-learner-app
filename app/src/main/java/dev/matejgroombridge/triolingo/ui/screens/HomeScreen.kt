package dev.matejgroombridge.triolingo.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.matejgroombridge.triolingo.data.UNITS
import dev.matejgroombridge.triolingo.data.VOCAB
import dev.matejgroombridge.triolingo.domain.dailyXP
import dev.matejgroombridge.triolingo.domain.dueIds
import dev.matejgroombridge.triolingo.domain.levelInfo
import dev.matejgroombridge.triolingo.domain.streak
import dev.matejgroombridge.triolingo.domain.unlearnedItems
import dev.matejgroombridge.triolingo.ui.AppViewModel
import dev.matejgroombridge.triolingo.ui.components.BtnStyle
import dev.matejgroombridge.triolingo.ui.components.ChunkyButton
import dev.matejgroombridge.triolingo.ui.components.IconBubble
import dev.matejgroombridge.triolingo.ui.components.ProgressRing
import dev.matejgroombridge.triolingo.ui.components.triolingoShadow
import dev.matejgroombridge.triolingo.ui.theme.LocalTriolingo

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = LocalTriolingo.current
    val state = vm.state
    val due = state.dueIds().size
    val fresh = state.unlearnedItems().size
    val st = state.streak()
    val lvl = state.levelInfo().lvl
    val goalPct = state.dailyXP().toFloat() / state.dailyGoal
    val learned = state.cards.size

    val heroTitle: String
    val heroSub: String
    val heroCTA: String
    val heroMode: String
    when {
        due > 0 -> {
            heroTitle = "$due ${if (due == 1) "memory is" else "memories are"} fading"
            heroSub = "Rescue them before they slip away — this is where fluency is made."
            heroCTA = "Review now"; heroMode = "review"
        }
        fresh > 0 -> {
            heroTitle = if (learned == 0) "Say your first words" else "Ready for new words"
            heroSub = if (learned == 0)
                "Four words. Two minutes. You'll be speaking Chinese before this song ends."
            else "$learned down, $fresh to go. Grab the next four."
            heroCTA = if (learned == 0) "Start speaking" else "Learn new words"; heroMode = "learn"
        }
        else -> {
            heroTitle = "All caught up! 🎉"
            heroSub = "Every word is locked in. Sharpen your ear and tongue while you wait."
            heroCTA = "Practice"; heroMode = "review"
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 48.dp),
    ) {
        // ── Top bar ──
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Triolingo", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = p.ink))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatChip("🔥 $st", p.red, pulse = st > 0)
                    StatChip("⭐ Lv $lvl", p.gold)
                    IconBubble("⚙️") { vm.showSettings = true }
                }
            }
        }

        // ── Hero ──
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .triolingoShadow(RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(p.card)
                    .padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(goalPct, 92.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.dailyXP()}", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink))
                        Text(
                            "/ ${state.dailyGoal} XP",
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.ink3, letterSpacing = 0.5.sp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(heroTitle, style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, color = p.ink))
                    Spacer(Modifier.height(2.dp))
                    Text(heroSub, style = TextStyle(fontSize = 14.sp, color = p.ink2, lineHeight = 20.sp))
                    Spacer(Modifier.height(12.dp))
                    ChunkyButton(heroCTA, BtnStyle.Primary, compact = true) { vm.startSession(heroMode) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Mode grid ──
        item {
            val modes = listOf(
                ModeEntry("🌱", "Learn", "New words & phrases", "learn", enabled = fresh > 0),
                ModeEntry("🧠", "Review", "Spaced repetition", "review", badge = due),
                ModeEntry("👂", "Listening", "Train your ear", "listen"),
                ModeEntry("🎙️", "Speaking", "Say it out loud", "speak"),
                ModeEntry("🎵", "Tones", "The secret weapon", "tone"),
                ModeEntry("🔬", "The Method", "Why this works", null),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modes.chunked(2).forEach { rowModes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowModes.forEach { m ->
                            ModeCard(m, Modifier.weight(1f)) {
                                if (m.mode == null) vm.showMethod = true else vm.startSession(m.mode)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Your Path ──
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 0.dp).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("YOUR PATH", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = p.ink3))
                Text("$learned/${VOCAB.size} words", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink3))
            }
        }

        items(UNITS.size) { idx ->
            val u = UNITS[idx]
            val unitItems = VOCAB.filter { it.unit == u.n }
            val done = unitItems.count { it.id in state.cards }
            val pct = Math.round(done.toDouble() / unitItems.size * 100).toInt()
            val prevDone = u.n == 1 || VOCAB.filter { it.unit == u.n - 1 }.all { it.id in state.cards }
            val locked = !prevDone && done == 0
            UnitCard(u.n, u.name, u.emoji, u.tag, pct, locked)
            Spacer(Modifier.height(10.dp))
        }

        // ── Footer links ──
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            ) {
                FooterLink("Why this works") { vm.showMethod = true }
                FooterLink("Settings") { vm.showSettings = true }
            }
        }
    }
}

private data class ModeEntry(
    val emoji: String,
    val title: String,
    val sub: String,
    val mode: String?,
    val enabled: Boolean = true,
    val badge: Int = 0,
)

@Composable
private fun StatChip(text: String, color: androidx.compose.ui.graphics.Color, pulse: Boolean = false) {
    val p = LocalTriolingo.current
    val scale = if (pulse) {
        val t = rememberInfiniteTransition(label = "chip-pulse")
        val s by t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s")
        s
    } else 1f
    Box(
        Modifier
            .scale(scale)
            .triolingoShadow(RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(p.card)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color))
    }
}

@Composable
private fun ModeCard(m: ModeEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalTriolingo.current
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (m.enabled) 1f else 0.45f)
                .triolingoShadow(RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(p.card)
                .clickable(enabled = m.enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.emoji, fontSize = 24.sp)
            Column {
                Text(m.title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink))
                Text(m.sub, style = TextStyle(fontSize = 12.sp, color = p.ink2), maxLines = 1)
            }
        }
        if (m.badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-6).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(p.red)
                    .widthIn(min = 20.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${m.badge}", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color.White))
            }
        }
    }
}

@Composable
private fun UnitCard(n: Int, name: String, emoji: String, tag: String, pct: Int, locked: Boolean) {
    val p = LocalTriolingo.current
    val done = pct == 100
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.55f else 1f)
            .triolingoShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (done) p.goldSoft else p.bgSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✅" else emoji, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("$n. $name", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink))
            Text(tag, style = TextStyle(fontSize = 12.sp, color = p.ink2), maxLines = 1)
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(p.bgSoft)) {
                Box(
                    Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (done) p.green else p.gold)
                )
            }
        }
        Text("$pct%", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink3))
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    val p = LocalTriolingo.current
    Text(
        text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink3, textDecoration = TextDecoration.Underline),
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    )
}
