package com.example.lyrics

import com.example.model.LyricsData
import com.example.model.LyricsLine
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

object LyricsEngine {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")

    fun parseLrc(lrcContent: String, userOffsetMs: Long = 0L): LyricsData {
        val lines = mutableListOf<LyricsLine>()
        var fileOffsetMs = 0L

        lrcContent.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[offset:", ignoreCase = true)) {
                val offsetStr = trimmed.substring(8, trimmed.length - 1).trim()
                fileOffsetMs = offsetStr.toLongOrNull() ?: 0L
                return@forEach
            }

            val matcher = TIME_TAG_PATTERN.matcher(trimmed)
            val timeTags = mutableListOf<Long>()
            var text = trimmed

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3) ?: "00"
                val ms = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = (min * 60 + sec) * 1000 + ms
                timeTags.add(totalMs)
            }

            text = TIME_TAG_PATTERN.matcher(text).replaceAll("").trim()

            if (text.isNotEmpty() && timeTags.isNotEmpty()) {
                for (time in timeTags) {
                    val adjusted = (time + fileOffsetMs + userOffsetMs).coerceAtLeast(0L)
                    lines.add(LyricsLine(timestampMs = adjusted, text = text))
                }
            }
        }

        val sorted = lines.sortedBy { it.timestampMs }
        return if (sorted.isNotEmpty()) {
            LyricsData(
                isSynced = true,
                lines = sorted,
                plainText = sorted.joinToString("\n") { it.text },
                offsetMs = userOffsetMs
            )
        } else {
            LyricsData(
                isSynced = false,
                lines = emptyList(),
                plainText = lrcContent,
                offsetMs = userOffsetMs
            )
        }
    }

    suspend fun getLyricsForSong(song: Song, userOffsetMs: Long = 0L): LyricsData = withContext(Dispatchers.IO) {
        // A sidecar .lrc file next to the song (same name) provides synced lyrics.
        if (song.path.isNotEmpty()) {
            val audioFile = File(song.path)
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                return@withContext parseLrc(lrcFile.readText(), userOffsetMs)
            }
        }

        LyricsData(
            isSynced = false,
            lines = emptyList(),
            plainText = "No lyrics found for \"${song.title}\".\n\nPlace a matching \".lrc\" file in the same folder as this song to enable synced lyrics.",
            offsetMs = userOffsetMs
        )
    }
}
