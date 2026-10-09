package com.sangeet.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sangeet.player.data.WhatsNew
import com.sangeet.player.ui.theme.Sangeet

/** The update log: each update's date and title, then what changed. */
@Composable
fun WhatsNewDialog(entries: List<WhatsNew.Entry>, onDismiss: () -> Unit) {
    val spec = Sangeet.spec
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                entries.forEachIndexed { i, e ->
                    Text(
                        listOf(e.title, e.date).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = spec.onSurface,
                        modifier = Modifier.padding(top = if (i == 0) 0.dp else 16.dp, bottom = 6.dp),
                    )
                    e.items.forEach { item ->
                        Text("•  ${item.text}", style = MaterialTheme.typography.bodyMedium, color = spec.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
