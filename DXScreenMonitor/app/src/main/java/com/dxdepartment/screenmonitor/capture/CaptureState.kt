package com.dxdepartment.screenmonitor.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { IDLE, STARTING, SHARING, STOPPED, ERROR }

data class CaptureStatus(
    val phase: Phase = Phase.IDLE,
    /** Why sharing stopped or failed, shown on the main screen. */
    val message: String? = null,
    val startedAt: Long? = null,
    /** Size of the shared picture (after scaling) and of the phone's screen. */
    val width: Int = 0,
    val height: Int = 0,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val rotation: Int = 0,
    /** Frames delivered by the display / pictures encoded, per second (last second). */
    val captureFps: Double = 0.0,
    val encodeFps: Double = 0.0,
    val framesTotal: Long = 0,
    val lastFrameKb: Int = 0,
    val viewers: Int = 0,
    val lastViewerAt: Long? = null,
    val lastViewerAddress: String? = null,
    val serverPort: Int? = null,
    val serverError: String? = null,
    val lowPower: Boolean = false,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
) {
    val sharing: Boolean get() = phase == Phase.SHARING || phase == Phase.STARTING
}

/** Live state of the capture service, read by the UI, the notification and the local viewer's /status. */
object CaptureState {
    private val _status = MutableStateFlow(CaptureStatus())
    val status: StateFlow<CaptureStatus> = _status.asStateFlow()

    fun update(block: (CaptureStatus) -> CaptureStatus) = _status.update(block)
}
