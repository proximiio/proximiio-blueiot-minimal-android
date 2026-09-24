//
//  SdkLogcat.kt
//  BlueiotMinimal
//
//  Forwards the SDK's log to logcat in debug builds. The SDK installs no log sink of its
//  own, so without this `adb logcat` shows nothing from the SDK. Release builds install
//  nothing. `BlueiotMinimalApplication` installs it before the diagnostics recording
//  starts, and the recording forwards each line to it, so both receive the SDK's log.
//  Replace it with the product's own logger or crash reporter.
//
package io.proximi.blueiot.minimal

import android.util.Log
import io.proximi.sdk.Proximiio
import io.proximi.sdk.core.logging.ProximiioLogEntry
import io.proximi.sdk.core.logging.ProximiioLogLevel

object SdkLogcat {
    /** Every line is tagged `Proximiio/<category>`, for example `Proximiio/Lifecycle`. */
    private const val TAG_PREFIX = "Proximiio/"

    /**
     * Installs the sink in debug builds when none is installed.
     *
     * The SDK calls the sink synchronously on its logging thread, so it only hands the
     * line to `Log.println`. Messages carry no token.
     */
    fun installInDebugBuilds() {
        if (!BuildConfig.DEBUG || Proximiio.logSink != null) return
        Proximiio.logSink = { entry -> Log.println(priority(entry.level), tag(entry), entry.message) }
    }

    fun tag(entry: ProximiioLogEntry): String = TAG_PREFIX + entry.category

    /** `NOTICE` has no logcat level of its own and is written as `INFO`. */
    fun priority(level: ProximiioLogLevel): Int =
        when (level) {
            ProximiioLogLevel.DEBUG -> Log.DEBUG
            ProximiioLogLevel.INFO, ProximiioLogLevel.NOTICE -> Log.INFO
            ProximiioLogLevel.WARNING -> Log.WARN
            ProximiioLogLevel.ERROR -> Log.ERROR
        }
}
