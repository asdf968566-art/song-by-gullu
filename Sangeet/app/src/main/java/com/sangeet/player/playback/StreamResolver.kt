package com.sangeet.player.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.sangeet.player.data.LibraryRepository
import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.download.DownloadRepository
import com.sangeet.player.data.model.SourceType
import java.io.File
import java.io.IOException

/**
 * "sangeet://track/<id>" ko asli jagah mein badalta hai:
 *  1. Download ho chuka hai -> app ke andar ki file (offline)
 *  2. Phone ka gaana -> content:// uri
 *  3. Online -> Wi-Fi / mobile data quality ke hisaab se stream URL
 * Ye player ke loader thread pe chalta hai, isliye blocking lookup theek hai.
 */
@OptIn(UnstableApi::class)
class StreamResolver(
    private val library: LibraryRepository,
    private val online: OnlineRepository,
    private val downloads: DownloadRepository,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val id = MediaItems.trackIdFrom(dataSpec.uri) ?: return dataSpec

        downloads.localPath(id)?.let { path ->
            return dataSpec.withUri(Uri.fromFile(File(path)))
        }

        val track = library.findBlocking(id) ?: throw IOException("Gaana nahi mila: $id")
        val isDeviceFile = track.streamUrl?.let { it.startsWith("content:") || it.startsWith("file:") } == true
        if (track.source == SourceType.LOCAL || isDeviceFile) {
            val uri = track.streamUrl ?: throw IOException("File missing")
            return dataSpec.withUri(Uri.parse(uri))
        }
        if (!online.canGoOnline) throw IOException("Internet nahi hai / Offline mode on hai")
        val url = online.streamUrl(track, online.streamingQuality())
            ?: throw IOException("Stream URL nahi bana")
        return dataSpec.withUri(Uri.parse(url))
    }
}
