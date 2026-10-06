//
//  DebugPositionSource.kt
//  BlueiotMinimal
//
//  Debug builds: a journey playback that replaces the wristband's positions, started by
//  the launch intent (`JourneyPlaybackLaunch`) or by the journey picker
//  (`JourneyPickerSheet`). Release builds compile the file of the same name in
//  `src/release`, which never replaces the binding's provider and shows no picker.
//
package io.proximi.blueiot.minimal

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioDiagnosticsEventKind
import io.proximi.sdk.fetchJourney
import io.proximi.sdk.journey.ProximiioJourney
import io.proximi.sdk.recordDiagnosticsEvent
import kotlinx.coroutines.CancellationException

object DebugPositionSource {
    private var request: JourneyPlaybackLaunch.Request? = null

    /** The journey playback that replaces the binding's provider, and its state for the map screen. */
    val playback = JourneyPlaybackController()

    /** Keeps the playback the launch intent requests. Called by `MainActivity`. */
    fun readLaunch(intent: Intent?) {
        request = JourneyPlaybackLaunch.request(intent)
    }

    /**
     * Called by `Venue.start` in place of attaching the binding's provider. Plays the
     * journey the launch intent requests. `true` when a journey replaces the binding's
     * provider, including one whose fetch failed.
     */
    suspend fun attachAtStart(venue: Venue): Boolean {
        val requested = request ?: return false
        playJourney(venue, requested.journeyId, requested.options)
        return true
    }

    /**
     * Fetches a journey by id and plays it in place of the attached provider. The launch
     * intent uses this call. On a failed fetch nothing is attached, and [playback] shows
     * the reason.
     */
    suspend fun playJourney(
        venue: Venue,
        id: String,
        options: JourneyPlaybackOptions,
    ) {
        playback.begin(id)
        try {
            playJourney(venue, venue.sdk.fetchJourney(id), options)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            venue.detachProvider()
            val reason = error.message ?: error.toString()
            Proximiio.recordDiagnosticsEvent(ProximiioDiagnosticsEventKind.state, "journey playback failed: $reason")
            playback.fail(reason)
        }
    }

    /**
     * Plays [journey] in place of the attached provider, usually the binding's. The journey
     * picker uses this call. [journey] must pass `validationFailure()`; the provider plays
     * what it is given.
     */
    suspend fun playJourney(
        venue: Venue,
        journey: ProximiioJourney,
        options: JourneyPlaybackOptions,
    ) {
        venue.detachProvider()
        playback.end()
        playback.begin(journey.displayName)
        val provider = JourneyPlaybackLaunch.provider(journey, options)
        Proximiio.recordDiagnosticsEvent(
            ProximiioDiagnosticsEventKind.state,
            "journey playback: ${journey.displayName}, ${options.logLine}",
        )
        venue.attach(provider)
        playback.attached(provider)
    }

    /** Detaches the playback and attaches the binding's provider again, as at launch. */
    suspend fun stopJourney(venue: Venue) {
        venue.detachProvider()
        playback.end()
        venue.attachRelay()
    }

    /** The picker button, or the playback controls while a journey plays. */
    @Composable
    fun Overlay(
        venue: Venue,
        modifier: Modifier,
    ) {
        JourneyPlaybackOverlay(venue = venue, playback = playback, modifier = modifier)
    }
}
