package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Builds the library from every audio file stored on the device (internal storage and SD cards),
 * including downloads that Android hasn't tagged as "music" or hasn't indexed yet.
 */
class MediaStoreScanner(private val context: Context) {

    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "alac", "aiff", "aif", "ape", "mka", "weba", "3gp")

    /** Voice notes, call recordings and system sounds are not music. */
    private val excludedPathParts = listOf(
        "/whatsapp voice notes/", "/voice notes/", "/call recordings/", "/callrecordings/", "/call/",
        "/recordings/", "/voice recorder/", "/soundrecorder/", "/ringtones/", "/notifications/",
        "/alarms/", "/android/data/", "/android/obb/"
    )

    /**
     * @param minDurationSec tracks shorter than this are treated as clips/voice notes and skipped
     * (files whose duration Android hasn't measured yet are always kept).
     */
    suspend fun scanAudioLibrary(favoriteIds: Set<Long>, minDurationSec: Int = 20): LibraryData = withContext(Dispatchers.IO) {
        // Remove the tone files a very old version generated as sample content.
        runCatching { File(context.filesDir, "pulse_demo_music").deleteRecursively() }

        var songs = queryMediaStore(favoriteIds, minDurationSec)
        // Make sure freshly downloaded files are indexed, then re-read so they appear right away.
        if (indexUnscannedFiles(songs.mapTo(HashSet()) { it.path.lowercase() }) > 0) {
            songs = queryMediaStore(favoriteIds, minDurationSec)
        }
        buildLibrary(songs)
    }

    private fun queryMediaStore(favoriteIds: Set<Long>, minDurationSec: Int): List<Song> {
        val songs = ArrayList<Song>()
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            columns += MediaStore.Audio.Media.GENRE
            columns += MediaStore.Audio.Media.BITRATE
        }

        // Everything except ringtones, alarms, notification sounds and (Android 12+) recordings.
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_RINGTONE} = 0")
            append(" AND ${MediaStore.Audio.Media.IS_ALARM} = 0")
            append(" AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) append(" AND ${MediaStore.Audio.Media.IS_RECORDING} = 0")
            // Unknown (0/NULL) durations are kept: new downloads are often not measured yet.
            append(" AND (${MediaStore.Audio.Media.DURATION} IS NULL OR ${MediaStore.Audio.Media.DURATION} = 0 OR ${MediaStore.Audio.Media.DURATION} >= ${minDurationSec.coerceAtLeast(0) * 1000L})")
            append(" AND ${MediaStore.Audio.Media.SIZE} > 150000")
        }

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                columns.toTypedArray(),
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
                val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1

                while (c.moveToNext()) {
                    val path = c.getString(dataCol).orEmpty()
                    val lowerPath = path.lowercase()
                    if (excludedPathParts.any { lowerPath.contains(it) }) continue

                    val id = c.getLong(idCol)
                    val fileName = c.getString(nameCol).orEmpty()
                    val baseName = fileName.substringBeforeLast('.')
                    val rawTitle = clean(c.getString(titleCol))
                    // Untagged files get their file name as title: make "My_song__name" read as "My song name".
                    val title = if (rawTitle == null || rawTitle == baseName) prettifyFileName(baseName) else rawTitle
                    val artist = clean(c.getString(artistCol)) ?: "Unknown artist"
                    val folderName = File(path).parentFile?.name
                    val tagAlbum = clean(c.getString(albumCol))
                    // Android uses the folder name as album when the file has no album tag.
                    val hasAlbumTag = tagAlbum != null && !tagAlbum.equals(folderName, ignoreCase = true)
                    val album = tagAlbum ?: folderName ?: "Unknown album"
                    val durationMs = c.getLong(durationCol)
                    val albumId = c.getLong(albumIdCol)
                    val mimeType = c.getString(mimeCol) ?: "audio/mpeg"
                    val size = c.getLong(sizeCol)
                    val tagGenre = if (genreCol >= 0) clean(c.getString(genreCol)) else null

                    val isLossless = mimeType.contains("flac", true) || mimeType.contains("wav", true) ||
                        lowerPath.endsWith(".flac") || lowerPath.endsWith(".wav") || lowerPath.endsWith(".alac") || lowerPath.endsWith(".aiff")
                    // Average bitrate from size and length is exact for CBR and VBR alike; the indexed value
                    // often reflects only the first (Xing/VBR header) frame, e.g. "32 kbps" for a 220 kbps MP3.
                    val indexedBps = if (bitrateCol >= 0) c.getInt(bitrateCol) else 0
                    val kbps = when {
                        durationMs > 0 && size > 0 -> (size * 8 / durationMs).toInt()
                        indexedBps > 0 -> indexedBps / 1000
                        else -> 0
                    }
                    // CD-quality FLAC averages ~700-1000 kbps; 24-bit/48kHz+ material is clearly above that.
                    val isHiRes = isLossless && kbps >= 1600

                    songs += Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId),
                        genre = tagGenre ?: inferGenreFromPathOrTitle(path, title),
                        year = c.getInt(yearCol),
                        trackNumber = c.getInt(trackCol) % 1000,
                        size = size,
                        dateAdded = c.getLong(dateCol) * 1000L,
                        path = path,
                        mimeType = mimeType,
                        bitRate = kbps,
                        isHiRes = isHiRes,
                        isLossless = isLossless,
                        isFavorite = favoriteIds.contains(id),
                        hasAlbumTag = hasAlbumTag
                    )
                }
            }
        }
        // The same file can be listed twice across volumes; keep one.
        return songs.distinctBy { if (it.path.isNotEmpty()) it.path.lowercase() else it.id.toString() }
    }

    private fun prettifyFileName(name: String): String {
        val pretty = name.replace('_', ' ').replace(Regex("\\s{2,}"), " ").trim()
        return pretty.ifBlank { "Unknown title" }
    }

    private fun clean(value: String?): String? {
        val v = value?.trim().orEmpty()
        return if (v.isEmpty() || v.equals("<unknown>", true) || v.equals("unknown", true)) null else v
    }

    /**
     * Walks the usual download/music folders for audio files Android hasn't indexed yet (common
     * right after a download) and asks the media scanner to index them. Returns how many were found.
     */
    private suspend fun indexUnscannedFiles(knownPaths: Set<String>): Int {
        val roots = storageRoots()
        val candidates = listOf(
            Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOWNLOADS, "Downloads", "Audio", "Songs", "Media",
            Environment.DIRECTORY_DOCUMENTS, "Telegram", "Snaptube", "VidMate", "Bluetooth",
            "Android/media/org.telegram.messenger/Telegram/Telegram Audio",
            "Android/media/org.telegram.messenger/Telegram/Telegram Documents"
        )
        val found = ArrayList<String>()
        for (root in roots) {
            for (dir in candidates) {
                collectAudio(File(root, dir), knownPaths, found, depth = 0)
                if (found.size >= 3000) break
            }
        }
        if (found.isEmpty()) return 0

        val remaining = AtomicInteger(found.size)
        withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(context, found.toTypedArray(), null) { _, _ ->
                    if (remaining.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                }
            }
        }
        return found.size
    }

    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        roots += Environment.getExternalStorageDirectory()
        // SD cards: /storage/XXXX-XXXX derived from app-specific dirs on each volume.
        context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
            val path = appDir.absolutePath
            val idx = path.indexOf("/Android/")
            if (idx > 0) roots += File(path.substring(0, idx))
        }
        return roots.filter { it.exists() }
    }

    private fun collectAudio(dir: File, known: Set<String>, out: MutableList<String>, depth: Int) {
        if (depth > 6 || out.size >= 3000 || !dir.isDirectory || dir.name.startsWith(".")) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        if (children.any { it.name == ".nomedia" }) return
        for (f in children) {
            if (f.isDirectory) {
                collectAudio(f, known, out, depth + 1)
            } else {
                val lower = f.absolutePath.lowercase()
                if (f.extension.lowercase() in audioExtensions && f.length() > 150_000 &&
                    lower !in known && excludedPathParts.none { lower.contains(it) }
                ) out += f.absolutePath
            }
        }
    }

    private fun buildLibrary(allSongs: List<Song>): LibraryData {
        val albums = allSongs.groupBy { it.album }.entries.mapIndexed { index, entry ->
            val songList = entry.value
            Album(
                id = index.toLong() + 1,
                title = entry.key,
                artist = songList.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "Unknown artist",
                artUri = songList.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                songCount = songList.size,
                year = songList.maxOfOrNull { it.year } ?: 0
            )
        }.sortedBy { it.title.lowercase() }

        val artists = allSongs.groupBy { it.artist }.entries.mapIndexed { index, entry ->
            Artist(
                id = index.toLong() + 1,
                name = entry.key,
                songCount = entry.value.size,
                albumCount = entry.value.map { it.album }.distinct().size
            )
        }.sortedBy { it.name.lowercase() }

        val genres = allSongs.groupBy { it.genre }.entries.mapIndexed { index, entry ->
            Genre(id = index.toLong() + 1, name = entry.key, songCount = entry.value.size)
        }.sortedBy { it.name.lowercase() }

        val folders = allSongs.groupBy { File(it.path).parent ?: "Internal storage" }.entries.map { entry ->
            Folder(
                path = entry.key,
                name = File(entry.key).name.ifEmpty { "Music" },
                songCount = entry.value.size
            )
        }.sortedBy { it.name.lowercase() }

        return LibraryData(songs = allSongs, albums = albums, artists = artists, genres = genres, folders = folders)
    }

    private fun inferGenreFromPathOrTitle(path: String, title: String): String {
        val lower = "$path $title".lowercase()
        return when {
            lower.contains("rock") || lower.contains("metal") -> "Rock"
            lower.contains("pop") -> "Pop"
            lower.contains("jazz") || lower.contains("blues") -> "Jazz"
            lower.contains("edm") || lower.contains("electro") || lower.contains("techno") || lower.contains("house") -> "EDM"
            lower.contains("classical") || lower.contains("symphony") -> "Classical"
            lower.contains("hiphop") || lower.contains("hip hop") || lower.contains("rap") || lower.contains("trap") -> "Hip-Hop"
            lower.contains("acoustic") || lower.contains("folk") -> "Acoustic"
            lower.contains("lofi") || lower.contains("lo-fi") -> "Lo-fi"
            lower.contains("bollywood") || lower.contains("hindi") -> "Bollywood"
            lower.contains("podcast") -> "Podcast"
            else -> "Other"
        }
    }
}

data class LibraryData(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val folders: List<Folder> = emptyList()
)
