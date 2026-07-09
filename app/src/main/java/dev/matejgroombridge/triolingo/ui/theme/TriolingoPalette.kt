package dev.matejgroombridge.triolingo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Triolingo design tokens, lifted verbatim from the web app's css/style.css
 * (:root light values + prefers-color-scheme dark values). Material 3 slots
 * cover the big surfaces; this palette carries the precise brand tokens the
 * web design uses everywhere (soft tints, triple ink scale, hairlines).
 */
@Immutable
data class TriolingoPalette(
    val bg: Color,
    val bgSoft: Color,
    val card: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val red: Color,
    val redSoft: Color,
    val gold: Color,
    val goldSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val blue: Color,
    val blueSoft: Color,
)

val LightTriolingo = TriolingoPalette(
    bg = Color(0xFFFAF6F0),
    bgSoft = Color(0xFFF3EDE3),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF201B16),
    ink2 = Color(0xFF6F665C),
    ink3 = Color(0xFFA89D90),
    line = Color(0xFFE8E0D3),
    red = Color(0xFFD6432C),
    redSoft = Color(0xFFFBE9E4),
    gold = Color(0xFFC08A2D),
    goldSoft = Color(0xFFF7ECD7),
    green = Color(0xFF3D9950),
    greenSoft = Color(0xFFE4F3E6),
    blue = Color(0xFF3D6F99),
    blueSoft = Color(0xFFE5EEF5),
)

val DarkTriolingo = TriolingoPalette(
    bg = Color(0xFF17130F),
    bgSoft = Color(0xFF1F1A15),
    card = Color(0xFF241E18),
    ink = Color(0xFFF1EAE0),
    ink2 = Color(0xFFA99E90),
    ink3 = Color(0xFF756A5C),
    line = Color(0xFF362E25),
    red = Color(0xFFE8664E),
    redSoft = Color(0xFF3A221C),
    gold = Color(0xFFD9A648),
    goldSoft = Color(0xFF362B18),
    green = Color(0xFF58B56B),
    greenSoft = Color(0xFF1E3323),
    blue = Color(0xFF6B9EC7),
    blueSoft = Color(0xFF1C2A36),
)

val LocalTriolingo = staticCompositionLocalOf { LightTriolingo }
