package dev.matejgroombridge.argot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.matejgroombridge.argot.domain.dailyXP
import dev.matejgroombridge.argot.domain.dueIds
import dev.matejgroombridge.argot.domain.streak
import dev.matejgroombridge.argot.domain.unlearnedItems
import dev.matejgroombridge.argot.ui.AppViewModel
import dev.matejgroombridge.argot.ui.components.BtnStyle
import dev.matejgroombridge.argot.ui.components.ChunkyButton
import dev.matejgroombridge.argot.ui.components.liuliShadow
import dev.matejgroombridge.argot.ui.theme.LocalLiuli

@Composable
fun SummaryScreen(vm: AppViewModel) {
    val p = LocalLiuli.current
    val sum = vm.summary ?: return
    val state = vm.state

    BackHandler { vm.goHome() }

    val acc = if (sum.correct + sum.wrong == 0) 100
    else Math.round(sum.correct.toDouble() / (sum.correct + sum.wrong) * 100).toInt()
    val due = state.dueIds().size
    val st = state.streak()
    val goalHit = state.dailyXP() >= state.dailyGoal
    val modeName = when (sum.mode) {
        "learn" -> "Lesson"; "review" -> "Review"; "practice" -> "Practice"
        "listen" -> "Listening"; "speak" -> "Speaking"; "tone" -> "Tone drill"
        else -> "Session"
    }
    val againLabel = when {
        due > 0 -> "🧠 Review $due fading ${if (due == 1) "word" else "words"}"
        state.unlearnedItems().isNotEmpty() -> "🌱 Keep going — new words"
        else -> "👂 Keep practicing"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 50.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (acc >= 90) "🏆" else if (acc >= 70) "🎉" else "💪", fontSize = 72.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "$modeName complete!",
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = p.ink),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (goalHit) "Daily goal smashed — your streak is safe. 🔥 ${if (st == 0) 1 else st} day${if (st == 1) "" else "s"}!"
            else "${state.dailyGoal - state.dailyXP()} XP to today's goal — one more session does it.",
            style = TextStyle(fontSize = 16.sp, color = p.ink2, lineHeight = 23.sp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SumStat("+${sum.xpEarned}", "XP", p.gold)
            SumStat("$acc%", "Accuracy", p.green)
            SumStat("${if (st == 0) 1 else st}", "Streak", p.red)
        }
        Spacer(Modifier.height(30.dp))
        Column(
            Modifier.widthIn(max = 340.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChunkyButton(againLabel, BtnStyle.Primary, fill = true, modifier = Modifier.fillMaxWidth()) {
                vm.startSession(vm.nextRecommendedMode())
            }
            ChunkyButton("Home", BtnStyle.Ghost, fill = true, modifier = Modifier.fillMaxWidth()) {
                vm.goHome()
            }
        }
    }
}

@Composable
private fun SumStat(value: String, label: String, color: Color) {
    val p = LocalLiuli.current
    Column(
        Modifier
            .liuliShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .widthIn(min = 100.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color))
        Text(
            label.uppercase(),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = p.ink3),
        )
    }
}
