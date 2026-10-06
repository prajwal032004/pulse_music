package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.dao.MusicDao
import com.example.data.entity.FavoriteEntity
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports liked songs and playlists to a JSON file the user picks, and restores them later
 * (on this phone or a new one). Songs are matched by file path first, then by title + artist,
 * because MediaStore ids change between devices.
 */
class BackupManager(
    private val context: Context,
    private val dao: MusicDao,
    private val repository: MusicRepository
) {
    data class Result(val favorites: Int, val playlists: Int, val songs: Int, val missing: Int)

    suspend fun export(uri: Uri): Result = withContext(Dispatchers.IO) {
        val byId = repository.libraryData.value.songs.associateBy { it.id }
        val favIds = dao.getAllFavoriteIds().first()
        val favorites = JSONArray().apply { favIds.mapNotNull { byId[it] }.forEach { put(it.toJson()) } }
        var songCount = 0
        val playlists = JSONArray()
        dao.getPlaylistsOnce().filter { !it.isSmart }.forEach { pl ->
            val songs = dao.getPlaylistSongIdsOnce(pl.id).mapNotNull { byId[it] }
            songCount += songs.size
            playlists.put(JSONObject().put("name", pl.name).put("createdAt", pl.createdAt).put("songs", JSONArray().apply { songs.forEach { put(it.toJson()) } }))
        }
        val root = JSONObject()
            .put("app", "Pulse Music")
            .put("format", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("favorites", favorites)
            .put("playlists", playlists)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
            ?: error("Could not open file")
        Result(favorites.length(), playlists.length(), songCount, 0)
    }

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Could not open file")
        val root = JSONObject(text)
        require(root.optString("app") == "Pulse Music") { "Not a Pulse Music backup" }

        val library = repository.libraryData.value.songs
        val byPath = library.associateBy { it.path.lowercase() }
        val byTitle = library.associateBy { key(it.title, it.artist) }
        var missing = 0
        fun resolve(o: JSONObject): Song? =
            (byPath[o.optString("path").lowercase()] ?: byTitle[key(o.optString("title"), o.optString("artist"))])
                .also { if (it == null) missing++ }

        val existingFavs = dao.getAllFavoriteIds().first().toSet()
        var favCount = 0
        root.optJSONArray("favorites")?.let { arr ->
            for (i in 0 until arr.length()) {
                val song = resolve(arr.getJSONObject(i)) ?: continue
                if (song.id !in existingFavs) dao.addFavorite(FavoriteEntity(songId = song.id))
                favCount++
            }
        }

        val existing = dao.getPlaylistsOnce().associateBy { it.name.lowercase() }
        var plCount = 0
        var songCount = 0
        root.optJSONArray("playlists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").ifBlank { "Restored playlist" }
                val id = existing[name.lowercase()]?.id ?: repository.createPlaylist(name)
                val already = dao.getPlaylistSongIdsOnce(id).toSet()
                val songs = o.optJSONArray("songs") ?: JSONArray()
                for (j in 0 until songs.length()) {
                    val song = resolve(songs.getJSONObject(j)) ?: continue
                    if (song.id !in already) repository.addSongToPlaylist(id, song.id)
                    songCount++
                }
                plCount++
            }
        }
        Result(favCount, plCount, songCount, missing)
    }

    private fun key(title: String, artist: String) = "${title.trim().lowercase()}|${artist.trim().lowercase()}"

    private fun Song.toJson() = JSONObject().put("title", title).put("artist", artist).put("album", album).put("path", path)
}
