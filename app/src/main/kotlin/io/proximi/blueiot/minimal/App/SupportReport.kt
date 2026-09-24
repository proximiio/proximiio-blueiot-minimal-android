//
//  SupportReport.kt
//  BlueiotMinimal
//
//  Sends the SDK's diagnostics report to another app, for example an email client. The
//  button is in the long-press sheet on the map and on the "Cannot reach the venue"
//  screen. The iOS app has no such action; see README, "The diagnostics log".
//
package io.proximi.blueiot.minimal

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioDiagnosticsReport
import io.proximi.sdk.ProximiioDiagnosticsReportError
import io.proximi.sdk.prepareDiagnosticsReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SupportReport {
    /**
     * The `FileProvider` authority declared in `AndroidManifest.xml`. The provider
     * exposes `cacheDir/proximiio-reports/` only, which is where the SDK writes the
     * report (`res/xml/diagnostics_paths.xml`).
     */
    fun authority(context: Context): String = "${context.packageName}.diagnostics"

    /**
     * Builds the report. With [sdk] `null`, for example when the SDK failed to start, the
     * report holds the recorded log alone.
     *
     * @throws ProximiioDiagnosticsReportError when the report fails its redaction audit or
     *   cannot be written. No file exists to send in either case.
     */
    suspend fun prepare(
        context: Context,
        sdk: Proximiio?,
    ): ProximiioDiagnosticsReport =
        withContext(Dispatchers.IO) {
            sdk?.prepareDiagnosticsReport() ?: Proximiio.prepareDiagnosticsReport(context)
        }

    /**
     * The share intent for [report]. The receiving app is granted read access to the one
     * file; nothing else in the app's storage is exposed.
     */
    fun shareIntent(
        context: Context,
        report: ProximiioDiagnosticsReport,
    ): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), report.archiveURL)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Venue Map diagnostics report")
                .putExtra(Intent.EXTRA_TEXT, report.verdict)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(report.archiveURL.name, uri)
        return Intent.createChooser(send, "Send diagnostics report")
    }
}

/**
 * "Send diagnostics report", and the reason when no report could be built.
 *
 * @param sdk the running SDK, or `null` when it did not start.
 */
@Composable
fun SupportReportButton(sdk: Proximiio?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<String?>(null) }
    var isPreparing by remember { mutableStateOf(false) }

    Column {
        TextButton(
            enabled = !isPreparing,
            onClick = {
                isPreparing = true
                failure = null
                scope.launch {
                    try {
                        val report = SupportReport.prepare(context, sdk)
                        context.startActivity(SupportReport.shareIntent(context, report))
                    } catch (error: ProximiioDiagnosticsReportError) {
                        failure = error.message ?: "The report could not be built."
                    } finally {
                        isPreparing = false
                    }
                }
            },
        ) { Text(if (isPreparing) "Preparing report…" else "Send diagnostics report") }
        failure?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
