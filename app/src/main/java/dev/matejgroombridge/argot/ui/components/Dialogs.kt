package dev.matejgroombridge.argot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.matejgroombridge.argot.ui.AppViewModel
import dev.matejgroombridge.argot.ui.theme.LocalLiuli
import java.util.Locale
import kotlin.math.roundToInt

/** Shared modal card (web .modal-veil + .modal): tap outside dismisses. */
@Composable
private fun LiuliModal(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalLiuli.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .liuliShadow(RoundedCornerShape(20.dp), lift = true)
                .clip(RoundedCornerShape(20.dp))
                .background(p.card)
                .padding(26.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

// ─── The Method ───

@Composable
fun MethodDialog(onDismiss: () -> Unit) {
    val p = LocalLiuli.current

    @Composable
    fun para(bold: String, rest: String) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = p.ink)) { append(bold) }
                append(" ")
                append(rest)
            },
            style = TextStyle(fontSize = 15.sp, color = p.ink2, lineHeight = 22.sp),
        )
        Spacer(Modifier.height(12.dp))
    }

    LiuliModal(onDismiss) {
        Text("🔬 Why Liúlì works", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.ink))
        Spacer(Modifier.height(14.dp))
        para("Spaced repetition.", "Each word comes back exactly when your brain is about to forget it (30 min → 1 day → growing intervals). This is the single most-replicated finding in memory research.")
        para("Active recall.", "You're never shown answers to reread — you're forced to retrieve them. Retrieval practice beats passive review by a wide margin (the “testing effect”).")
        para("Listening first.", "Fluency is built ear-first. Audio plays before text on most exercises, so you bind sound → meaning, not text → meaning.")
        para("Speak from day one.", "The mic exercises force output. Producing language is a different skill from recognizing it — so we train it directly.")
        para("Tones as a skill, not trivia.", "Dedicated tone drills train the pitch categories your ear needs before your mouth can copy them.")
        para("Chunks, not just words.", "You learn whole sentences (“太贵了!”) you can deploy instantly — the fastest path to feeling fluent.")
        para("Mistakes are fuel.", "Wrong answers come back within the same session until you nail them, and again 10 minutes later.")
        Spacer(Modifier.height(8.dp))
        ChunkyButton("让我们开始吧 — let's go", BtnStyle.Primary, fill = true, modifier = Modifier.fillMaxWidth()) { onDismiss() }
    }
}

// ─── Settings ───

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = LocalLiuli.current
    var confirmReset by remember { mutableStateOf(false) }

    LiuliModal(onDismiss) {
        Text("⚙️ Settings", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.ink))
        Spacer(Modifier.height(4.dp))

        SettingRow(
            label = "Show characters",
            sub = "Hanzi alongside pinyin (recommended — free exposure)",
        ) {
            LiuliToggle(vm.state.settings.showHanzi) { vm.setShowHanzi(it) }
        }
        RowDivider()
        SettingRow(label = "Sound effects") {
            LiuliToggle(vm.state.settings.sound) { vm.setSound(it) }
        }
        RowDivider()
        SettingRow(
            label = "Voice speed",
            sub = String.format(Locale.US, "%.2f×", vm.state.settings.rate),
        ) {
            Slider(
                value = vm.state.settings.rate,
                onValueChange = { vm.setRate((it * 20).roundToInt() / 20f) }, // 0.05 steps
                onValueChangeFinished = { vm.previewVoice() },
                valueRange = 0.5f..1.2f,
                modifier = Modifier.width(130.dp),
                colors = SliderDefaults.colors(thumbColor = p.red, activeTrackColor = p.red, inactiveTrackColor = p.line),
            )
        }
        RowDivider()
        SettingRow(label = "Daily goal", sub = "${vm.state.dailyGoal} XP") {
            Slider(
                value = vm.state.dailyGoal.toFloat(),
                onValueChange = { vm.setDailyGoal((it / 50).roundToInt() * 50) },
                valueRange = 50f..500f,
                modifier = Modifier.width(130.dp),
                colors = SliderDefaults.colors(thumbColor = p.red, activeTrackColor = p.red, inactiveTrackColor = p.line),
            )
        }
        RowDivider()
        SettingRow(label = "Danger zone") {
            Text(
                "Reset all progress",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.red, textDecoration = TextDecoration.Underline),
                modifier = Modifier.clickable { confirmReset = true }.padding(4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        ChunkyButton("Done", BtnStyle.Ghost, fill = true, modifier = Modifier.fillMaxWidth()) { onDismiss() }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = p.card,
            title = { Text("Reset all progress?", color = p.ink) },
            text = { Text("Wipe all progress? This can't be undone.", color = p.ink2) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetProgress() }) {
                    Text("Reset", color = p.red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = p.ink2) }
            },
        )
    }
}

@Composable
private fun SettingRow(label: String, sub: String? = null, trailing: @Composable () -> Unit) {
    val p = LocalLiuli.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.ink))
            if (sub != null) Text(sub, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.ink3, lineHeight = 16.sp))
        }
        trailing()
    }
}

@Composable
private fun RowDivider() {
    val p = LocalLiuli.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

/** Pill toggle (web .toggle). */
@Composable
private fun LiuliToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalLiuli.current
    Box(
        Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) p.green else p.line)
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(3.dp)
                .size(24.dp)
                .liuliShadow(CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
