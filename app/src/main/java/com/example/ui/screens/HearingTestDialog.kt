package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiolab.ToneGenerator
import com.example.model.HearingEarCurve
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.PulseCyan
import com.example.ui.theme.PulseHotPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun HearingTestDialog(
    toneGenerator: ToneGenerator,
    onTestCompleted: (HearingEarCurve, HearingEarCurve) -> Unit,
    onDismiss: () -> Unit
) {
    val frequencies = listOf(250, 500, 1000, 2000, 4000, 8000)
    var currentFreqIndex by remember { mutableStateOf(0) }
    var currentEar by remember { mutableStateOf(ToneGenerator.HearingChannel.LEFT) } // LEFT then RIGHT
    var currentVolumeDb by remember { mutableStateOf(-45f) } // -60dB to -5dB

    val leftThresholds = remember { mutableStateMapOf<Int, Float>() }
    val rightThresholds = remember { mutableStateMapOf<Int, Float>() }

    val scope = rememberCoroutineScope()
    var isTonePlaying by remember { mutableStateOf(false) }

    val currentFreq = frequencies[currentFreqIndex]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hearing, contentDescription = null, tint = PulseCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hearing Compensation Tuner", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Testing: ${if (currentEar == ToneGenerator.HearingChannel.LEFT) "LEFT EAR" else "RIGHT EAR"}",
                    color = if (currentEar == ToneGenerator.HearingChannel.LEFT) PulseCyan else PulseHotPink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Frequency: ${currentFreq} Hz",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Put on your earphones in a quiet room. Tap 'Play Tone', then confirm if you can hear it.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Play tone button
                Button(
                    onClick = {
                        scope.launch {
                            isTonePlaying = true
                            toneGenerator.playHearingTone(
                                frequencyHz = currentFreq,
                                volumeDb = currentVolumeDb,
                                channel = currentEar,
                                durationMs = 1200L
                            )
                            isTonePlaying = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTonePlaying) PulseHotPink else PulseCyan,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isTonePlaying) "Playing..." else "Play Tone ($currentFreq Hz)")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            // User heard it! Record threshold and proceed
                            if (currentEar == ToneGenerator.HearingChannel.LEFT) {
                                leftThresholds[currentFreq] = currentVolumeDb
                            } else {
                                rightThresholds[currentFreq] = currentVolumeDb
                            }

                            // Next frequency or switch ear
                            if (currentFreqIndex < frequencies.lastIndex) {
                                currentFreqIndex++
                                currentVolumeDb = -45f
                            } else if (currentEar == ToneGenerator.HearingChannel.LEFT) {
                                currentEar = ToneGenerator.HearingChannel.RIGHT
                                currentFreqIndex = 0
                                currentVolumeDb = -45f
                            } else {
                                // Test finished! Compute compensation curves
                                val leftCurve = HearingEarCurve(
                                    leftThresholds.mapValues { (_, threshold) ->
                                        // If heard at -45dB, 0 compensation; if heard only at -10dB, +5dB compensation
                                        ((threshold - (-45f)) * 0.25f).coerceIn(0f, 6f)
                                    }
                                )
                                val rightCurve = HearingEarCurve(
                                    rightThresholds.mapValues { (_, threshold) ->
                                        ((threshold - (-45f)) * 0.25f).coerceIn(0f, 6f)
                                    }
                                )
                                onTestCompleted(leftCurve, rightCurve)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("I Hear It")
                    }

                    Button(
                        onClick = {
                            // Step volume up to test louder
                            currentVolumeDb = (currentVolumeDb + 5f).coerceAtMost(0f)
                            scope.launch {
                                isTonePlaying = true
                                toneGenerator.playHearingTone(
                                    frequencyHz = currentFreq,
                                    volumeDb = currentVolumeDb,
                                    channel = currentEar,
                                    durationMs = 1200L
                                )
                                isTonePlaying = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant, contentColor = TextPrimary)
                    ) {
                        Text("Can't Hear (Louder)")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                toneGenerator.stopTone()
                onDismiss()
            }) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = ObsidianSurfaceVariant
    )
}
