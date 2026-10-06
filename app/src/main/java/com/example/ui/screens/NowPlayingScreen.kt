package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.model.*
import com.example.model.RepeatMode
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import kotlin.math.roundToInt

/** Callbacks for everything the player surface can do. */
class NowPlayingActions(
    val onCollapse: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onSpeedChange: (Float) -> Unit,
    val onToggleFavorite: (Song) -> Unit,
    val onShare: (Song) -> Unit,
    val onMore: (Song) -> Unit,
    val onStartSleepTimer: (Int) -> Unit,
    val onSleepEndOfTrack: () -> Unit,
    val onCancelSleepTimer: () -> Unit,
    val onSleepFadeChange: (Boolean) -> Unit,
    val onReorderQueue: (Int, Int) -> Unit,
    val onRemoveFromQueue: (Int) -> Unit,
    val onClearQueue: () -> Unit,
    val onSkipTo: (Int) -> Unit,
    val onSaveQueue: (String) -> Unit,
    val onOpenAudioLab: (Int) -> Unit,
    val onOpenLyrics: () -> Unit,
    val onOpenOutputSwitcher: () -> Unit,
    val onPitchChange: (Float) -> Unit = {},
    val onCycleABLoop: () -> Unit = {}
)

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    audioLabState: AudioLabState,
    sleepFadeOut: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
    isPane: Boolean = false
) {
    val song = playbackState.currentSong ?: return
    val colors = PulseTheme.colors
    val palette = remember(song.artworkKey()) { artworkPalette(song.artworkKey()) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().testTag("now_playing_screen")) {
        PlayerBackdrop(song, palette)

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (isPane) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())
        ) {
            val landscape = !isPane && maxWidth > maxHeight && maxWidth >= 560.dp
            Column(Modifier.fillMaxSize()) {
                PlayerTopBar(song, playbackState, isPane, actions)
                if (landscape) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerArtwork(song, playbackState, actions, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                        Spacer(Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            PlayerDetails(song, playbackState, audioLabState, actions, onQueue = { showQueue = true }, onSleep = { showSleep = true }, onSpeed = { showSpeed = true })
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.weight(0.15f))
                        PlayerArtwork(song, playbackState, actions, Modifier.fillMaxWidth(if (isPane) 0.78f else 0.9f).aspectRatio(1f))
                        Spacer(Modifier.weight(0.2f))
                        PlayerDetails(song, playbackState, audioLabState, actions, onQueue = { showQueue = true }, onSleep = { showSleep = true }, onSpeed = { showSpeed = true })
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showQueue) QueueSheet(playbackState, actions, onDismiss = { showQueue = false })
    if (showSleep) SleepTimerSheet(playbackState, sleepFadeOut, actions, onDismiss = { showSleep = false })
    if (showSpeed) SpeedSheet(playbackState.speed, playbackState.pitch, actions.onSpeedChange, actions.onPitchChange, onDismiss = { showSpeed = false })
}

@Composable
private fun PlayerBackdrop(song: Song, palette: List<Color>) {
    val colors = PulseTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        AnimatedContent(targetState = song.artworkKey(), transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) }, label = "backdrop") { _ ->
            Box(Modifier.fillMaxSize()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SongArtwork(
                        song,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = if (colors.isDark) 0.75f else 0.6f }
                            .blur(90.dp),
                        RoundedCornerShape(0.dp)
                    )
                } else {
                    AuroraBackground(Modifier.fillMaxSize(), palette[0], palette[1], palette[2], strength = 1.6f)
                }
            }
        }
        // Scrim so glass panels and text keep contrast over bright artwork
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (colors.isDark) listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))
                        else listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.55f), colors.background.copy(alpha = 0.95f))
                    )
                )
        )
    }
}

@Composable
private fun PlayerTopBar(song: Song, state: PlaybackState, isPane: Boolean, actions: NowPlayingActions) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isPane) {
            GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Collapse player", actions.onCollapse, modifier = Modifier.testTag("now_playing_collapse_button"))
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM QUEUE", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(
                listOfNotNull(
                    if (state.queue.size > 1) "${state.queueIndex + 1} of ${state.queue.size}" else null,
                    song.album.takeIf { song.hasAlbumTag && it.isNotBlank() }
                ).joinToString(" · ").ifEmpty { song.artist },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GlassIconButton(Icons.Rounded.MoreVert, "More options", { actions.onMore(song) })
    }
}

@Composable
private fun PlayerArtwork(song: Song, state: PlaybackState, actions: NowPlayingActions, modifier: Modifier) {
    val palette = remember(song.artworkKey()) { artworkPalette(song.artworkKey()) }
    val motion = PulseTheme.motion
    val scale by animateFloatAsState(if (state.isPlaying) 1f else 0.9f, spring(dampingRatio = 0.6f, stiffness = 200f), label = "artScale")
    var drag by remember { mutableFloatStateOf(0f) }
    val dragAnim by animateFloatAsState(drag, label = "artDrag")
    // Double-tap the artwork to like: a heart bursts out of the cover.
    var burstKey by remember { mutableIntStateOf(0) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(burstKey) {
        if (burstKey > 0) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        }
    }
    val spin = rememberInfiniteTransition(label = "vinyl")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "angle")

    Box(modifier, contentAlignment = Alignment.Center) {
        // Soft "vinyl" glow: rotating conic halo in the artwork's colors
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.12f * scale; scaleY = 1.12f * scale; rotationZ = if (state.isPlaying) angle * motion else 0f; alpha = 0.55f * motion.coerceAtLeast(0.3f) }
                .blur(40.dp)
        ) {
            drawCircle(Brush.sweepGradient(listOf(palette[0], palette[1], palette[2], palette[0])), radius = size.minDimension * 0.46f)
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.06f * scale; scaleY = 1.06f * scale }) {
            for (i in 1..3) {
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.minDimension * (0.36f + i * 0.045f), style = Stroke(1.dp.toPx()))
            }
        }
        AnimatedContent(
            targetState = song,
            transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(250)) },
            label = "artwork"
        ) { s ->
            SongArtwork(
                s,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = dragAnim * 0.5f
                        rotationZ = dragAnim / 60f
                    }
                    .softGlow(palette.first(), radius = 48.dp, cornerRadius = 32.dp, alpha = 0.55f * motion)
                    .pointerInput(s.id) {
                        detectTapGestures(onDoubleTap = {
                            if (!s.isFavorite) actions.onToggleFavorite(s)
                            burstKey++
                        })
                    }
                    .draggable(
                        rememberDraggableState { drag += it },
                        Orientation.Horizontal,
                        onDragStopped = {
                            if (drag < -140) actions.onNext() else if (drag > 140) actions.onPrevious()
                            drag = 0f
                        }
                    ),
                RoundedCornerShape(32.dp),
                iconSize = 72.dp
            )
        }
        if (burst.value in 0.001f..0.999f) {
            val p = burst.value
            Icon(
                Icons.Rounded.Favorite,
                null,
                tint = Color.White,
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        val grow = if (p < 0.35f) p / 0.35f else 1f + (p - 0.35f) * 0.25f
                        scaleX = 0.4f + grow * 0.8f; scaleY = scaleX
                        alpha = if (p < 0.6f) 1f else (1f - p) / 0.4f
                    }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerDetails(
    song: Song,
    state: PlaybackState,
    lab: AudioLabState,
    actions: NowPlayingActions,
    onQueue: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit
) {
    val colors = PulseTheme.colors
    var scrubMs by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val heartScale by animateFloatAsState(if (song.isFavorite) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "heart")
            PulseIconButton(
                if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (song.isFavorite) "Remove from Liked Songs" else "Add to Liked Songs",
                { actions.onToggleFavorite(song) },
                tint = if (song.isFavorite) colors.accentPartner else colors.textPrimary,
                modifier = Modifier.graphicsLayer { scaleX = heartScale; scaleY = heartScale }.testTag("now_playing_favorite_button")
            )
            PulseIconButton(Icons.Rounded.IosShare, "Share", { actions.onShare(song) })
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (lab.isDolbyAtmosDetected) DolbyAtmosBadge()
            if (song.isHiRes) HiResBadge() else if (song.isLossless) LosslessBadge()
            if (lab.spatialAudioEnabled) SpatialBadge()
            Spacer(Modifier.weight(1f))
            lab.outputDevice?.let { device ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .glass(colors, RoundedCornerShape(12.dp))
                        .clickable(onClick = actions.onOpenOutputSwitcher)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(device.type.icon(), "Audio output: ${device.name}", tint = colors.accent, modifier = Modifier.size(16.dp))
                    if (device.type != AudioDeviceType.SPEAKER) {
                        Spacer(Modifier.width(6.dp))
                        Text(device.name, style = MaterialTheme.typography.labelSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        WaveformSeekBar(
            currentPositionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            onSeek = actions.onSeek,
            seed = song.id,
            onScrub = { scrubMs = it }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(scrubMs ?: state.currentPositionMs), style = MaterialTheme.typography.labelMedium, color = if (scrubMs != null) colors.accent else colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text("-" + formatDuration((state.durationMs - (scrubMs ?: state.currentPositionMs)).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulseIconButton(
                Icons.Rounded.Shuffle,
                if (state.shuffleMode == ShuffleMode.ON) "Shuffle on" else "Shuffle off",
                actions.onToggleShuffle,
                tint = if (state.shuffleMode == ShuffleMode.ON) colors.accent else colors.textSecondary
            )
            GlassIconButton(Icons.Rounded.SkipPrevious, "Previous", actions.onPrevious, size = 60.dp, iconSize = 32.dp)
            GradientPlayButton(state.isPlaying, actions.onPlayPause, size = 84.dp, modifier = Modifier.testTag("now_playing_play_pause_button"))
            GlassIconButton(Icons.Rounded.SkipNext, "Next", actions.onNext, size = 60.dp, iconSize = 32.dp)
            PulseIconButton(
                if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (state.repeatMode) { RepeatMode.OFF -> "Repeat off"; RepeatMode.ALL -> "Repeat all"; RepeatMode.ONE -> "Repeat one" },
                actions.onToggleRepeat,
                tint = if (state.repeatMode != RepeatMode.OFF) colors.accent else colors.textSecondary
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            val tool = Modifier.weight(1f)
            PlayerToolButton(Icons.Rounded.Lyrics, "Lyrics", onClick = actions.onOpenLyrics, modifier = tool)
            PlayerToolButton(Icons.Rounded.QueueMusic, "Queue", onClick = onQueue, modifier = tool)
            PlayerToolButton(Icons.Rounded.GraphicEq, "EQ", active = lab.equalizerEnabled && lab.currentPreset != EqPreset.FLAT, onClick = { actions.onOpenAudioLab(0) }, modifier = tool)
            val abText = when {
                state.loopPointA == null -> "A-B"
                state.loopPointB == null -> "A…"
                else -> "A↔B"
            }
            PlayerToolButton(null, "Loop", text = abText, active = state.loopPointA != null, onClick = actions.onCycleABLoop, modifier = tool)
            val sleepLabel = when {
                state.sleepAtEndOfTrack -> "End"
                state.sleepTimerRemainingSeconds != null -> formatDuration(state.sleepTimerRemainingSeconds * 1000)
                else -> "Sleep"
            }
            PlayerToolButton(Icons.Rounded.Bedtime, sleepLabel, active = state.sleepTimerRemainingSeconds != null || state.sleepAtEndOfTrack, onClick = onSleep, modifier = tool)
            PlayerToolButton(null, "Speed", text = "${"%.2f".format(java.util.Locale.US, state.speed).trimEnd('0').trimEnd('.')}x", active = state.speed != 1f || state.pitch != 1f, onClick = onSpeed, modifier = tool)
        }
    }
}

@Composable
private fun PlayerToolButton(icon: ImageVector?, label: String, onClick: () -> Unit, active: Boolean = false, text: String? = null, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(46.dp)
                .then(if (active) Modifier.clip(CircleShape).background(colors.accentGradient) else Modifier.glass(colors, CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(icon, label, tint = if (active) Color.White else colors.textPrimary, modifier = Modifier.size(22.dp))
            else Text(text ?: "", style = MaterialTheme.typography.labelMedium, color = if (active) Color.White else colors.textPrimary, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
// Queue sheet: drag handles to reorder, swipe to remove, shuffle / clear / save actions
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlaybackState, actions: NowPlayingActions, onDismiss: () -> Unit) {
    val colors = PulseTheme.colors
    val current = state.currentSong ?: return
    var saving by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val rowHeight = 72.dp
    val rowPx = with(density) { rowHeight.toPx() }
    val upNext = state.upNextIndices.filter { it in state.queue.indices }
    val canReorder = state.shuffleMode == ShuffleMode.OFF
    var dragging by remember { mutableStateOf<Int?>(null) } // position within upNext
    var dragOffset by remember { mutableFloatStateOf(0f) }

    PulseBottomSheet(onDismiss = onDismiss) {
        Text("Queue", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 20.dp))
        SongRow(current, isCurrent = true, isPlaying = state.isPlaying, onClick = {}, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), trailing = {})
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("UP NEXT · ${upNext.size}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(
                if (canReorder) "Hold ≡ to reorder · swipe to remove" else "Swipe to remove · turn off shuffle to reorder",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(upNext, key = { _, qi -> "${state.queue[qi].id}_$qi" }) { pos, qIndex ->
                val song = state.queue[qIndex]
                val isDragging = dragging == pos
                val target = dragging?.let { (it + (dragOffset / rowPx).roundToInt()).coerceIn(0, upNext.lastIndex) }
                val shift = when {
                    dragging == null || target == null -> 0f
                    isDragging -> dragOffset
                    pos in (dragging!! + 1)..target -> -rowPx
                    pos in target until dragging!! -> rowPx
                    else -> 0f
                }
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            actions.onRemoveFromQueue(qIndex); true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = shift; scaleX = if (isDragging) 1.02f else 1f; scaleY = if (isDragging) 1.02f else 1f },
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.accentPartner.copy(alpha = 0.25f))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = colors.accentPartner) }
                    }
                ) {
                    SongRow(
                        song = song,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { actions.onSkipTo(qIndex) },
                        showDuration = false,
                        modifier = Modifier
                            .height(rowHeight)
                            .padding(horizontal = 12.dp)
                            .then(if (isDragging) Modifier.glass(colors, RoundedCornerShape(18.dp), strong = true) else Modifier.background(colors.surface.copy(alpha = 0.01f))),
                        trailing = {
                            if (canReorder) {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .pointerInput(qIndex, upNext) {
                                            detectDragGestures(
                                                onDragStart = { dragging = pos; dragOffset = 0f },
                                                onDragEnd = {
                                                    val from = dragging
                                                    val to = from?.let { (it + (dragOffset / rowPx).roundToInt()).coerceIn(0, upNext.lastIndex) }
                                                    if (from != null && to != null && from != to) actions.onReorderQueue(upNext[from], upNext[to])
                                                    dragging = null; dragOffset = 0f
                                                },
                                                onDragCancel = { dragging = null; dragOffset = 0f }
                                            ) { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = colors.textSecondary) }
                            }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QueueAction(Icons.Rounded.Shuffle, "Shuffle", Modifier.weight(1f), active = state.shuffleMode == ShuffleMode.ON, onClick = actions.onToggleShuffle)
            QueueAction(Icons.Rounded.ClearAll, "Clear", Modifier.weight(1f), onClick = actions.onClearQueue)
            QueueAction(Icons.Rounded.PlaylistAdd, "Save", Modifier.weight(1f), onClick = { saving = true })
        }
    }

    if (saving) {
        TextInputDialog(
            title = "Save queue as playlist",
            placeholder = "Queue ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())}",
            confirmLabel = "Save",
            onDismiss = { saving = false },
            onConfirm = { actions.onSaveQueue(it); saving = false }
        )
    }
}

@Composable
private fun QueueAction(icon: ImageVector, label: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Column(
        modifier
            .height(72.dp)
            .then(if (active) Modifier.clip(RoundedCornerShape(20.dp)).background(colors.accentGradient) else Modifier.glass(colors, RoundedCornerShape(20.dp)))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (active) Color.White else colors.accent)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) Color.White else colors.textPrimary)
    }
}

// ---------------------------------------------------------------------------------------------
// Sleep timer & speed sheets
// ---------------------------------------------------------------------------------------------

@Composable
private fun SleepTimerSheet(state: PlaybackState, fadeOut: Boolean, actions: NowPlayingActions, onDismiss: () -> Unit) {
    val colors = PulseTheme.colors
    val active = state.sleepTimerRemainingSeconds != null || state.sleepAtEndOfTrack
    PulseBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text("Sleep timer", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            }
            if (active) {
                Spacer(Modifier.height(16.dp))
                GlassCard(Modifier.fillMaxWidth(), tint = colors.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Music stops in", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                            Text(
                                if (state.sleepAtEndOfTrack) "End of this track" else formatDuration((state.sleepTimerRemainingSeconds ?: 0) * 1000),
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.textPrimary
                            )
                        }
                        GradientButton("Turn off", { actions.onCancelSleepTimer(); onDismiss() }, height = 44.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val presets = listOf(5, 10, 15, 30, 45, 60, 90)
            presets.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { m ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .glass(colors, RoundedCornerShape(20.dp))
                                .clickable { actions.onStartSleepTimer(m); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$m", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                                Text("min", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                        }
                    }
                    if (row.size < 4) {
                        Box(
                            Modifier
                                .weight((4 - row.size).toFloat())
                                .height(64.dp)
                                .glass(colors, RoundedCornerShape(20.dp))
                                .clickable { actions.onSleepEndOfTrack(); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MusicNote, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("End of track", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
            GlassListRow(
                Icons.Rounded.VolumeDown,
                "Fade out",
                subtitle = "Gently lower the volume in the last 30 seconds",
                trailing = { PulseSwitch(fadeOut, actions.onSleepFadeChange) }
            )
        }
    }
}

@Composable
private fun SpeedSheet(speed: Float, pitch: Float, onChange: (Float) -> Unit, onPitchChange: (Float) -> Unit, onDismiss: () -> Unit) {
    val colors = PulseTheme.colors
    PulseBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Playback speed", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text("${"%.2f".format(java.util.Locale.US, speed)}x", style = MaterialTheme.typography.titleLarge, color = colors.accent)
            }
            Spacer(Modifier.height(16.dp))
            PulseSlider(
                value = speed,
                onValueChange = { onChange((it * 20).roundToInt() / 20f) },
                valueRange = 0.5f..2f,
                label = "Playback speed"
            )
            Row(Modifier.fillMaxWidth()) {
                Text("0.5x", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                Text("2.0x", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    PulseChip("${s}x", selected = speed == s, onClick = { onChange(s) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
            // Pitch shifts the key without changing tempo (shown in semitones).
            val semitones = (12 * kotlin.math.ln(pitch) / kotlin.math.ln(2f)).roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pitch", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Text("Change the key without changing tempo", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Text(if (semitones == 0) "Original" else "${if (semitones > 0) "+" else ""}$semitones st", style = MaterialTheme.typography.titleLarge, color = colors.accent)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.Remove, "Lower pitch", { onPitchChange(semitoneRatio(semitones - 1)) }, size = 44.dp)
                PulseSlider(
                    value = semitones.toFloat(),
                    onValueChange = { onPitchChange(semitoneRatio(it.roundToInt())) },
                    valueRange = -6f..6f,
                    centered = true,
                    label = "Pitch",
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                GlassIconButton(Icons.Rounded.Add, "Raise pitch", { onPitchChange(semitoneRatio(semitones + 1)) }, size = 44.dp)
            }
            if (speed != 1f || pitch != 1f) {
                Spacer(Modifier.height(16.dp))
                GradientButton("Reset to normal", { onChange(1f); onPitchChange(1f) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.RestartAlt, height = 48.dp)
            }
        }
    }
}

private fun semitoneRatio(semitones: Int): Float =
    Math.pow(2.0, semitones.coerceIn(-6, 6) / 12.0).toFloat()
