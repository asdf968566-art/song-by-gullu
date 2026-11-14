package com.dxdepartment.screenmonitor.capture

import java.util.concurrent.atomic.AtomicInteger

class Frame(val jpeg: ByteArray, val width: Int, val height: Int, val version: Long, val at: Long)

/**
 * The newest encoded picture, handed from the capture thread to any number of viewer threads.
 * Viewers wait for a newer version instead of polling, so nothing is encoded twice or sent twice.
 */
object FrameHub {
    private val lock = Object()
    private var latest: Frame? = null
    private var version = 0L

    /** Viewers currently connected (the capture engine encodes at full speed only while > 0). */
    val viewers = AtomicInteger(0)

    /** Someone (the app's own preview, a snapshot request) wants a picture soon. */
    @Volatile var previewWanted: Boolean = false

    fun publish(jpeg: ByteArray, width: Int, height: Int) {
        synchronized(lock) {
            version++
            latest = Frame(jpeg, width, height, version, System.currentTimeMillis())
            lock.notifyAll()
        }
    }

    fun latest(): Frame? = synchronized(lock) { latest }

    /** Waits until a picture newer than [afterVersion] exists, at most [timeoutMs]; returns the newest one (maybe the same). */
    fun awaitNewer(afterVersion: Long, timeoutMs: Long): Frame? {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (version <= afterVersion) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                lock.wait(left)
            }
            return latest
        }
    }

    fun clear() {
        synchronized(lock) {
            latest = null
            lock.notifyAll()
        }
    }
}
