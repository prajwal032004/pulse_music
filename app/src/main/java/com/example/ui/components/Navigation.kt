package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.PulseTheme
import com.example.ui.viewmodel.NavigationTab

private fun NavigationTab.icon(selected: Boolean): ImageVector = when (this) {
    NavigationTab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    NavigationTab.LIBRARY -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
    NavigationTab.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
    NavigationTab.PLAYLISTS -> if (selected) Icons.Rounded.QueueMusic else Icons.Outlined.QueueMusic
    NavigationTab.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
}

@Composable
fun PulseBottomBar(
    current: NavigationTab,
    onSelect: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .softGlow(Color.Black, radius = 24.dp, cornerRadius = 28.dp, alpha = if (colors.isDark) 0.9f else 0.18f, offsetY = 8.dp)
            .darkBar(colors, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag("bottom_nav_bar"),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationTab.entries.forEach { tab ->
            val selected = tab == current
            val tint by animateColorAsState(if (selected) colors.accent else colors.textSecondary, label = "navTint")
            val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "navScale")
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(tab) }
                    .semantics { this.selected = selected; contentDescription = tab.label }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (selected) colors.accentSoft else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tab.icon(selected), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp).scale(scale))
                }
                Spacer(Modifier.height(2.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
            }
        }
    }
}

@Composable
fun PulseNavRail(
    current: NavigationTab,
    onSelect: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(96.dp)
            .padding(8.dp)
            .darkBar(colors, RoundedCornerShape(28.dp))
            .padding(vertical = 16.dp)
            .testTag("nav_rail"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PulseLogo(size = 44.dp)
        Spacer(Modifier.height(28.dp))
        NavigationTab.entries.forEach { tab ->
            val selected = tab == current
            val tint by animateColorAsState(if (selected) Color.White else colors.textSecondary, label = "railTint")
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(tab) }
                    .semantics { this.selected = selected; contentDescription = tab.label }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 36.dp)
                        .then(
                            if (selected) Modifier
                                .softGlow(colors.accent, radius = 12.dp, cornerRadius = 18.dp, alpha = 0.4f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.accentGradient)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tab.icon(selected), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (selected) colors.textPrimary else colors.textSecondary)
            }
        }
    }
}

/**
 * Deep, near-opaque navigation surface: much darker than the content glass so the bar reads as a
 * solid anchor, with a faint top-lit rim and a whisper of accent tint for depth.
 */
private fun Modifier.darkBar(colors: com.example.ui.theme.PulseColors, shape: RoundedCornerShape): Modifier {
    val fill = if (colors.isDark) {
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF111117), Color(0xFF050507)))
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F0F9)))
    }
    val rim = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(
            if (colors.isDark) Color.White.copy(alpha = 0.14f) else Color.White,
            if (colors.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.06f)
        )
    )
    return this
        .clip(shape)
        .background(fill)
        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0.06f), Color.Transparent, colors.accentPartner.copy(alpha = 0.05f))))
        .border(1.dp, rim, shape)
}

/** The Pulse app mark on its gradient glass disc. */
@Composable
fun PulseLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val colors = PulseTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .softGlow(colors.accent, radius = size / 3, cornerRadius = size / 2, alpha = 0.45f)
            .clip(CircleShape)
            .background(colors.accentGradient),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_pulse_mark),
            contentDescription = "Pulse Music",
            modifier = Modifier.size(size * 0.58f)
        )
    }
}
