package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.AppSettings
import com.example.data.scanner.LibraryData
import com.example.ui.components.*
import com.example.ui.theme.AccentOption
import com.example.ui.theme.PulseTheme
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

class SettingsCallbacks(
    val onThemeMode: (ThemeMode) -> Unit,
    val onAccent: (AccentOption) -> Unit,
    val onAnimationIntensity: (Float) -> Unit,
    val onSkipSilence: (Boolean) -> Unit,
    val onSleepFade: (Boolean) -> Unit,
    val onLyricsScale: (Float) -> Unit,
    val onOpenAudioLab: (Int) -> Unit,
    val onRescan: () -> Unit,
    val onOpenStats: () -> Unit,
    val onOpenCarMode: () -> Unit,
    val onOpenSystemNotificationSettings: () -> Unit,
    val onShakeToSkip: (Boolean) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onMinTrackSeconds: (Int) -> Unit = {},
    val onBackup: () -> Unit = {},
    val onRestore: () -> Unit = {}
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    library: LibraryData,
    callbacks: SettingsCallbacks,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    val themeMode = ThemeMode.fromName(settings.themeMode)
    val accent = AccentOption.fromName(settings.accent)
    val storageMb = remember(library.songs) { library.songs.sumOf { it.size } / (1024 * 1024) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader("Settings", subtitle = "Make Pulse yours")

        SettingsGroup("Theme", Icons.Rounded.Palette) {
            Text("Appearance", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeTile(mode, mode == themeMode, { callbacks.onThemeMode(mode) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (themeMode == ThemeMode.MATERIAL_YOU) "Accent color · from your wallpaper" else "Accent color",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AccentOption.entries.forEach { option ->
                    val selected = option == accent && themeMode != ThemeMode.MATERIAL_YOU
                    Box(
                        Modifier
                            .size(48.dp)
                            .then(if (selected) Modifier.softGlow(option.primary, radius = 14.dp, cornerRadius = 24.dp, alpha = 0.6f) else Modifier)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(option.partner, option.primary)))
                            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else colors.glassBorder, CircleShape)
                            .clickable(enabled = themeMode != ThemeMode.MATERIAL_YOU) { callbacks.onAccent(option) }
                            .semantics { contentDescription = "${option.label} accent"; this.selected = selected },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Animation intensity", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text("${(settings.animationIntensity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
            PulseSlider(settings.animationIntensity, { callbacks.onAnimationIntensity((it * 20).roundToInt() / 20f) }, Modifier.padding(horizontal = 8.dp), label = "Animation intensity")
        }

        SettingsGroup("Playback", Icons.Rounded.PlayCircle) {
            GlassListRow(Icons.Rounded.FastForward, "Skip silence", subtitle = "Trim quiet gaps in tracks and podcasts", trailing = { PulseSwitch(settings.skipSilence, callbacks.onSkipSilence) })
            GlassListRow(Icons.Rounded.Bedtime, "Sleep timer fade-out", subtitle = "Lower volume gently before stopping", trailing = { PulseSwitch(settings.sleepFadeOut, callbacks.onSleepFade) })
            GlassListRow(Icons.Rounded.Vibration, "Shake to skip", subtitle = "Shake your phone twice to play the next song", trailing = { PulseSwitch(settings.shakeToSkip, callbacks.onShakeToSkip) })
            GlassListRow(Icons.Rounded.LightMode, "Keep screen on", subtitle = "While the player or lyrics are open and playing", trailing = { PulseSwitch(settings.keepScreenOn, callbacks.onKeepScreenOn) })
            GlassListRow(Icons.Rounded.Notifications, "Notification & lock screen", subtitle = "Media controls, like & shuffle buttons", onClick = callbacks.onOpenSystemNotificationSettings, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
        }

        SettingsGroup("Audio & devices", Icons.Rounded.Headphones) {
            GlassListRow(Icons.Rounded.Equalizer, "Equalizer", subtitle = "10-band EQ, presets, preamp", onClick = { callbacks.onOpenAudioLab(0) }, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
            GlassListRow(Icons.Rounded.SurroundSound, "Spatial & ear controls", subtitle = "3D stage, balance, mono, bass boost", onClick = { callbacks.onOpenAudioLab(1) }, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
            GlassListRow(Icons.Rounded.BluetoothAudio, "Bluetooth & wired headphones", subtitle = "Device EQ profiles, auto pause & resume", onClick = { callbacks.onOpenAudioLab(2) }, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
            GlassListRow(Icons.Rounded.DirectionsCar, "Car mode", subtitle = "Big, glanceable controls for driving", onClick = callbacks.onOpenCarMode, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
        }

        SettingsGroup("Library", Icons.Rounded.LibraryMusic) {
            GlassListRow(Icons.Rounded.Refresh, "Rescan library", subtitle = "${(library.songs.size).plural("song")} · ${(library.albums.size).plural("album")} · ${(library.artists.size).plural("artist")}", onClick = callbacks.onRescan)
            GlassListRow(Icons.Rounded.Storage, "Storage", subtitle = "$storageMb MB of music in ${library.folders.size} folders")
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = colors.accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hide short audio", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    Text("Skip clips, voice notes and sound effects", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 20 to "20s", 30 to "30s", 60 to "60s").forEach { (sec, label) ->
                    PulseChip(label, selected = settings.minTrackSeconds == sec, onClick = { callbacks.onMinTrackSeconds(sec) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            GlassListRow(Icons.Rounded.Insights, "Listening statistics", subtitle = "Top songs, artists, streaks", onClick = callbacks.onOpenStats, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
        }

        SettingsGroup("Backup & restore", Icons.Rounded.CloudSync) {
            GlassListRow(Icons.Rounded.Upload, "Back up", subtitle = "Save liked songs & playlists to a file", onClick = callbacks.onBackup, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
            GlassListRow(Icons.Rounded.Download, "Restore", subtitle = "Bring them back on this or a new phone", onClick = callbacks.onRestore, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary) })
        }

        SettingsGroup("Accessibility", Icons.Rounded.Accessibility) {
            GlassListRow(
                Icons.Rounded.MotionPhotosOff,
                "Reduce motion",
                subtitle = "Calm backgrounds, no spinning or glow pulses",
                trailing = { PulseSwitch(settings.animationIntensity == 0f, { callbacks.onAnimationIntensity(if (it) 0f else 1f) }) }
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TextFields, null, tint = colors.accent)
                Spacer(Modifier.width(14.dp))
                Text("Lyrics text size", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text("${(settings.lyricsFontScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
            PulseSlider(settings.lyricsFontScale, { callbacks.onLyricsScale((it * 20).roundToInt() / 20f) }, Modifier.padding(horizontal = 8.dp), 0.8f..1.6f, label = "Lyrics text size")
        }

        SettingsGroup("About", Icons.Rounded.Info) {
            GlassListRow(Icons.Rounded.MusicNote, "Pulse Music", subtitle = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            GlassListRow(Icons.Rounded.Shield, "Private by design", subtitle = "100% offline · no ads · no accounts · no tracking")
            GlassListRow(Icons.Rounded.Code, "Built with", subtitle = "Kotlin · Jetpack Compose · Media3 ExoPlayer · Room")
            GlassListRow(Icons.Rounded.FontDownload, "Typeface", subtitle = "Plus Jakarta Sans · SIL Open Font License")
        }

        MadeByCard(Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    val colors = PulseTheme.colors
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .glass(colors, CardShape)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            content = content
        )
    }
}

@Composable
private fun ThemeModeTile(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = PulseTheme.colors
    val (preview, icon) = when (mode) {
        ThemeMode.AMOLED -> listOf(Color.Black, Color(0xFF16161D)) to Icons.Rounded.DarkMode
        ThemeMode.LIGHT -> listOf(Color(0xFFF5F3FA), Color.White) to Icons.Rounded.LightMode
        ThemeMode.MATERIAL_YOU -> listOf(Color(0xFF7C3AED), Color(0xFF0EA5E9)) to Icons.Rounded.Wallpaper
        ThemeMode.SYSTEM -> listOf(Color.Black, Color(0xFFF5F3FA)) to Icons.Rounded.BrightnessAuto
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(preview))
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.glassBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (mode == ThemeMode.LIGHT) Color(0xFF15121E) else Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            mode.label.replace(" Dark", "").replace("Follow ", "").replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun MadeByCard(modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    val motion = PulseTheme.motion
    val beat = rememberInfiniteTransition(label = "heartbeat")
    val heart by beat.animateFloat(
        initialValue = 1f,
        targetValue = if (motion > 0f) 1.22f else 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heart"
    )
    Box(
        modifier
            .fillMaxWidth()
            .softGlow(colors.accent, radius = 30.dp, alpha = 0.35f * motion.coerceAtLeast(0.4f))
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(colors.accentPartner.copy(alpha = 0.35f), colors.accent.copy(alpha = 0.25f), Color.Transparent)))
            .glass(colors, CardShape)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PulseLogo(size = 60.dp)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Made with ", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                Icon(
                    Icons.Rounded.Favorite,
                    "love",
                    tint = colors.accentPartner,
                    modifier = Modifier.size(18.dp).graphicsLayer { scaleX = heart; scaleY = heart }
                )
                Text(" by", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Prajwal A Bhandagi",
                style = MaterialTheme.typography.headlineMedium.copy(brush = Brush.linearGradient(colors.accentGradientColors)),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text("Designer & Developer", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .glass(colors, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Verified, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pulse Music v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            Text("© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Prajwal A Bhandagi · All rights reserved", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, textAlign = TextAlign.Center)
        }
    }
}
