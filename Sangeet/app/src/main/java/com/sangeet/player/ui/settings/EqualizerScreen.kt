package com.sangeet.player.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet

@Composable
fun EqualizerScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val eq by c.equalizer.state.collectAsStateWithLifecycle()
    val spec = Sangeet.spec

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTopBar(nav, "Equalizer")

        if (!eq.available) {
            Text(
                "Play a song first — the equalizer attaches to the player. (Not supported on some phones.)",
                color = spec.muted,
                modifier = Modifier.padding(20.dp),
            )
        }

        SettingsGroup("Equalizer") {
            SettingsSwitch(Icons.Rounded.Equalizer, "Equalizer on", "Apply bands and bass boost", eq.enabled) {
                c.equalizer.setEnabled(it)
            }
        }

        if (eq.available && eq.presets.isNotEmpty()) {
            SettingsGroup("Presets") {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    eq.presets.forEachIndexed { i, name ->
                        FilterChip(
                            selected = eq.preset == i,
                            onClick = { c.equalizer.usePreset(i) },
                            label = { Text(name) },
                            enabled = eq.enabled,
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = spec.accent,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        }

        if (eq.available) {
            SettingsGroup("Bands") {
                eq.bands.forEach { band ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (band.centerHz >= 1000) "${band.centerHz / 1000}k" else "${band.centerHz}",
                            color = spec.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(44.dp),
                        )
                        Slider(
                            value = band.level.toFloat(),
                            onValueChange = { c.equalizer.setBand(band.index, it.toInt()) },
                            valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                            enabled = eq.enabled,
                            colors = SliderDefaults.colors(thumbColor = spec.accent, activeTrackColor = spec.accent),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+.1f".format(band.level / 100f),
                            color = spec.muted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }
            }
            SettingsGroup("Bass boost") {
                Slider(
                    value = eq.bassBoost.toFloat(),
                    onValueChange = { c.equalizer.setBassBoost(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = eq.enabled,
                    colors = SliderDefaults.colors(thumbColor = spec.accent, activeTrackColor = spec.accent),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
