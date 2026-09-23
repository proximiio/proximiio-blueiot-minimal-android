//
//  SdkLogcatTests.kt
//  BlueiotMinimalTests
//
//  The SDK's log reaches logcat only through `SdkLogcat`. A level mapped to the wrong
//  priority hides warnings and errors behind a logcat filter.
//
package io.proximi.blueiot.minimal

import android.util.Log
import io.proximi.sdk.core.logging.ProximiioLogEntry
import io.proximi.sdk.core.logging.ProximiioLogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkLogcatTests {
    @Test
    fun everySdkLevelMapsToALogcatPriority() {
        assertEquals(Log.DEBUG, SdkLogcat.priority(ProximiioLogLevel.DEBUG))
        assertEquals(Log.INFO, SdkLogcat.priority(ProximiioLogLevel.INFO))
        assertEquals(Log.INFO, SdkLogcat.priority(ProximiioLogLevel.NOTICE))
        assertEquals(Log.WARN, SdkLogcat.priority(ProximiioLogLevel.WARNING))
        assertEquals(Log.ERROR, SdkLogcat.priority(ProximiioLogLevel.ERROR))
    }

    @Test
    fun theTagNamesTheSdkCategory() {
        val entry = ProximiioLogEntry(ProximiioLogLevel.INFO, "Lifecycle", "started", 0L)
        assertEquals("Proximiio/Lifecycle", SdkLogcat.tag(entry))
    }
}
