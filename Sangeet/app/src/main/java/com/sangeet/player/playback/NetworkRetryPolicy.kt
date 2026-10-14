package com.sangeet.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Tries a load again for longer only when the connection is the problem (report #41): a timeout, a dropped or
 * refused connection, no DNS. Anything else (a refused YouTube link, a missing song) gives up after 3 tries as
 * before, so the next song comes quickly.
 */
@OptIn(UnstableApi::class)
class NetworkRetryPolicy : DefaultLoadErrorHandlingPolicy(CONNECTION_RETRIES) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (connectionProblem(info.exception)) return minOf(info.errorCount * 1_000L, 3_000L)
        if (info.errorCount > OTHER_RETRIES) return C.TIME_UNSET
        return super.getRetryDelayMsFor(info)
    }

    companion object {
        private const val CONNECTION_RETRIES = 10
        private const val OTHER_RETRIES = 3

        /** The connection broke, timed out or couldn't be made (not a server that answered "no"). */
        fun connectionProblem(e: Throwable?): Boolean {
            var cause = e
            while (cause != null) {
                if (cause is HttpDataSource.InvalidResponseCodeException) return false
                // No internet when the song's link was looked up (StreamResolver).
                if (cause is androidx.media3.datasource.DataSourceException &&
                    cause.reason == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                ) return true
                if (cause is java.net.SocketTimeoutException || cause is java.net.UnknownHostException ||
                    cause is java.net.ConnectException || cause is java.net.SocketException ||
                    cause is javax.net.ssl.SSLException
                ) return true
                cause = cause.cause
            }
            return false
        }
    }
}
