package com.sangeet.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.data.update.AppUpdater
import com.sangeet.player.ui.LocalAppContainer
import kotlinx.coroutines.launch

/** Naya version aaya / download ho raha / install karo — poore app mein kahin bhi dikhta hai. */
@Composable
fun UpdateDialog() {
    val c = LocalAppContainer.current
    val updater = c.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    when (val s = state) {
        is AppUpdater.State.Available -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("New version available 🎉") },
            text = {
                Text(
                    "Build ${s.update.build} (you have ${updater.currentBuild}).\n" +
                        "Size: ${s.update.sizeBytes / 1_048_576} MB. Your data will be kept.",
                )
            },
            confirmButton = { TextButton(onClick = { scope.launch { updater.download(s.update) } }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { updater.skip(s.update) }) { Text("Later") } },
        )

        is AppUpdater.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${s.percent}%")
                }
            },
            confirmButton = {},
        )

        is AppUpdater.State.ReadyToInstall -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("Install") },
            text = {
                Text(
                    "Download complete. Tap \"Install\".\n" +
                        "The first time, Android will ask you to turn on \"Allow from this source\". Turn it on, come back and tap Install again.",
                )
            },
            confirmButton = { TextButton(onClick = { updater.install(s.file) }) { Text("Install") } },
            dismissButton = { TextButton(onClick = { updater.dismiss() }) { Text("Later") } },
        )

        is AppUpdater.State.UpToDate -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("You're up to date ✅") },
            text = { Text("You have the latest version (build ${updater.currentBuild}).") },
            confirmButton = { TextButton(onClick = { updater.dismiss() }) { Text("OK") } },
        )

        is AppUpdater.State.Error -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("Update failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { updater.dismiss() }) { Text("OK") } },
        )

        else -> Unit
    }
}
