package com.dxdepartment.screenmonitor.capture

import com.dxdepartment.screenmonitor.settings.Quality
import com.dxdepartment.screenmonitor.settings.Resolution
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputSizeTest {
    private fun size(w: Int, h: Int, r: Resolution) = CaptureEngine.outputSize(w, h, Quality(resolution = r))

    @Test fun pixel3xlPortraitTo1080p() = assertEquals(1080 to 2220, size(1440, 2960, Resolution.P1080))

    @Test fun pixel3xlLandscapeTo1080p() = assertEquals(2220 to 1080, size(2960, 1440, Resolution.P1080))

    @Test fun pixel3xlTo720p() = assertEquals(720 to 1480, size(1440, 2960, Resolution.P720))

    @Test fun nativeKeepsSize() = assertEquals(1440 to 2960, size(1440, 2960, Resolution.NATIVE))

    @Test fun neverUpscales() = assertEquals(720 to 1280, size(720, 1280, Resolution.P1080))

    @Test fun sizesAreEven() {
        val (w, h) = size(1081, 2341, Resolution.P720)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test fun lowPowerCapsAt720pAnd10Fps() {
        val q = Quality.lowPower(Quality(resolution = Resolution.NATIVE, fps = 30))
        assertEquals(Resolution.P720, q.resolution)
        assertEquals(10, q.fps)
    }
}
