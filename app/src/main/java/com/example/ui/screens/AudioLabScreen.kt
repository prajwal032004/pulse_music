package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiolab.AudioLabManager
import com.example.data.AppSettings
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import kotlin.math.roundToInt

/** Settings callbacks the Audio Lab needs from the ViewModel. */
class AudioLabCallbacks(
    val onBack: () -> Unit,
    val onOpenOutputSwitcher: () -> Unit,
    val onOpenBluetoothSettings: () -> Unit,
    val onOpenSoundSettings: () -> Unit,
    val onPerDeviceEq: (Boolean) -> Unit,
    val onPauseOnDisconnect: (Boolean) -> Unit,
    val onResumeOnReconnect: (Boolean) -> Unit,
    val onBluetoothAutoplay: (Boolean) -> Unit,
    val onToast: (String) -> Unit
)

@Composable
fun AudioLabScreen(
    state: AudioLabState,
    spectrum: List<Float>,
    isPlaying: Boolean,
    settings: AppSettings,
    manager: AudioLabManager,
    callbacks: AudioLabCallbacks,
    initialTab: Int,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 120.dp
) {
    val colors = PulseTheme.colors
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    var showHearingTest by remember { mutableStateOf(false) }

    AuroraBackground(modifier.fillMaxSize(), strength = 0.8f) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", callbacks.onBack, modifier = Modifier.testTag("audio_lab_back_button"))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Audio Lab", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Text(state.outputDevice?.let { "Output · ${it.name}" } ?: "Sound tuned your way", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Equalizer" to Icons.Rounded.Equalizer, "Spatial" to Icons.Rounded.Hearing, "Devices" to Icons.Rounded.Headphones)
                    .forEachIndexed { i, (label, icon) ->
                        PulseChip(label, tab == i, { tab = i }, icon = icon, modifier = Modifier.weight(1f))
                    }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (tab) {
                    0 -> EqualizerTab(state, spectrum, isPlaying, manager, callbacks)
                    1 -> SpatialTab(state, manager, callbacks, onHearingTest = { showHearingTest = true })
                    else -> DevicesTab(state, settings, manager, callbacks)
                }
            }
        }
    }

    if (showHearingTest) {
        HearingTestDialog(
            toneGenerator = manager.toneGenerator,
            onTestCompleted = { left, right ->
                manager.setHearingCompensation(true, left, right)
                showHearingTest = false
                callbacks.onToast("Hearing profile saved")
            },
            onDismiss = { showHearingTest = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Equalizer
// ---------------------------------------------------------------------------------------------

@Composable
private fun EqualizerTab(state: AudioLabState, spectrum: List<Float>, isPlaying: Boolean, manager: AudioLabManager, callbacks: AudioLabCallbacks) {
    val colors = PulseTheme.colors

    // Live spectrum
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live spectrum", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (state.isDolbyAtmosDetected) DolbyAtmosBadge()
                Spacer(Modifier.width(6.dp))
                // Only claim Hi-Res when the active output actually reports a >48 kHz path.
                if (state.outputDevice?.supportsHiRes == true) HiResBadge()
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val n = spectrum.size
                val slot = size.width / n
                val w = slot * 0.62f
                val brush = Brush.verticalGradient(listOf(colors.accentPartner, colors.accent), startY = 0f, endY = size.height * 0.78f)
                val base = size.height * 0.78f
                spectrum.forEachIndexed { i, m ->
                    val h = base * m
                    val x = i * slot + (slot - w) / 2
                    drawRoundRect(brush, Offset(x, base - h), Size(w, h), CornerRadius(w / 2))
                    // Reflection
                    drawRoundRect(brush, Offset(x, base + 2.dp.toPx()), Size(w, h * 0.25f), CornerRadius(w / 2), alpha = 0.18f)
                }
            }
            if (!isPlaying) {
                Text("Play something to see the spectrum move", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
    }

    // 10-band EQ
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("10-band equalizer", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(state.currentPreset.displayName, style = MaterialTheme.typography.bodySmall, color = colors.accent)
                }
                PulseIconButton(Icons.Rounded.RestartAlt, "Reset to flat", { manager.resetEqualizer() }, tint = colors.textSecondary)
                PulseSwitch(state.equalizerEnabled, { manager.setEqualizerEnabled(it) }, Modifier.testTag("equalizer_switch"))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(250.dp)) {
                Column(Modifier.fillMaxHeight().padding(vertical = 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf("+15", "0", "−15").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary) }
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    // Response curve connecting the band levels
                    Canvas(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp).fillMaxHeight()) {
                        val n = state.bands.size
                        val slot = size.width / n
                        val thumbR = 9.dp.toPx()
                        val usable = size.height - thumbR * 2
                        drawLine(colors.textTertiary.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                        val points = state.bands.mapIndexed { i, b ->
                            Offset(i * slot + slot / 2, thumbR + usable * (1f - (b.levelDb + 15f) / 30f))
                        }
                        if (points.size > 1) {
                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    val p0 = points[i - 1]
                                    val p1 = points[i]
                                    val mx = (p0.x + p1.x) / 2
                                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                                }
                            }
                            drawPath(path, Brush.horizontalGradient(colors.accentGradientColors), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round), alpha = if (state.equalizerEnabled) 0.55f else 0.2f)
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        state.bands.forEach { band ->
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    (if (band.levelDb > 0) "+" else "") + band.levelDb.roundToInt(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (band.levelMb != 0) colors.textPrimary else colors.textTertiary,
                                    modifier = Modifier.height(20.dp)
                                )
                                VerticalGlowSlider(
                                    value = band.levelDb,
                                    onValueChange = { manager.setBandLevel(band.index, (it * 100).roundToInt()) },
                                    enabled = state.equalizerEnabled,
                                    label = "${band.displayFrequency} hertz band",
                                    modifier = Modifier.weight(1f)
                                )
                                Text(band.displayFrequency, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.height(24.dp).padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preamp", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.width(72.dp))
                PulseSlider(state.preampGainDb, { manager.setPreampGain((it * 2).roundToInt() / 2f) }, Modifier.weight(1f), -12f..12f, centered = true, enabled = state.equalizerEnabled, label = "Preamp gain")
                Text("${if (state.preampGainDb > 0) "+" else ""}${"%.1f".format(state.preampGainDb)} dB", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.width(64.dp).padding(start = 8.dp))
            }
        }
    }

    // Presets
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Presets", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "Save as Custom",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { manager.saveCustomPreset(); callbacks.onToast("Saved as Custom preset") }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            val presets = listOf(
                EqPreset.FLAT, EqPreset.JAZZ, EqPreset.ROCK, EqPreset.POP, EqPreset.CLASSICAL, EqPreset.HIPHOP,
                EqPreset.EDM, EqPreset.METAL, EqPreset.ACOUSTIC, EqPreset.VOCAL, EqPreset.BASS_BOOST, EqPreset.CUSTOM
            )
            presets.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { p ->
                        PulseChip(p.displayName, state.currentPreset == p, { manager.applyPreset(p) }, Modifier.weight(1f))
                    }
                }
            }
            GlassListRow(
                Icons.Rounded.AutoAwesome,
                "Auto-match track genre",
                subtitle = "Switch presets automatically for rock, jazz, EDM…",
                trailing = { PulseSwitch(state.autoPresetByGenre, { manager.setAutoPresetByGenre(it) }) }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Spatial, ear controls & enhancements
// ---------------------------------------------------------------------------------------------

@Composable
private fun SpatialTab(state: AudioLabState, manager: AudioLabManager, callbacks: AudioLabCallbacks, onHearingTest: () -> Unit) {
    val colors = PulseTheme.colors

    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column {
            GlassListRow(
                Icons.Rounded.SurroundSound,
                "Spatial audio",
                subtitle = if (state.isSpatializerSupported) "Uses Android's spatializer on supported headphones" else "Not supported on this device · virtualizer used instead",
                trailing = { PulseSwitch(state.spatialAudioEnabled, { manager.setSpatialAudioEnabled(it) }) }
            )
            GlassListRow(
                Icons.Rounded.Explore,
                "Head tracking",
                subtitle = if (state.isHeadTrackingSupported) "Available · manage in system Bluetooth settings" else "Needs compatible headphones",
                onClick = callbacks.onOpenBluetoothSettings,
                trailing = { Icon(Icons.Rounded.OpenInNew, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            SoundStageIllustration(state.soundStage3D, state.virtualSurroundEnabled, state.balance)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("3D sound stage", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
                PulseSwitch(state.virtualSurroundEnabled, { manager.setVirtualSurroundEnabled(it) })
            }
            PulseSlider(state.soundStage3D.toFloat(), { manager.setSoundStage3D(it.roundToInt()) }, valueRange = 0f..100f, enabled = state.virtualSurroundEnabled, label = "Sound stage width")
            Row(Modifier.fillMaxWidth()) {
                Text("Narrow", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                Text("Wide", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ear controls", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("Applied in real time to the audio signal", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                PulseIconButton(Icons.Rounded.RestartAlt, "Reset ear controls", { manager.resetEarControls() }, tint = colors.textSecondary)
            }
            Spacer(Modifier.height(10.dp))
            LabeledSlider("Left ear", "${state.leftVolumePercent}%", state.leftVolumePercent.toFloat(), { manager.setLeftEarVolume(it.roundToInt()) }, 0f..100f)
            LabeledSlider("Right ear", "${state.rightVolumePercent}%", state.rightVolumePercent.toFloat(), { manager.setRightEarVolume(it.roundToInt()) }, 0f..100f)
            val balanceLabel = when {
                state.balance < -0.02f -> "L ${(-state.balance * 100).roundToInt()}%"
                state.balance > 0.02f -> "R ${(state.balance * 100).roundToInt()}%"
                else -> "Center"
            }
            LabeledSlider("Balance", balanceLabel, state.balance, { manager.setEarBalance(if (kotlin.math.abs(it) < 0.04f) 0f else it) }, -1f..1f, centered = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulseChip("Swap L ↔ R", state.isChannelSwapEnabled, { manager.setChannelSwap(!state.isChannelSwapEnabled) }, Modifier.weight(1f), icon = Icons.Rounded.SwapHoriz)
                PulseChip("Mono audio", state.isMonoModeEnabled, { manager.setMonoMode(!state.isMonoModeEnabled) }, Modifier.weight(1f), icon = Icons.Rounded.Hearing)
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column {
            Text("Sound enhancements", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            GlassListRow(Icons.Rounded.GraphicEq, "Bass boost", subtitle = "${state.bassBoostStrength / 10}%", trailing = { PulseSwitch(state.bassBoostEnabled, { manager.setBassBoostEnabled(it) }) })
            PulseSlider(state.bassBoostStrength.toFloat(), { manager.setBassBoostStrength(it.roundToInt()) }, valueRange = 0f..1000f, enabled = state.bassBoostEnabled, label = "Bass boost strength")
            GlassListRow(Icons.Rounded.VolumeUp, "Loudness enhancer", subtitle = "+${"%.1f".format(state.loudnessGainMb / 100f)} dB", trailing = { PulseSwitch(state.loudnessEnabled, { manager.setLoudnessEnabled(it) }) })
            PulseSlider(state.loudnessGainMb.toFloat(), { manager.setLoudnessGain(it.roundToInt()) }, valueRange = 0f..1000f, enabled = state.loudnessEnabled, label = "Loudness gain")
        }
    }

    GlassCard(Modifier.fillMaxWidth(), tint = colors.accent, contentPadding = 16.dp) {
        Column {
            GlassListRow(
                Icons.Rounded.Hearing,
                "Hearing tuner",
                subtitle = "A 2-minute tone test builds a personal profile for each ear",
                trailing = { PulseSwitch(state.isHearingCompensationEnabled, { manager.setHearingCompensation(it) }) }
            )
            Spacer(Modifier.height(8.dp))
            GradientButton("Start hearing test", onHearingTest, Modifier.fillMaxWidth(), icon = Icons.Rounded.Headphones, height = 48.dp)
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    current: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    centered: Boolean = false
) {
    val colors = PulseTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.width(84.dp))
        PulseSlider(current, onChange, Modifier.weight(1f), range, centered = centered, label = label)
        Text(value, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.width(56.dp).padding(start = 8.dp))
    }
}

/** Top-down head with sound waves; the arcs widen with the sound stage and lean with balance. */
@Composable
private fun SoundStageIllustration(stage: Int, enabled: Boolean, balance: Float) {
    val colors = PulseTheme.colors
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .glass(colors, RoundedCornerShape(20.dp))
    ) {
        val c = Offset(size.width / 2, size.height / 2)
        val head = size.height * 0.24f
        val spread = 0.4f + (if (enabled) stage / 100f else 0f) * 0.9f
        for (i in 1..4) {
            val r = head + i * size.height * 0.09f * spread
            val alpha = (0.5f - i * 0.1f).coerceAtLeast(0.08f)
            val leftAlpha = alpha * (1f - balance.coerceAtLeast(0f))
            val rightAlpha = alpha * (1f + balance.coerceAtMost(0f))
            drawArc(colors.accentPartner.copy(alpha = leftAlpha), 140f, 80f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(colors.accent.copy(alpha = rightAlpha), -40f, 80f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        // Head, ears and nose
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)), center = c, radius = head), head, c)
        drawCircle(Color.White.copy(alpha = 0.3f), head, c, style = Stroke(1.5.dp.toPx()))
        drawOval(colors.accentPartner, Offset(c.x - head - head * 0.18f, c.y - head * 0.3f), Size(head * 0.3f, head * 0.6f))
        drawOval(colors.accent, Offset(c.x + head - head * 0.12f, c.y - head * 0.3f), Size(head * 0.3f, head * 0.6f))
        val nose = Path().apply {
            moveTo(c.x - head * 0.18f, c.y - head * 0.95f)
            lineTo(c.x, c.y - head * 1.22f)
            lineTo(c.x + head * 0.18f, c.y - head * 0.95f)
        }
        drawPath(nose, Color.White.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------------------------
// Devices: Bluetooth & wired headsets
// ---------------------------------------------------------------------------------------------

@Composable
private fun DevicesTab(state: AudioLabState, settings: AppSettings, manager: AudioLabManager, callbacks: AudioLabCallbacks) {
    val colors = PulseTheme.colors
    val device = state.outputDevice

    // Current output hero
    GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = 20.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(88.dp)
                    .softGlow(colors.accent, radius = 30.dp, cornerRadius = 44.dp, alpha = 0.6f * PulseTheme.motion)
                    .clip(CircleShape)
                    .background(colors.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon((device?.type ?: AudioDeviceType.SPEAKER).icon(), null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("NOW PLAYING ON", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(device?.name ?: "This phone", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(device?.detail ?: "Phone speaker", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            if (device?.supportsHiRes == true) {
                Spacer(Modifier.height(8.dp))
                AudioBadge("Hi-Res capable · up to ${device.maxSampleRateHz / 1000} kHz", colors.warning, icon = Icons.Rounded.HighQuality)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Switch output", callbacks.onOpenOutputSwitcher, Modifier.weight(1f), icon = Icons.Rounded.Cast, height = 48.dp)
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .glass(colors, RoundedCornerShape(24.dp))
                        .clickable(onClick = callbacks.onOpenBluetoothSettings),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Bluetooth, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bluetooth", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                }
            }
        }
    }

    val others = state.connectedOutputs.filter { it.type != AudioDeviceType.SPEAKER && it.id != device?.id }
    if (others.isNotEmpty()) {
        GlassCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
            Column {
                Text("Also connected", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary, modifier = Modifier.padding(12.dp))
                others.forEach { d -> GlassListRow(d.type.icon(), d.name, subtitle = d.detail) }
            }
        }
    }

    // Per-device EQ profiles
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
        Column {
            GlassListRow(
                Icons.Rounded.Tune,
                "Device EQ profiles",
                subtitle = "Automatically apply a preset when this kind of device connects",
                trailing = { PulseSwitch(settings.perDeviceEq, callbacks.onPerDeviceEq) }
            )
            listOf(AudioDeviceType.WIRED_HEADSET, AudioDeviceType.BLUETOOTH, AudioDeviceType.USB, AudioDeviceType.SPEAKER).forEach { type ->
                val saved = state.deviceProfiles[type]
                val isCurrent = state.currentDevice == type
                GlassListRow(
                    type.icon(),
                    type.profileLabel + if (isCurrent) " · active" else "",
                    subtitle = saved?.let { "${it.displayName} preset" } ?: "Not set",
                    iconTint = if (isCurrent) colors.accentPartner else colors.accent,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (saved != null) {
                                PulseIconButton(Icons.Rounded.Close, "Clear ${type.profileLabel} profile", { manager.clearProfileFor(type) }, tint = colors.textTertiary, iconSize = 18.dp)
                            }
                            Text(
                                "Use current",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        manager.saveProfileFor(type)
                                        callbacks.onToast("${type.profileLabel} → ${state.currentPreset.displayName}")
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            )
                        }
                    }
                )
            }
        }
    }

    // Automation
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
        Column {
            Text("Headphone automation", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary, modifier = Modifier.padding(12.dp))
            GlassListRow(Icons.Rounded.PauseCircle, "Pause when unplugged", subtitle = "Wired or Bluetooth headphones disconnect", trailing = { PulseSwitch(settings.pauseOnDisconnect, callbacks.onPauseOnDisconnect) })
            GlassListRow(Icons.Rounded.PlayCircle, "Resume when reconnected", subtitle = "Within 30 minutes of an interruption", trailing = { PulseSwitch(settings.resumeOnReconnect, callbacks.onResumeOnReconnect) })
            GlassListRow(Icons.Rounded.BluetoothConnected, "Auto-play on Bluetooth", subtitle = "Start music when a car or headset connects", trailing = { PulseSwitch(settings.bluetoothAutoplay, callbacks.onBluetoothAutoplay) })
        }
    }

    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headset, null, tint = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text("Headset buttons", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            listOf(
                "1Ã— press" to "Play / pause",
                "2Ã— press" to "Next track",
                "3Ã— press" to "Previous track",
                "Play after closing app" to "Resumes your last queue"
            ).forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                }
            }
        }
    }
}
