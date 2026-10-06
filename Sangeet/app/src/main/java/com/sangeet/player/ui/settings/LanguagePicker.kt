package com.sangeet.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.data.Categories
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.launch

/** Each language with its own script, so it is easy to spot. */
private val NATIVE = mapOf(
    "hindi" to "हिंदी", "punjabi" to "ਪੰਜਾਬੀ", "haryanvi" to "हरियाणवी", "bhojpuri" to "भोजपुरी",
    "tamil" to "தமிழ்", "telugu" to "తెలుగు", "marathi" to "मराठी", "bengali" to "বাংলা", "gujarati" to "ગુજરાતી",
)

/**
 * Which languages' songs you want. Shown in Settings and from Home (no password, unlike Music sources).
 * At least one stays chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageChooser(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val spec = Sangeet.spec
    Column(modifier) {
        Text(
            "Songs in these languages come first in For You, Home and suggestions. Pick one or more.",
            style = MaterialTheme.typography.bodySmall,
            color = spec.muted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        FlowRow(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Categories.languages.forEach { lang ->
                val on = lang in s.languages
                val name = lang.replaceFirstChar(Char::uppercase)
                FilterChip(
                    selected = on,
                    onClick = {
                        val next = if (on) s.languages - lang else s.languages + lang
                        // The app's scope: the change is kept even if the sheet closes right away.
                        if (next.isNotEmpty()) c.scope.launch { c.settings.setLanguages(next) }
                    },
                    label = { Text(NATIVE[lang]?.let { "$name · $it" } ?: name) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
                    } else null,
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = spec.accent,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

/** The language chooser as a sheet (from Home). [onDone] says whether the choice changed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(onDone: (changed: Boolean) -> Unit) {
    val c = LocalAppContainer.current
    val before = remember { c.settings.current.languages }
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val changed = s.languages.toSet() != before.toSet()
    ModalBottomSheet(
        onDismissRequest = { onDone(changed) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Song languages",
                style = MaterialTheme.typography.titleLarge,
                color = Sangeet.spec.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LanguageChooser()
            Button(
                onClick = { onDone(changed) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("Done") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
