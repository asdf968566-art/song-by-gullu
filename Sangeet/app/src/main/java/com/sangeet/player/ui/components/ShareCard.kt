package com.sangeet.player.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sangeet.player.data.model.Track
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Song share card: cover + title + artist (+ a lyric line), shared as an image to WhatsApp / Instagram. */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    suspend fun share(context: Context, track: Track, lyricLine: String? = null) {
        val file = withContext(Dispatchers.IO) { render(context, track, lyricLine) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Listening to \"${track.title}\" by ${track.artist} on Sangeet 🎵" +
                (com.sangeet.player.data.LibrarySync.songLink(track)?.let { "\n$it" } ?: ""))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** "My Sangeet Wrapped": minutes, top songs, top singers and streak on one picture. */
    suspend fun shareWrapped(context: Context, minutes: Long, songs: List<Pair<String, String>>, singers: List<String>, streak: Int, coverUrl: String?) {
        val file = withContext(Dispatchers.IO) {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val cover: Bitmap? = coverUrl?.let { url ->
                runCatching {
                    val res = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(400).build())
                    (res as? SuccessResult)?.drawable?.toBitmap()
                }.getOrNull()
            }
            val tint = cover?.let { Bitmap.createScaledBitmap(it, 1, 1, true).getPixel(0, 0) } ?: 0xFF1DB954.toInt()
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), tint, 0xFF0B0B0F.toInt(), Shader.TileMode.CLAMP)
            })
            if (cover != null) {
                val rect = RectF(700f, 90f, 1000f, 390f)
                val save = canvas.save()
                canvas.clipPath(android.graphics.Path().apply { addRoundRect(rect, 28f, 28f, android.graphics.Path.Direction.CW) })
                canvas.drawBitmap(cover, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restoreToCount(save)
            }
            val white = 0xFFFFFFFF.toInt()
            val dim = 0xB3FFFFFF.toInt()
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            drawText(canvas, "MY SANGEET WRAPPED", 40f, Typeface.DEFAULT_BOLD, dim, 100f, maxLines = 1, width = 560, left = true)
            drawText(canvas, "$year", 120f, Typeface.DEFAULT_BOLD, white, 170f, maxLines = 1, width = 560, left = true)
            drawText(canvas, "${"%,d".format(minutes)} minutes of music", 58f, Typeface.DEFAULT_BOLD, white, 430f, maxLines = 1, left = true)
            drawText(canvas, "Top songs", 40f, Typeface.DEFAULT_BOLD, dim, 560f, maxLines = 1, x = 70f, width = 460, left = true)
            drawText(canvas, "Top singers", 40f, Typeface.DEFAULT_BOLD, dim, 560f, maxLines = 1, x = 580f, width = 440, left = true)
            var ys = 630f
            songs.take(5).forEachIndexed { i, (t, _) -> ys = drawText(canvas, "${i + 1}. $t", 38f, Typeface.DEFAULT_BOLD, white, ys, maxLines = 2, x = 70f, width = 470, left = true) + 10f }
            var ya = 630f
            singers.take(5).forEachIndexed { i, a -> ya = drawText(canvas, "${i + 1}. $a", 38f, Typeface.DEFAULT_BOLD, white, ya, maxLines = 2, x = 580f, width = 440, left = true) + 10f }
            if (streak > 0) drawText(canvas, "🔥 $streak-day listening streak", 44f, Typeface.DEFAULT_BOLD, white, 1150f, maxLines = 1, left = true)
            canvas.drawText("🎵 Sangeet", 70f, H - 60f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 34f; typeface = Typeface.DEFAULT_BOLD })
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            File(dir, "sangeet_wrapped.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "My Sangeet Wrapped 🎵")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your Wrapped").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun render(context: Context, track: Track, lyricLine: String?): File {
        val cover: Bitmap? = track.artworkUrl?.let { url ->
            val res = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(900).build())
            (res as? SuccessResult)?.drawable?.toBitmap()
        }
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background: cover colour gradient
        val tint = cover?.let { Bitmap.createScaledBitmap(it, 1, 1, true).getPixel(0, 0) } ?: 0xFF4A2BD8.toInt()
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), tint, 0xFF0B0B0F.toInt(), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // Cover
        val size = 760f
        val left = (W - size) / 2
        val top = 150f
        val rect = RectF(left, top, left + size, top + size)
        if (cover != null) {
            val save = canvas.save()
            val path = android.graphics.Path().apply { addRoundRect(rect, 40f, 40f, android.graphics.Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawBitmap(cover, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(save)
        } else {
            canvas.drawRoundRect(rect, 40f, 40f, Paint().apply { color = 0x33FFFFFF })
        }

        var y = top + size + 70f
        y = drawText(canvas, track.title, 64f, Typeface.DEFAULT_BOLD, 0xFFFFFFFF.toInt(), y, maxLines = 2)
        y = drawText(canvas, track.artist, 42f, Typeface.DEFAULT, 0xCCFFFFFF.toInt(), y + 10f, maxLines = 1)
        if (!lyricLine.isNullOrBlank()) {
            drawText(canvas, "“${lyricLine.trim()}”", 40f, Typeface.create(Typeface.DEFAULT, Typeface.ITALIC), 0xE6FFFFFF.toInt(), y + 30f, maxLines = 2)
        }
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("🎵 Sangeet", 70f, H - 60f, brand)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        return File(dir, "sangeet_share.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun drawText(
        canvas: Canvas, text: String, size: Float, face: Typeface, color: Int, y: Float, maxLines: Int,
        x: Float = 70f, width: Int = W - 140, left: Boolean = false,
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; this.color = color }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(if (left) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }
}
