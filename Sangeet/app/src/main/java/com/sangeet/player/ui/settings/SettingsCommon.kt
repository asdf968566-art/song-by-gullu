package com.sangeet.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.themedCard

@Composable
fun SettingsTopBar(nav: NavController, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Sangeet.spec.onSurface)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Sangeet.spec.onSurface)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val spec = Sangeet.spec
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = spec.accent,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .themedCard(spec, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spec = Sangeet.spec
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = spec.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = spec.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = spec.muted)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.Rounded.ChevronRight, null, tint = spec.muted)
        }
    }
}

@Composable
fun SettingsSwitch(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsItem(icon, title, subtitle, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Sangeet.spec.accent),
        )
    }
}

/** Chhote options ki line (jaise 128 / 256 / 320 kbps). */
@Composable
fun <T> ChoiceRow(title: String, subtitle: String?, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val spec = Sangeet.spec
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = spec.onSurface)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = spec.muted)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(
                    selected = o == selected,
                    onClick = { onSelect(o) },
                    label = { Text(label(o)) },
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
