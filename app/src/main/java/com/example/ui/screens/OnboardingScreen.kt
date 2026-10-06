package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme

/** Friendly music-access permission screen shown until access is granted or skipped. */
@Composable
fun OnboardingScreen(
    onGrantAccess: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    val pulse = rememberInfiniteTransition(label = "onboard")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "ring")

    AuroraBackground(modifier.fillMaxSize(), strength = 1.3f) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 0..2) {
                        val t = (ring + i / 3f) % 1f
                        drawCircle(
                            color = colors.accent.copy(alpha = (1f - t) * 0.5f),
                            radius = size.minDimension * (0.25f + t * 0.25f),
                            style = Stroke(2.dp.toPx())
                        )
                    }
                }
                Box(
                    Modifier
                        .size(150.dp)
                        .glass(colors, RoundedCornerShape(75.dp), strong = true),
                    contentAlignment = Alignment.Center
                ) {
                    PulseLogo(size = 96.dp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Welcome to Pulse", style = MaterialTheme.typography.displaySmall, color = colors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Allow access to the music on this device so Pulse can build your library. Your files never leave your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
                Column {
                    Feature(Icons.Rounded.LibraryMusic, "Your whole library", "Songs, albums, artists, folders & lyrics")
                    Feature(Icons.Rounded.Headphones, "Tuned for your headphones", "Bluetooth & wired profiles, 10-band EQ")
                    Feature(Icons.Rounded.Notifications, "Controls everywhere", "Notification, lock screen & widgets")
                }
            }
            Spacer(Modifier.weight(1f))
            GradientButton("Grant access", onGrantAccess, Modifier.fillMaxWidth(), icon = Icons.Rounded.LockOpen)
            Spacer(Modifier.height(8.dp))
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, subtitle: String) {
    GlassListRow(icon, title, subtitle = subtitle, iconTint = PulseTheme.colors.accent)
}
