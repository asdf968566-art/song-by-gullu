package com.sangeet.player.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sangeet.player.MainActivity
import com.sangeet.player.R
import com.sangeet.player.SangeetApplication
import com.sangeet.player.playback.PlayerState

/** Home screen widget: cover, song name, previous / play-pause / next. */
class SangeetWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val c = (context.applicationContext as SangeetApplication).container
        render(context, c.player.state.value, null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val player = (context.applicationContext as SangeetApplication).container.player
        when (intent.action) {
            ACTION_TOGGLE -> player.togglePlay()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.previous()
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.sangeet.player.widget.TOGGLE"
        private const val ACTION_NEXT = "com.sangeet.player.widget.NEXT"
        private const val ACTION_PREV = "com.sangeet.player.widget.PREV"

        /** Player state badalte hi widget update (AppContainer se call hota hai). */
        suspend fun update(context: Context, state: PlayerState) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SangeetWidget::class.java))
            if (ids.isEmpty()) return
            val cover = state.current?.artworkUrl?.let { url ->
                runCatching {
                    val res = context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(200).allowHardware(false).build())
                    (res as? SuccessResult)?.drawable?.toBitmap(200, 200)
                }.getOrNull()
            }
            render(context, state, cover)
        }

        private fun render(context: Context, state: PlayerState, cover: Bitmap?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SangeetWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(context.packageName, R.layout.widget_player)
            val t = state.current
            v.setTextViewText(R.id.widget_title, t?.title ?: "Sangeet")
            v.setTextViewText(R.id.widget_artist, t?.artist ?: "Tap to play music")
            if (cover != null) v.setImageViewBitmap(R.id.widget_cover, cover)
            else v.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            v.setImageViewResource(R.id.widget_play, if (state.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play)

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_cover, open)
            v.setOnClickPendingIntent(R.id.widget_title, open)
            v.setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_TOGGLE, 1))
            v.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 2))
            v.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 3))
            manager.updateAppWidget(ids, v)
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, SangeetWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
