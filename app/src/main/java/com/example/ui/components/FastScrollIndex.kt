package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A–Z fast-scroll rail. Tap or drag along it to jump; the active letter is highlighted. */
@Composable
fun FastScrollIndex(
    availableLetters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = com.example.ui.theme.PulseTheme.colors
    val alphabet = remember { ('A'..'Z').toList() + '#' }
    val currentOnSelected by rememberUpdatedState(onLetterSelected)
    var active by remember { mutableStateOf<Char?>(null) }

    fun select(y: Float, height: Int) {
        val idx = ((y / height) * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
        val letter = alphabet[idx]
        if (letter != active) {
            active = letter
            currentOnSelected(letter)
        }
    }

    Column(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .glass(colors, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures { select(it.y, size.height); active = null }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { active = null },
                    onDragCancel = { active = null }
                ) { change, _ ->
                    change.consume()
                    select(change.position.y, size.height)
                }
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        alphabet.forEach { char ->
            val present = availableLetters.contains(char) || (char == '#' && availableLetters.any { !it.isLetter() })
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = if (char == active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = when {
                        char == active -> colors.accentPartner
                        present -> colors.accent
                        else -> colors.textTertiary.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.padding(vertical = 0.5.dp)
                )
            }
        }
    }
}
