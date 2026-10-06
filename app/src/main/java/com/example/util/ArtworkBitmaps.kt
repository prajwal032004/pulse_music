package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.Song
import com.example.ui.components.artworkKey
import com.example.ui.components.artworkPalette
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Bitmap helpers shared by the media notification, home-screen widgets and share card. */
object ArtworkBitmaps {

    private val pngCache = LruCache<String, ByteArray>(24)

    /** PNG bytes of the generated gradient cover (used as notification artwork for art-less tracks). */
    fun generatedPng(song: Song, sizePx: Int): ByteArray {
        val key = "${song.artworkKey()}_$sizePx"
        pngCache.get(key)?.let { return it }
        val bytes = ByteArrayOutputStream().use { out ->
            generated(song, sizePx).compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        pngCache.put(key, bytes)
        return bytes
    }

    fun generated(song: Song, sizePx: Int): Bitmap = generated(song.artworkKey(), sizePx)

    fun generated(key: String, sizePx: Int): Bitmap {
        val colors = artworkPalette(key).map { it.toArgb() }.toIntArray()
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = sizePx.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, s, s, colors, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, p)
        p.shader = RadialGradient(s * 0.72f, s * 0.28f, s * 0.55f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(s * 0.72f, s * 0.28f, s * 0.55f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.012f
        p.color = 0x12FFFFFF
        for (i in 1..4) c.drawCircle(s * 0.5f, s * 1.05f, s * 0.22f * i, p)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, s * 0.55f, 0f, s, 0x00000000, 0x59000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, p)
        return bmp
    }

    /** Loads the real embedded album art when available, else null. Safe to call off the main thread. */
    fun loadArtwork(context: Context, song: Song, sizePx: Int): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null)
            } else {
                val uri = song.albumArtUri ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= sizePx) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        }.getOrNull()
    }

    fun artworkOrGenerated(context: Context, song: Song, sizePx: Int): Bitmap =
        loadArtwork(context, song, sizePx)?.let { centerCrop(it, sizePx) } ?: generated(song, sizePx)

    fun centerCrop(src: Bitmap, sizePx: Int): Bitmap {
        if (src.width == sizePx && src.height == sizePx) return src
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, x, y, side, side)
        return Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
    }

    fun rounded(src: Bitmap, radiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radiusPx, radiusPx, p)
        return out
    }

    // -----------------------------------------------------------------------------------------
    // Share card
    // -----------------------------------------------------------------------------------------

    /** Renders a 1080×1350 "Now playing" story card and returns a shareable content:// URI. */
    fun renderShareCard(context: Context, song: Song, accentArgb: Int, partnerArgb: Int): Uri {
        val w = 1080
        val h = 1350
        val card = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(card)
        val art = artworkOrGenerated(context, song, 720)

        // Blurred-art backdrop: heavy downscale then upscale, then a dark gradient wash.
        val tiny = Bitmap.createScaledBitmap(art, 12, 12, true)
        val backdrop = Bitmap.createScaledBitmap(tiny, w, w, true)
        c.drawBitmap(backdrop, 0f, (h - w) / 2f, null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x66000000, 0xB3000000.toInt(), 0xF2000000.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.5f, 520f, 620f, (accentArgb and 0x00FFFFFF) or 0x66000000, 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // Glowing artwork
        val artRect = RectF(180f, 150f, 900f, 870f)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = partnerArgb
            alpha = 150
            maskFilter = BlurMaskFilter(70f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(artRect.left + 20, artRect.top + 40, artRect.right - 20, artRect.bottom + 30), 64f, 64f, glow)
        c.drawBitmap(rounded(art, 56f), null, artRect, null)

        val regular = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
        val bold = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(regular, 800, false) else Typeface.create(regular, Typeface.BOLD)
        val medium = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(regular, 500, false) else regular

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 72f; typeface = bold; textAlign = Paint.Align.CENTER }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt(); textSize = 44f; typeface = medium; textAlign = Paint.Align.CENTER }
        val title = TextUtils.ellipsize(song.title, titlePaint, w - 160f, TextUtils.TruncateAt.END).toString()
        val artist = TextUtils.ellipsize(song.artist, artistPaint, w - 160f, TextUtils.TruncateAt.END).toString()
        c.drawText(title, w / 2f, 1000f, titlePaint)
        c.drawText(artist, w / 2f, 1066f, artistPaint)

        // Gradient progress line
        p.shader = LinearGradient(240f, 0f, 840f, 0f, partnerArgb, accentArgb, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(240f, 1122f, 840f, 1132f), 5f, 5f, p)
        p.shader = null

        // Footer badge
        val badge = RectF(300f, 1196f, 780f, 1280f)
        p.color = 0x26FFFFFF
        c.drawRoundRect(badge, 42f, 42f, p)
        p.shader = LinearGradient(330f, 0f, 390f, 0f, partnerArgb, accentArgb, Shader.TileMode.CLAMP)
        c.drawCircle(360f, 1238f, 26f, p)
        p.shader = null
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 34f; typeface = bold }
        c.drawText("Pulse Music", 402f, 1251f, footer)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "pulse_now_playing.png")
        FileOutputStream(file).use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
