package com.sangeet.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.data.settings.AccentColor
import com.sangeet.player.data.settings.AppSettings
import com.sangeet.player.data.settings.DarkMode
import com.sangeet.player.data.settings.ThemeStyle
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.SangeetTheme
import com.sangeet.player.ui.theme.ThemedBackground
import com.sangeet.player.ui.theme.playButtonStyle
import com.sangeet.player.ui.theme.playIconColor
import com.sangeet.player.ui.theme.themedCard
import kotlinx.coroutines.launch

@Composable
fun ThemePickerScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTopBar(nav, "Choose theme")

        ThemeStyle.entries.chunked(2).forEach { row ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { style ->
                    ThemePreview(
                        settings = s,
                        style = style,
                        selected = s.theme == style,
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { c.settings.setTheme(style) } }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        SettingsGroup("Light / Dark") {
            ChoiceRow("Mode", "AMOLED is always dark", DarkMode.entries, s.darkMode, { it.label }) {
                scope.launch { c.settings.setDarkMode(it) }
            }
        }

        SettingsGroup("Accent color") {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentColor.entries.forEach { a ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(a.argb))
                            .border(3.dp, if (s.accent == a) spec.onSurface else Color.Transparent, CircleShape)
                            .clickable { scope.launch { c.settings.setAccent(a) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (s.accent == a) Icon(Icons.Rounded.Check, a.label, tint = Color.White)
                    }
                }
            }
            if (s.theme == ThemeStyle.MATERIAL_YOU) {
                Text(
                    "Material You takes its colors from your wallpaper (Android 12+).",
                    style = MaterialTheme.typography.bodySmall,
                    color = spec.muted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Ek theme ka chhota live preview. */
@Composable
private fun ThemePreview(
    settings: AppSettings,
    style: ThemeStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val outer = Sangeet.spec
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) outer.accent else outer.muted.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            SangeetTheme(settings, styleOverride = style) {
                val spec = Sangeet.spec
                ThemedBackground {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .themedCard(spec, RoundedCornerShape(10.dp), corner = 10.dp, elevation = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .padding(6.dp)
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(spec.accent.copy(alpha = 0.7f))
                            )
                            Column {
                                Box(Modifier.width(60.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(spec.onSurface.copy(alpha = 0.8f)))
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.width(40.dp).height(6.dp).clip(RoundedCornerShape(4.dp)).background(spec.muted.copy(alpha = 0.6f)))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .playButtonStyle(spec, 48.dp),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.PlayArrow, null, tint = playIconColor(spec)) }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(spec.muted.copy(alpha = 0.35f))
                            ) {
                                Box(Modifier.fillMaxWidth(0.45f).height(4.dp).background(spec.accent))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(style.label, style = MaterialTheme.typography.titleSmall, color = spec.onSurface)
                    }
                }
            }
        }
        Text(
            style.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = outer.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}
