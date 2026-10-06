package com.example.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseColors
import com.example.ui.theme.PulseTheme

// ---------------------------------------------------------------------------------------------
// Glass primitives
// ---------------------------------------------------------------------------------------------

val CardShape = RoundedCornerShape(24.dp)
val ChipShape = RoundedCornerShape(16.dp)

/**
 * Frosted-glass fill: a top-lit translucent gradient plus a 1dp light-catching rim.
 * Works over the aurora / blurred-artwork backdrops to give layered depth.
 */
fun Modifier.glass(
    colors: PulseColors,
    shape: Shape = CardShape,
    strong: Boolean = false,
    tint: Color? = null
): Modifier {
    val top = if (strong) colors.glassStrong else colors.glass.copy(alpha = colors.glass.alpha * 1.4f)
    val bottom = if (strong) colors.glass else colors.glass.copy(alpha = colors.glass.alpha * 0.55f)
    val fill = if (tint != null) {
        Brush.verticalGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.12f)))
    } else {
        Brush.verticalGradient(listOf(top, bottom))
    }
    val rim = Brush.linearGradient(
        0f to colors.glassHighlight,
        0.35f to colors.glassBorder,
        1f to colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.4f)
    )
    return this
        .clip(shape)
        .background(if (colors.isDark) colors.surface.copy(alpha = if (strong) 0.55f else 0.35f) else Color.Transparent)
        .background(fill)
        .border(1.dp, rim, shape)
}

/** Soft colored glow drawn behind the element (no harsh drop shadows). API 28+ only. */
fun Modifier.softGlow(
    color: Color,
    radius: Dp = 28.dp,
    cornerRadius: Dp = 24.dp,
    alpha: Float = 0.55f,
    offsetY: Dp = 6.dp
): Modifier = this.drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || alpha <= 0.01f) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = android.graphics.Color.TRANSPARENT
        frameworkPaint.setShadowLayer(radius.toPx(), 0f, offsetY.toPx(), color.copy(alpha = alpha).toArgb())
        canvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            cornerRadius.toPx(), cornerRadius.toPx(), paint
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    strong: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = PulseTheme.colors
    Box(
        modifier = modifier
            .glass(colors, shape, strong, tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * App-wide ambient backdrop: pure background plus slow drifting aurora blobs that give the
 * glass panels something to refract. Motion scales with the animation-intensity setting.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    primary: Color = PulseTheme.colors.auroraA,
    secondary: Color = PulseTheme.colors.auroraB,
    tertiary: Color = PulseTheme.colors.auroraC,
    strength: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = PulseTheme.colors
    val motion = PulseTheme.motion
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val a by animateColorAsState(primary, tween(900), label = "auroraA")
    val b by animateColorAsState(secondary, tween(900), label = "auroraB")
    val c by animateColorAsState(tertiary, tween(900), label = "auroraC")
    val baseAlpha = (if (colors.isDark) 0.34f else 0.42f) * strength

    Box(modifier = modifier.background(colors.background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = drift * motion
            val w = size.width
            val h = size.height
            val r = maxOf(w, h)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(a.copy(alpha = baseAlpha), Color.Transparent),
                    center = Offset(w * (0.10f + 0.25f * t), h * (0.05f + 0.10f * t)),
                    radius = r * 0.62f
                ),
                radius = r * 0.62f,
                center = Offset(w * (0.10f + 0.25f * t), h * (0.05f + 0.10f * t))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(b.copy(alpha = baseAlpha * 0.85f), Color.Transparent),
                    center = Offset(w * (0.95f - 0.20f * t), h * (0.40f + 0.12f * t)),
                    radius = r * 0.55f
                ),
                radius = r * 0.55f,
                center = Offset(w * (0.95f - 0.20f * t), h * (0.40f + 0.12f * t))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = baseAlpha * 0.6f), Color.Transparent),
                    center = Offset(w * (0.30f + 0.30f * (1 - t)), h * (0.92f - 0.08f * t)),
                    radius = r * 0.5f
                ),
                radius = r * 0.5f,
                center = Offset(w * (0.30f + 0.30f * (1 - t)), h * (0.92f - 0.08f * t))
            )
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons & chips
// ---------------------------------------------------------------------------------------------

/** Press feedback: a subtle spring scale-down. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 600f), label = "press")
    return this.scale(scale)
}

@Composable
fun GradientPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    glow: Boolean = true
) {
    val colors = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction)
            .then(if (glow) Modifier.softGlow(colors.accent, radius = size * 0.35f, cornerRadius = size / 2, alpha = 0.65f * PulseTheme.motion.coerceAtLeast(0.4f)) else Modifier)
            .clip(CircleShape)
            .background(colors.accentGradient)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 56.dp
) {
    val colors = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = height)
            .pressScale(interaction)
            .softGlow(colors.accent, radius = 22.dp, cornerRadius = height / 2, alpha = 0.45f * PulseTheme.motion.coerceAtLeast(0.3f))
            .clip(RoundedCornerShape(50))
            .background(colors.accentGradient)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    tint: Color = PulseTheme.colors.textPrimary,
    active: Boolean = false
) {
    val colors = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction)
            .then(
                if (active) Modifier.clip(CircleShape).background(colors.accentGradient)
                else Modifier.glass(colors, CircleShape)
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Color.White else tint, modifier = Modifier.size(iconSize))
    }
}

/** Plain round icon button with a 48dp touch target and no container. */
@Composable
fun PulseIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PulseTheme.colors.textPrimary,
    iconSize: Dp = 24.dp
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PulseChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null
) {
    val colors = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .pressScale(interaction)
            .then(
                if (selected) Modifier
                    .softGlow(colors.accent, radius = 14.dp, cornerRadius = 16.dp, alpha = 0.35f * PulseTheme.motion)
                    .clip(ChipShape)
                    .background(colors.accentGradient)
                else Modifier.glass(colors, ChipShape)
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else iconTint ?: colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = if (selected) Color.White else colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = PulseTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun PulseSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = PulseTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.glassStrong,
            uncheckedBorderColor = colors.glassBorder
        )
    )
}

/** A settings-style row: leading icon tile, title/subtitle, trailing slot. */
@Composable
fun GlassListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = PulseTheme.colors.accent,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = PulseTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = PulseTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .softGlow(colors.accent, radius = 40.dp, cornerRadius = 48.dp, alpha = 0.4f)
                .glass(colors, CircleShape, strong = true),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            GradientButton(actionLabel, onAction, height = 48.dp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sliders
// ---------------------------------------------------------------------------------------------

/**
 * Horizontal gradient slider with a glowing thumb. [centered] draws the active range from the
 * middle (for balance / gain controls).
 */
@Composable
fun PulseSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    centered: Boolean = false,
    enabled: Boolean = true,
    gradient: List<Color>? = null,
    label: String? = null
) {
    val colors = PulseTheme.colors
    val gradientColors = gradient ?: colors.accentGradientColors
    val currentOnChange by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun toValue(x: Float, width: Float): Float =
        valueRange.start + (x / width).coerceIn(0f, 1f) * span

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                if (label != null) contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { target ->
                    currentOnChange(target.coerceIn(valueRange.start, valueRange.endInclusive)); true
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { currentOnChange(toValue(it.x, size.width.toFloat())) }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnChange(toValue(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        val trackH = 6.dp.toPx()
        val cy = size.height / 2
        val thumbR = 9.dp.toPx()
        val usable = size.width - thumbR * 2
        val thumbX = thumbR + usable * fraction
        drawRoundRect(
            color = colors.glassStrong,
            topLeft = Offset(thumbR, cy - trackH / 2),
            size = Size(usable, trackH),
            cornerRadius = CornerRadius(trackH / 2)
        )
        val startX = if (centered) thumbR + usable / 2 else thumbR
        val left = minOf(startX, thumbX)
        val right = maxOf(startX, thumbX)
        val alpha = if (enabled) 1f else 0.35f
        if (right - left > 0.5f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(gradientColors.map { it.copy(alpha = alpha) }, startX = thumbR, endX = thumbR + usable),
                topLeft = Offset(left, cy - trackH / 2),
                size = Size(right - left, trackH),
                cornerRadius = CornerRadius(trackH / 2)
            )
        }
        drawCircle(gradientColors.last().copy(alpha = 0.28f * alpha), radius = thumbR * 1.9f, center = Offset(thumbX, cy))
        drawCircle(Color.White.copy(alpha = if (enabled) 1f else 0.5f), radius = thumbR, center = Offset(thumbX, cy))
        drawCircle(gradientColors.last().copy(alpha = alpha), radius = thumbR * 0.45f, center = Offset(thumbX, cy))
    }
}

/** Vertical glowing EQ band slider. [value] is in [valueRange], zero line drawn at center. */
@Composable
fun VerticalGlowSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -15f..15f,
    enabled: Boolean = true,
    label: String? = null
) {
    val colors = PulseTheme.colors
    val currentOnChange by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(fraction, tween(160, easing = FastOutSlowInEasing), label = "eqBand")

    fun toValue(y: Float, height: Float): Float =
        valueRange.start + (1f - (y / height).coerceIn(0f, 1f)) * span

    Canvas(
        modifier = modifier
            .width(36.dp)
            .semantics {
                if (label != null) contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { target -> currentOnChange(target.coerceIn(valueRange.start, valueRange.endInclusive)); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { currentOnChange(toValue(it.y, size.height.toFloat())) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    currentOnChange(toValue(change.position.y, size.height.toFloat()))
                }
            }
    ) {
        val trackW = 6.dp.toPx()
        val cx = size.width / 2
        val thumbR = 9.dp.toPx()
        val usable = size.height - thumbR * 2
        val thumbY = thumbR + usable * (1f - animatedFraction)
        val zeroY = thumbR + usable / 2
        val alpha = if (enabled) 1f else 0.35f

        drawRoundRect(
            color = colors.glassStrong,
            topLeft = Offset(cx - trackW / 2, thumbR),
            size = Size(trackW, usable),
            cornerRadius = CornerRadius(trackW / 2)
        )
        val top = minOf(zeroY, thumbY)
        val bottom = maxOf(zeroY, thumbY)
        val brush = Brush.verticalGradient(
            listOf(colors.accentPartner.copy(alpha = alpha), colors.accent.copy(alpha = alpha)),
            startY = thumbR, endY = thumbR + usable
        )
        // Glow halo behind the active segment
        drawRoundRect(
            brush = brush,
            topLeft = Offset(cx - trackW * 1.6f, top),
            size = Size(trackW * 3.2f, (bottom - top).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(trackW * 1.6f),
            alpha = 0.22f
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(cx - trackW / 2, top),
            size = Size(trackW, (bottom - top).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(trackW / 2)
        )
        drawCircle(colors.accentPartner.copy(alpha = 0.3f * alpha), radius = thumbR * 1.8f, center = Offset(cx, thumbY))
        drawCircle(Color.White.copy(alpha = if (enabled) 1f else 0.5f), radius = thumbR, center = Offset(cx, thumbY))
    }
}

/** Mini equalizer bars that bounce while [playing]; used to mark the active row. */
@Composable
fun NowPlayingBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = PulseTheme.colors.accent) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 360).map { delay ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar$delay"
        )
    }
    Canvas(modifier = modifier.size(18.dp)) {
        val barW = size.width / 5
        phases.forEachIndexed { i, anim ->
            val h = size.height * (if (playing) anim.value else 0.35f)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barW * 2, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2)
            )
        }
    }
}
