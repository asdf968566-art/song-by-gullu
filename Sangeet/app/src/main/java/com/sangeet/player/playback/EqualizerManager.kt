package com.sangeet.player.playback

import android.os.Build
import android.media.audiofx.DynamicsProcessing
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Int, val centerHz: Int, val level: Int)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val presets: List<String> = emptyList(),
    val preset: Int = -1,
    val bassBoost: Int = 0,
    /** Same loudness for every song (quiet old songs louder, loud new ones softer). */
    val normalize: Boolean = false,
)

/** Android ka built-in equalizer + bass boost, player ke audio session pe. */
class EqualizerManager(context: Context) {
    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var dynamics: DynamicsProcessing? = null
    private val _state = MutableStateFlow(EqState(enabled = prefs.getBoolean("enabled", false), normalize = prefs.getBoolean("normalize", false)))
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        release()
        try {
            val e = Equalizer(0, sessionId)
            val range = e.bandLevelRange
            val presets = (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
            val savedPreset = prefs.getInt("preset", -1)
            if (savedPreset in presets.indices) {
                e.usePreset(savedPreset.toShort())
            } else {
                for (b in 0 until e.numberOfBands) {
                    val saved = prefs.getInt("band_$b", Int.MIN_VALUE)
                    if (saved != Int.MIN_VALUE) e.setBandLevel(b.toShort(), saved.toShort())
                }
            }
            e.setEnabled(_state.value.enabled)
            eq = e

            val bb = runCatching { BassBoost(0, sessionId) }.getOrNull()
            bb?.let {
                if (it.strengthSupported) it.setStrength(prefs.getInt("bass", 0).toShort())
                it.setEnabled(_state.value.enabled)
            }
            bass = bb

            dynamics = runCatching { makeNormalizer(sessionId) }.getOrNull()

            _state.value = _state.value.copy(
                available = true,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                presets = presets,
                preset = savedPreset,
                bands = readBands(e),
                bassBoost = prefs.getInt("bass", 0),
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(available = false)
        }
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        runCatching { eq?.setEnabled(on); bass?.setEnabled(on) }
        _state.value = _state.value.copy(enabled = on)
    }

    fun setBand(index: Int, level: Int) {
        val e = eq ?: return
        runCatching { e.setBandLevel(index.toShort(), level.toShort()) }
        prefs.edit().putInt("band_$index", level).putInt("preset", -1).apply()
        _state.value = _state.value.copy(bands = readBands(e), preset = -1)
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        runCatching { e.usePreset(index.toShort()) }
        prefs.edit().putInt("preset", index).apply()
        _state.value = _state.value.copy(bands = readBands(e), preset = index)
    }

    fun setNormalize(on: Boolean) {
        prefs.edit().putBoolean("normalize", on).apply()
        runCatching { dynamics?.enabled = on }
        _state.value = _state.value.copy(normalize = on)
    }

    /**
     * Volume leveller: a gentle compressor lifts quiet songs and a limiter stops loud ones from clipping.
     * Uses Android's DynamicsProcessing (Android 9+), so no extra library and almost no battery.
     */
    private fun makeNormalizer(sessionId: Int): DynamicsProcessing? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            false, 0, // pre-EQ
            true, 1, // one-band compressor
            false, 0, // post-EQ
            true, // limiter
        ).build()
        val dp = DynamicsProcessing(0, sessionId, cfg)
        dp.setMbcBandAllChannelsTo(0, DynamicsProcessing.MbcBand(true, 20_000f, 5f, 150f, 3f, -28f, 8f, -90f, 1f, 0f, 9f))
        dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1.5f, 0f))
        dp.enabled = _state.value.normalize
        return dp
    }

    fun setBassBoost(strength: Int) {
        runCatching { bass?.setStrength(strength.toShort()) }
        prefs.edit().putInt("bass", strength).apply()
        _state.value = _state.value.copy(bassBoost = strength)
    }

    private fun readBands(e: Equalizer) = (0 until e.numberOfBands).map { b ->
        EqBand(b, e.getCenterFreq(b.toShort()) / 1000, e.getBandLevel(b.toShort()).toInt())
    }

    private fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { dynamics?.release() }
        eq = null
        bass = null
        dynamics = null
    }
}
