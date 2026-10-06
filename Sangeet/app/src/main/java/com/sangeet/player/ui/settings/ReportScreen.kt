package com.sangeet.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.data.CrashReporter
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.launch
import com.sangeet.player.ui.theme.LocalBottomBarSpace

/** "Report a problem": write what went wrong and send it from right here (no browser, no GitHub app). */
@Composable
fun ReportScreen(nav: NavController) {
    val context = LocalContext.current
    val spec = Sangeet.spec
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val crash = remember { CrashReporter.pendingCrash(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomBarSpace.current)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface) }
            Text("Report a problem", style = MaterialTheme.typography.titleLarge, color = spec.onSurface)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = spec.accent)
                    Text("  Sent! Thanks, it will be looked at (report #$sent).", color = spec.onSurface)
                }
                Button(onClick = { nav.popBackStack() }) { Text("Done") }
                return@Column
            }
            if (crash != null) {
                Text("The app closed unexpectedly last time. The error details will be included.", color = spec.muted)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("What went wrong?") },
                placeholder = { Text("E.g. the song stops after a minute, the Home page is slow…") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = logs, onCheckedChange = { logs = it })
                Text("Include app log and phone model (helps fix it faster)", color = spec.onSurface)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (CrashReporter.canSendDirectly) {
                Button(
                    onClick = {
                        sending = true
                        scope.launch {
                            runCatching { CrashReporter.submit(context, text, logs) }
                                .onSuccess { sent = it }
                                .onFailure { error = "Couldn't send (${it.message}). Check your internet, or share it instead." }
                            sending = false
                        }
                    },
                    enabled = !sending && (text.isNotBlank() || crash != null),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp) else Text("Send")
                }
            }
            OutlinedButton(
                onClick = { CrashReporter.share(context, text, logs) },
                enabled = !sending && (text.isNotBlank() || crash != null),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (CrashReporter.canSendDirectly) "Share instead (WhatsApp, email…)" else "Send with WhatsApp, email…") }
        }
    }
}
