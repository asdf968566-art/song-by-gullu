package com.sangeet.player.playback

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Pause and play fade instead of cutting in and out (report #41, Oct 12: "jhatke se pause naa ho, volume down hoti
 * jaaye, aur dobara play kare to volume dheere dheere badhe").
 *
 * The media session (the app, notification, lock screen, headset buttons, Android Auto) talks to this player:
 * pausing turns the sound down over [FADE_OUT_MS] and then pauses, playing starts silent and turns up over
 * [FADE_IN_MS]. Unplugged headphones and calls still pause at once (ExoPlayer does those itself). The app's own
 * volume (crossfade, sleep timer) is kept apart and multiplied in.
 */
@OptIn(UnstableApi::class)
class FadingPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {
    private val handler = Handler(exo.applicationLooper)
    private var appVolume = 1f
    private var level = 1f
    private var step: Runnable? = null
    private var fadingOut = false

    override fun setVolume(volume: Float) {
        appVolume = volume
        exo.volume = appVolume * level
    }

    override fun getVolume(): Float = appVolume

    override fun play() {
        if (fadingOut) {
            // Play again while the pause was still fading: turn back up from where it is.
            fadingOut = false
            fade(1f, FADE_IN_MS)
            return
        }
        if (exo.playWhenReady) {
            super.play()
            return
        }
        cancel()
        set(0f)
        super.play()
        fade(1f, FADE_IN_MS)
    }

    override fun pause() {
        if (fadingOut) return
        if (!exo.playWhenReady || !exo.isPlaying) {
            // Already paused, or still loading: there's nothing to hear, so pause at once.
            cancel()
            set(1f)
            super.pause()
            return
        }
        fadingOut = true
        fade(0f, FADE_OUT_MS) {
            fadingOut = false
            exo.pause()
            set(1f)
        }
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }

    override fun stop() {
        finish()
        super.stop()
    }

    override fun release() {
        cancel()
        super.release()
    }

    /** Ends a fade at once (a pause that was fading pauses now). */
    private fun finish() {
        if (fadingOut) {
            fadingOut = false
            exo.pause()
        }
        cancel()
        set(1f)
    }

    private fun set(v: Float) {
        level = v
        exo.volume = appVolume * level
    }

    private fun cancel() {
        step?.let { handler.removeCallbacks(it) }
        step = null
    }

    private fun fade(to: Float, ms: Long, done: (() -> Unit)? = null) {
        cancel()
        val from = level
        val start = SystemClock.uptimeMillis()
        val r = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - start).toFloat() / ms).coerceIn(0f, 1f)
                set(from + (to - from) * t * t * (3 - 2 * t)) // gentle at both ends
                if (t < 1f) {
                    handler.postDelayed(this, 16)
                } else {
                    step = null
                    done?.invoke()
                }
            }
        }
        step = r
        handler.post(r)
    }

    companion object {
        const val FADE_OUT_MS = 450L
        const val FADE_IN_MS = 800L
    }
}
