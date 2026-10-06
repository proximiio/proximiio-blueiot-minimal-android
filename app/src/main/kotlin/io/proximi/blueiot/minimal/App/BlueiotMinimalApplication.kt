//
//  BlueiotMinimalApplication.kt
//  BlueiotMinimal
//
//  The process entry point. It starts the SDK's diagnostics log before any Activity
//  exists and holds the wristband session. The launch order is in `MainActivity`.
//
package io.proximi.blueiot.minimal

import android.app.Application
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioDiagnosticsEventKind
import io.proximi.sdk.ProximiioDiagnosticsRecordingOptions
import io.proximi.sdk.ProximiioDiagnosticsReportError
import io.proximi.sdk.recordDiagnosticsEvent
import io.proximi.sdk.startDiagnosticsRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlueiotMinimalApplication : Application() {
    /** Lives as long as the process. It runs the suspending calls below. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The wristband session, one per process. Created on first use, which is the first
     * frame of `RootScreen`; creating it runs `restore()`.
     */
    val wristband: WristbandSession by lazy { WristbandSession(this, scope) }

    override fun onCreate() {
        super.onCreate()
        // Installed before the recording starts: `capturesSDKLog` chains to the sink that
        // is installed at that moment, so debug builds get both logcat and the file.
        SdkLogcat.installInDebugBuilds()
        // Starts the SDK diagnostics log (README, "The diagnostics log"). Lines recorded
        // before the call returns are dropped. `capturesSDKLog = true` includes the SDK's
        // own log lines (fixes, floors, relay state, warnings). If the log cannot be
        // written, the app runs without one.
        scope.launch {
            try {
                Proximiio.startDiagnosticsRecording(this@BlueiotMinimalApplication, recordingOptions())
            } catch (_: ProximiioDiagnosticsReportError) {
                return@launch
            }
            val enabled = NotificationManagerCompat.from(this@BlueiotMinimalApplication).areNotificationsEnabled()
            Proximiio.recordDiagnosticsEvent(
                ProximiioDiagnosticsEventKind.state,
                "notifications: ${if (enabled) "enabled" else "disabled"}",
            )
        }
    }

    companion object {
        /** The process's [WristbandSession]. */
        fun wristband(context: Context): WristbandSession = (context.applicationContext as BlueiotMinimalApplication).wristband

        /** The recording options. Separate so a test can read them. */
        fun recordingOptions(): ProximiioDiagnosticsRecordingOptions =
            ProximiioDiagnosticsRecordingOptions(
                capturesSDKLog = true,
                additionalSecrets = VenueConfiguration.secrets,
            )
    }
}
