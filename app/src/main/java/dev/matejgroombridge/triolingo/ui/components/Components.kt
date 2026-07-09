package dev.matejgroombridge.triolingo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import dev.matejgroombridge.triolingo.ui.theme.LocalTriolingo
import kotlin.random.Random

/** Warm, soft drop shadow used by cards/chips (the web `--shadow`). */
fun Modifier.triolingoShadow(shape: Shape, lift: Boolean = false) = this.shadow(
    elevation = if (lift) 10.dp else 5.dp,
    shape = shape,
    ambientColor = Color(0xFF3C2C18),
    spotColor = Color(0xFF3C2C18),
)

/**
 * The web design's signature "chunky" pressable: a face resting on a solid
 * darker edge (box-shadow 0 4px 0 …) that the face sinks into when pressed.
 */
@Composable
fun Chunky(
    face: Color,
    edge: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    border: Color? = null,
    borderWidth: Dp = 2.dp,
    edgeHeight: Dp = 4.dp,
    enabled: Boolean = true,
    fill: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink = if (pressed && enabled && onClick != null) edgeHeight - 1.dp else 0.dp
    Box(modifier) {
        // edge: same rounded rect shifted down, revealed below the face
        Box(
            Modifier
                .matchParentSize()
                .padding(top = edgeHeight)
                .clip(shape)
                .background(edge)
        )
        var faceMod: Modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
            .padding(bottom = edgeHeight - sink)
            .padding(top = sink)
            .clip(shape)
            .background(face)
        if (border != null) faceMod = faceMod.border(borderWidth, border, shape)
        if (onClick != null) faceMod = faceMod.clickable(interaction, indication = null, enabled = enabled) { onClick() }
        Box(faceMod) { content(pressed) }
    }
}

enum class BtnStyle { Primary, Green, Ghost }

@Composable
fun ChunkyButton(
    text: String,
    style: BtnStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalTriolingo.current
    val face: Color; val edge: Color; val textColor: Color; val border: Color?
    when (style) {
        BtnStyle.Primary -> { face = p.red; edge = lerp(p.red, Color.Black, 0.3f); textColor = Color.White; border = null }
        BtnStyle.Green -> { face = p.green; edge = lerp(p.green, Color.Black, 0.3f); textColor = Color.White; border = null }
        BtnStyle.Ghost -> { face = p.card; edge = p.line; textColor = p.ink2; border = p.line }
    }
    Chunky(
        face = if (enabled) face else face.copy(alpha = 0.45f).compositeOverBg(p.bg),
        edge = if (enabled) edge else edge.copy(alpha = 0.45f).compositeOverBg(p.bg),
        shape = RoundedCornerShape(16.dp),
        border = border,
        edgeHeight = if (style == BtnStyle.Ghost) 3.dp else 4.dp,
        enabled = enabled,
        fill = fill,
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(
                    if (compact) PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                    else PaddingValues(horizontal = 28.dp, vertical = 15.dp)
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = TextStyle(
                    fontSize = if (compact) 16.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) textColor else textColor.copy(alpha = 0.6f),
                ),
            )
        }
    }
}

/** Flatten a translucent colour onto [bg] so disabled chunky layers don't stack. */
private fun Color.compositeOverBg(bg: Color): Color = lerp(bg, this.copy(alpha = 1f), this.alpha)

/** 44dp round emoji button on the soft background (web .icon-btn). */
@Composable
fun IconBubble(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fontSize: Int = 20,
    onClick: () -> Unit,
) {
    val p = LocalTriolingo.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(p.bgSoft)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = fontSize.sp)
    }
}

/** Daily-goal ring (web ringSVG): gold arc over a soft track. */
@Composable
fun ProgressRing(pct: Float, size: Dp, content: @Composable () -> Unit) {
    val p = LocalTriolingo.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = 9.dp.toPx()
            val pad = 5.dp.toPx() + strokePx / 2
            val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)
            val topLeft = Offset(pad, pad)
            drawArc(p.bgSoft, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            drawArc(
                p.gold, -90f, 360f * pct.coerceIn(0f, 1f), false, topLeft, arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

// ─── Confetti ─── port of the web canvas burst: 90 squares, ~110 frames

private class ConfettiParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val s: Float, val c: Color, var r: Float, val vr: Float,
)

private class ConfettiBurst(w: Float, h: Float, density: Float, colors: List<Color>) {
    val parts = List(90) {
        ConfettiParticle(
            x = w / 2 + (Random.nextFloat() - 0.5f) * 200 * density,
            y = h * 0.35f,
            vx = (Random.nextFloat() - 0.5f) * 11 * density,
            vy = (-Random.nextFloat() * 11 - 4) * density,
            s = (Random.nextFloat() * 7 + 4) * density,
            c = colors.random(),
            r = Random.nextFloat() * Math.PI.toFloat(),
            vr = (Random.nextFloat() - 0.5f) * 0.25f,
        )
    }
    var frames = 0
    fun step(density: Float) {
        for (pt in parts) {
            pt.x += pt.vx; pt.y += pt.vy; pt.vy += 0.32f * density; pt.r += pt.vr
        }
        frames++
    }
}

/** Full-screen confetti overlay; every bump of [trigger] fires a burst. */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFFD6432C), Color(0xFFC08A2D), Color(0xFF3D9950), Color(0xFF3D6F99), Color(0xFFE8664E))
    val density = LocalDensity.current.density
    val bursts = remember { mutableStateListOf<ConfettiBurst>() }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var frameTick by remember { mutableStateOf(0L) }

    LaunchedEffect(trigger) {
        if (trigger <= 0 || canvasSize == Size.Zero) return@LaunchedEffect
        bursts.add(ConfettiBurst(canvasSize.width, canvasSize.height, density, colors))
        while (bursts.isNotEmpty()) {
            withFrameNanos { t ->
                bursts.forEach { it.step(density) }
                bursts.removeAll { it.frames >= 110 }
                frameTick = t
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        canvasSize = size
        @Suppress("UNUSED_EXPRESSION") frameTick // invalidate every frame while animating
        for (b in bursts) for (pt in b.parts) {
            rotate(degrees = Math.toDegrees(pt.r.toDouble()).toFloat(), pivot = Offset(pt.x, pt.y)) {
                drawRect(pt.c, Offset(pt.x - pt.s / 2, pt.y - pt.s / 2), Size(pt.s, pt.s * 0.65f))
            }
        }
    }
}

/** Top-center "⬆️ Level N! Keep climbing." toast (web .levelup). */
@Composable
fun LevelUpToast(level: Int) {
    val p = LocalTriolingo.current
    Row(
        Modifier
            .triolingoShadow(RoundedCornerShape(99.dp), lift = true)
            .clip(RoundedCornerShape(99.dp))
            .background(p.ink)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⬆️", fontSize = 16.sp)
        Text(
            "Level $level! Keep climbing.",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.bg),
        )
    }
}
