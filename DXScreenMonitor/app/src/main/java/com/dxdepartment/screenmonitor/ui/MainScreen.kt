@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dxdepartment.screenmonitor.ui

import android.graphics.BitmapFactory
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dxdepartment.screenmonitor.capture.CaptureState
import com.dxdepartment.screenmonitor.capture.CaptureStatus
import com.dxdepartment.screenmonitor.capture.FrameHub
import com.dxdepartment.screenmonitor.capture.Phase
import com.dxdepartment.screenmonitor.device.DeviceIdentity
import com.dxdepartment.screenmonitor.net.NetState
import com.dxdepartment.screenmonitor.net.NetworkMonitor
import com.dxdepartment.screenmonitor.recovery.Recovery
import com.dxdepartment.screenmonitor.settings.AppSettings
import com.dxdepartment.screenmonitor.settings.ImageQuality
import com.dxdepartment.screenmonitor.settings.Quality
import com.dxdepartment.screenmonitor.settings.Resolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScreenActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val openOverlaySettings: () -> Unit,
    val openBatterySettings: () -> Unit,
)

@Composable
fun MainScreen(actions: ScreenActions) {
    val ctx = LocalContext.current
    val status by CaptureState.status.collectAsStateWithLifecycle()
    val net by NetworkMonitor.state.collectAsStateWithLifecycle()
    val settings by AppSettings.init(ctx).collectAsStateWithLifecycle()

    // "x minutes ago" and permission states are refreshed every few seconds while the screen is open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var batteryFree by remember { mutableStateOf(Recovery.ignoringBatteryOptimizations(ctx)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                overlayAllowed = Settings.canDrawOverlays(ctx)
                batteryFree = Recovery.ignoringBatteryOptimizations(ctx)
                delay(2000)
            }
        }
    }

    var editName by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("DX Screen Monitor", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DeviceCard(onEdit = { editName = true })
            StatusCard(status, net, now)
            ShareButton(status, actions)
            if (status.phase == Phase.SHARING) PreviewCard(status)
            QualityCard(settings.quality, status.lowPower)
            ViewerCard(status, net, settings.viewerKey, settings.localViewer)
            RecoveryCard(settings.autoResume, overlayAllowed, batteryFree, actions)
            Text(
                DeviceIdentity.model + " · app " + com.dxdepartment.screenmonitor.BuildConfig.VERSION_NAME,
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    if (editName) {
        var text by remember { mutableStateOf(settings.deviceLabel) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Device name") },
            text = {
                Column {
                    Text("A label for this phone, for example \"Pixel 01\". Leave empty to use the phone's own name.", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { AppSettings.setDeviceLabel(text); editName = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, dot: Color? = null, mono: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Muted, modifier = Modifier.width(130.dp), fontSize = 14.sp)
        if (dot != null) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            value, fontSize = 14.sp, modifier = Modifier.weight(1f),
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun DeviceCard(onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val settings by AppSettings.init(ctx).collectAsStateWithLifecycle()
    Section("Device") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    settings.deviceLabel.ifBlank { DeviceIdentity.systemName(ctx) },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(DeviceIdentity.id(ctx), fontFamily = FontFamily.Monospace, color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onEdit) { Text("Rename") }
        }
    }
}

private fun ago(t: Long?, now: Long): String =
    if (t == null) "Never" else DateUtils.getRelativeTimeSpanString(t, now, DateUtils.SECOND_IN_MILLIS).toString()

@Composable
private fun StatusCard(s: CaptureStatus, net: NetState, now: Long) {
    Section("Status") {
        val (shareText, shareColor) = when (s.phase) {
            Phase.SHARING -> "Sharing" to Live
            Phase.STARTING -> "Starting…" to Warn
            Phase.ERROR -> "Error" to Bad
            Phase.STOPPED -> "Stopped" to Muted
            Phase.IDLE -> "Not sharing" to Muted
        }
        InfoRow("Screen sharing", shareText, shareColor)
        val conn = when {
            s.phase != Phase.SHARING -> "Not connected"
            s.viewers == 1 -> "1 viewer watching"
            s.viewers > 1 -> "${s.viewers} viewers watching"
            else -> "Waiting for a viewer"
        }
        InfoRow("Connection", conn, if (s.viewers > 0) Live else Muted)
        InfoRow("Network", net.label + if (net.switches > 0) " · changed ${ago(net.changedAt, now)}" else "", if (!net.online) Bad else if (net.validated) Live else Warn)
        InfoRow("Last connection", ago(s.lastViewerAt, now) + (s.lastViewerAddress?.let { " · $it" } ?: ""))
        if (s.phase == Phase.SHARING) {
            InfoRow("Since", ago(s.startedAt, now))
            InfoRow("Picture", "${s.width}×${s.height} (screen ${s.screenWidth}×${s.screenHeight}, ${s.rotation}°)")
            InfoRow("Frame rate", "%.0f FPS from screen · %.0f FPS sent · %d KB/frame".format(s.captureFps, s.encodeFps, s.lastFrameKb))
        }
        s.batteryPercent?.let {
            InfoRow(
                "Battery", "$it%" + (if (s.charging) " · charging" else "") + (if (s.lowPower) " · low battery mode (720p, 10 FPS)" else ""),
                if (s.lowPower) Warn else null,
            )
        }
        s.message?.let { Text(it, color = if (s.phase == Phase.ERROR) Bad else Warn, fontSize = 14.sp) }
    }
}

@Composable
private fun ShareButton(s: CaptureStatus, actions: ScreenActions) {
    if (s.sharing) {
        Button(
            onClick = actions.stop,
            colors = ButtonDefaults.buttonColors(containerColor = Bad, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Stop sharing", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        Button(
            onClick = actions.start,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Start sharing", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** A small live picture of what viewers see (decoded at most once a second, only while this screen is open). */
@Composable
private fun PreviewCard(s: CaptureStatus) {
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        FrameHub.previewWanted = true
        onDispose { FrameHub.previewWanted = false }
    }
    LaunchedEffect(Unit) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = -1L
            while (true) {
                val f = FrameHub.latest()
                if (f != null && f.version != last) {
                    last = f.version
                    img = withContext(Dispatchers.Default) {
                        val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeByteArray(f.jpeg, 0, f.jpeg.size, o)?.asImageBitmap()
                    }
                }
                delay(1000)
            }
        }
    }
    Section("Live preview") {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val i = img
            if (i == null) Text("Waiting for the first picture…", color = Muted)
            else Image(i, "Shared screen", contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 320.dp))
        }
        Text("Frames: ${s.framesTotal}", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun QualityCard(q: Quality, lowPower: Boolean) {
    Section("Video quality") {
        Text("Resolution", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Resolution.entries.forEach { r ->
                FilterChip(selected = q.resolution == r, onClick = { AppSettings.setResolution(r) }, label = { Text(r.label) })
            }
        }
        Text("Frame rate", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quality.FPS_CHOICES.forEach { f ->
                FilterChip(selected = q.fps == f, onClick = { AppSettings.setFps(f) }, label = { Text("$f FPS") })
            }
        }
        Text("Picture quality", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageQuality.entries.forEach { i ->
                FilterChip(selected = q.image == i, onClick = { AppSettings.setImageQuality(i) }, label = { Text(i.label) })
            }
        }
        if (lowPower) Text("Battery is low: using at most 720p and 10 FPS until it charges.", color = Warn, fontSize = 13.sp)
        Text("Changes apply at once, even while sharing.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ViewerCard(s: CaptureStatus, net: NetState, key: String, enabled: Boolean) {
    Section("Local viewer (same Wi-Fi)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Watch from a browser on this network", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { AppSettings.setLocalViewer(it) })
        }
        if (!enabled) return@Section
        val port = s.serverPort
        val addr = net.localAddress
        val url = if (addr != null) "http://$addr:${port ?: AppSettings.get(LocalContext.current).viewerPort}/?key=$key" else null
        InfoRow("Viewer key", key, mono = true)
        when {
            s.phase != Phase.SHARING -> Text("Start sharing, then open the address below on a PC or phone on the same Wi-Fi.", color = Muted, fontSize = 13.sp)
            port == null -> Text(s.serverError ?: "The viewer is not running.", color = Bad, fontSize = 13.sp)
        }
        if (url == null) {
            Text("Not on Wi-Fi: connect this phone to Wi-Fi (or turn on its hotspot) to watch it locally.", color = Warn, fontSize = 13.sp)
        } else {
            Text(url, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            val qr = remember(url) { Qr.bitmap(url) }
            if (qr != null) {
                Text("Pairing QR: scan it with the viewing phone's camera.", color = Muted, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        qr, "Pairing QR code",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
        OutlinedButton(onClick = { AppSettings.newViewerKey() }) { Text("New viewer key") }
        Text("A new key disconnects nobody already watching; new viewers need it.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun RecoveryCard(autoResume: Boolean, overlayAllowed: Boolean, batteryFree: Boolean, actions: ScreenActions) {
    Section("Recovery") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Resume after restart")
                Text("After a reboot or if Android stops the app, ask to share again.", color = Muted, fontSize = 13.sp)
            }
            Switch(checked = autoResume, onCheckedChange = { AppSettings.setAutoResume(it) })
        }
        InfoRow("Open by itself", if (overlayAllowed) "Allowed" else "Not allowed (a notification is shown instead)", if (overlayAllowed) Live else Warn)
        if (!overlayAllowed) OutlinedButton(onClick = actions.openOverlaySettings) { Text("Allow \"Display over other apps\"") }
        InfoRow("Battery optimisation", if (batteryFree) "Off for this app" else "On (Android may stop sharing)", if (batteryFree) Live else Warn)
        if (!batteryFree) OutlinedButton(onClick = actions.openBatterySettings) { Text("Don't optimise battery") }
    }
}
