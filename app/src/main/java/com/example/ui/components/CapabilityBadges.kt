package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SpatialAudioOff
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.PulseTheme

/** Small glass pill describing an audio capability (Dolby Atmos, Hi-Res, Spatial, Lossless). */
@Composable
fun AudioBadge(
    text: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(badgeColor.copy(alpha = 0.14f))
            .border(1.dp, badgeColor.copy(alpha = 0.35f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text = text, color = badgeColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun HiResBadge(modifier: Modifier = Modifier) =
    AudioBadge("Hi-Res", PulseAmber, modifier, Icons.Rounded.HighQuality)

@Composable
fun LosslessBadge(modifier: Modifier = Modifier) =
    AudioBadge("Lossless", PulseTheme.colors.success, modifier, Icons.Rounded.GraphicEq)

@Composable
fun SpatialBadge(modifier: Modifier = Modifier) =
    AudioBadge("Spatial", PulseTheme.colors.accent, modifier, Icons.Rounded.SpatialAudioOff)

@Composable
fun DolbyAtmosBadge(modifier: Modifier = Modifier) =
    AudioBadge("Dolby Atmos", Color(0xFF60A5FA), modifier, Icons.Rounded.SurroundSound)
