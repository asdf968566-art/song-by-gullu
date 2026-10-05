package com.sangeet.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.ui.theme.Sangeet
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Music sources are behind a password. Only a slow PBKDF2 hash of it is in the app (the repo is public),
 * so it can't simply be read from the code. Unlocked once, it stays open until the app is closed.
 */
object SourcesLock {
    private const val SALT = "sangeet-sources-v1"
    private const val HASH = "05a6f807c54f64d3c838672cf313980af82b35585fcec872c6ba405d8eaa0f3c"
    var unlocked = false
        private set

    suspend fun tryUnlock(password: String): Boolean = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(password.trim().toCharArray(), SALT.toByteArray(), 120_000, 256)
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            .joinToString("") { "%02x".format(it) }
        (hash == HASH).also { if (it) unlocked = true }
    }
}

@Composable
fun LockedSourcesScreen(nav: NavController) {
    var open by remember { mutableStateOf(SourcesLock.unlocked) }
    if (open) {
        SourcesScreen(nav)
        return
    }
    val spec = Sangeet.spec
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val unlock: () -> Unit = {
        if (!checking && password.isNotBlank()) {
            checking = true
            scope.launch {
                if (SourcesLock.tryUnlock(password)) open = true else wrong = true
                checking = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface) }
            Text("Music sources", style = MaterialTheme.typography.titleLarge, color = spec.onSurface)
        }
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Lock, null, tint = spec.accent)
            Text("Enter the password to change music sources", color = spec.onSurface)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; wrong = false },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { unlock() }),
                isError = wrong,
                supportingText = { if (wrong) Text("Wrong password") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = unlock, enabled = !checking && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (checking) "Checking…" else "Unlock")
            }
        }
    }
}
