package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pulse Music core palette — AMOLED black with electric violet / hot pink accents
val PureBlack = Color(0xFF000000)
val Surface1 = Color(0xFF0F0F14)
val Surface2 = Color(0xFF16161D)
val Surface3 = Color(0xFF1E1E27)

val ElectricViolet = Color(0xFF8B5CF6)
val VioletLight = Color(0xFFA78BFA)
val VioletDeep = Color(0xFF6D28D9)
val HotPink = Color(0xFFEC4899)
val Indigo = Color(0xFF4F46E5)

// Legacy names kept so older screens and dialogs keep compiling with the new palette
val ObsidianBlack = PureBlack
val ObsidianSurface = Surface1
val ObsidianSurfaceVariant = Surface2
val ObsidianSurfaceSubtle = Surface3

val PulseCyan = VioletLight
val PulseCyanMuted = ElectricViolet
val PulseMagenta = HotPink
val PulseHotPink = HotPink
val PulseViolet = ElectricViolet
val PulseAmber = Color(0xFFFBBF24)
val PulseEmerald = Color(0xFF34D399)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99FFFFFF)
val TextMuted = Color(0x61FFFFFF)
val BorderSubtle = Color(0x14FFFFFF)

/** Accent choices offered in Settings → Theme. First color is the primary, second the gradient partner. */
enum class AccentOption(val label: String, val primary: Color, val partner: Color) {
    VIOLET("Violet", ElectricViolet, HotPink),
    SUNSET("Sunset", Color(0xFFF97316), Color(0xFFEC4899)),
    OCEAN("Ocean", Color(0xFF3B82F6), Color(0xFF06B6D4)),
    EMERALD("Emerald", Color(0xFF10B981), Color(0xFF84CC16)),
    CRIMSON("Crimson", Color(0xFFEF4444), Color(0xFFF59E0B)),
    AURORA("Aurora", Color(0xFF06B6D4), Color(0xFF8B5CF6));

    companion object {
        fun fromName(name: String?): AccentOption = entries.firstOrNull { it.name == name } ?: VIOLET
    }
}

// Deterministic gradient pairs used for generated album covers
val ArtworkPalettes: List<List<Color>> = listOf(
    listOf(Color(0xFF7C3AED), Color(0xFFEC4899), Color(0xFFF97316)),
    listOf(Color(0xFF1E3A8A), Color(0xFF7C3AED), Color(0xFFDB2777)),
    listOf(Color(0xFF0EA5E9), Color(0xFF6366F1), Color(0xFFA855F7)),
    listOf(Color(0xFFF43F5E), Color(0xFFF59E0B), Color(0xFFFDE68A)),
    listOf(Color(0xFF059669), Color(0xFF0EA5E9), Color(0xFF6366F1)),
    listOf(Color(0xFF9333EA), Color(0xFF312E81), Color(0xFF0F172A)),
    listOf(Color(0xFFDB2777), Color(0xFF7C2D12), Color(0xFF1C1917)),
    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF1E1B4B))
)
