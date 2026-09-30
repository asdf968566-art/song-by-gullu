package com.sangeet.player.playback

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
)

/** Android ka built-in equalizer + bass boost, player ke audio session pe. */
class EqualizerManager(context: Context) {
    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private val _state = MutableStateFlow(EqState(enabled = prefs.getBoolean("enabled", false)))
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
        eq = null
        bass = null
    }
}
