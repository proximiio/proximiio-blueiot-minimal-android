//
//  DiagnosticsTests.kt
//  BlueiotMinimalTests
//
//  The diagnostics log leaves the phone: support asks for the report and the visitor
//  sends it. The SDK removes the credential shapes it recognises on its own. The two
//  values this app is built with are removed only because `VenueConfiguration.secrets`
//  passes them to the recorder, and a line that carries one verbatim looks like a line
//  that does not. The report also fails silently in a second way: a `FileProvider` path
//  that does not cover the SDK's report directory throws only when a visitor taps Send.
//
package io.proximi.blueiot.minimal

import android.app.Application
import android.content.Intent
import android.net.Uri
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioDiagnosticsEventKind
import io.proximi.sdk.ProximiioDiagnosticsRecordingOptions
import io.proximi.sdk.recordDiagnosticsEvent
import io.proximi.sdk.startDiagnosticsRecording
import io.proximi.sdk.stopDiagnosticsRecording
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

// The plain `Application` rather than the app's own, so no second recording starts
// under the test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiagnosticsTests {
    /**
     * The two real values' shape: long, and with no `token=` or `Bearer` in front of
     * them, so only the value passed to the recorder can remove them.
     */
    private val secrets = listOf("SENTINEL-APP-TOKEN-8f2a1c", "SENTINEL-RELAY-TOKEN-1c04e7")
    private val directory: File = Files.createTempDirectory("diagnostics").toFile()

    @After
    fun tearDown() {
        runBlocking { Proximiio.stopDiagnosticsRecording() }
        directory.deleteRecursively()
    }

    @Test
    fun theLogNeverCarriesAConfiguredSecretVerbatim() =
        runBlocking {
            Proximiio.stopDiagnosticsRecording()
            Proximiio.startDiagnosticsRecording(
                null,
                ProximiioDiagnosticsRecordingOptions(directory = directory, additionalSecrets = secrets),
            )
            Proximiio.recordDiagnosticsEvent(
                ProximiioDiagnosticsEventKind.state,
                "relay answered 401 for ${secrets[1]} under ${secrets[0]}",
            )
            Proximiio.stopDiagnosticsRecording()

            val log = File(directory, "proximiio-diagnostics.log").readText()
            assertTrue("the line itself was not written", log.contains("relay answered 401"))
            for (secret in secrets) {
                assertFalse("$secret written verbatim", log.contains(secret))
            }
        }

    /** The report the SDK writes is inside the directory the app's `FileProvider` exposes. */
    @Test
    fun theReportIsSharedThroughTheAppsFileProvider() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val report = SupportReport.prepare(context, sdk = null)

            val chooser = SupportReport.shareIntent(context, report)
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("${context.packageName}.diagnostics", uri.authority)
            assertEquals("application/zip", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }
}
