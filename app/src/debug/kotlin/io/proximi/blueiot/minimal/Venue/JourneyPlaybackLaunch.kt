//
//  JourneyPlaybackLaunch.kt
//  BlueiotMinimal
//
//  Debug builds only. Plays a journey stored on Proximi.io in place of the cloud relay,
//  for testing the app away from the venue. Intent extras on the launch:
//
//    journeyPlayback <id>   the journey, `<organisation uuid>:<uuid>`
//    journeySpeed <x>       optional, 0.5 to 10, default 1
//    journeyLoop <bool>     optional, start again after the last waypoint
//
//  The journey is fetched once with the application token. The positions are generated
//  on the phone. This file is in the `debug` source set, so release builds do not
//  contain it.
//
package io.proximi.blueiot.minimal

import android.content.Intent
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioDiagnosticsEventKind
import io.proximi.sdk.attachPositionProvider
import io.proximi.sdk.fetchJourney
import io.proximi.sdk.journey.JourneyPlaybackConfiguration
import io.proximi.sdk.journey.JourneyPlaybackProvider
import io.proximi.sdk.recordDiagnosticsEvent
import kotlinx.coroutines.CancellationException

object JourneyPlaybackLaunch {
    const val JOURNEY_EXTRA = "journeyPlayback"
    const val SPEED_EXTRA = "journeySpeed"
    const val LOOP_EXTRA = "journeyLoop"

    /** The playback the launch extras request. */
    data class Request(
        val journeyId: String,
        val speed: Double,
        val loops: Boolean,
    )

    /**
     * Reads the launch extras through [extra], which returns an extra's value or `null`.
     * Returns `null` when `journeyPlayback` is absent or blank.
     *
     * `adb shell am start` sends `--ef` as a `Float`, `--es` as a `String` and `--ez` as
     * a `Boolean`, so the speed and the loop flag are accepted in each form. A speed that
     * is not a number is 1. A loop value of `false`, `no` or `0` is off.
     */
    fun request(extra: (String) -> Any?): Request? {
        val id = (extra(JOURNEY_EXTRA) as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val speed =
            when (val value = extra(SPEED_EXTRA)) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            } ?: 1.0
        val loops =
            when (val value = extra(LOOP_EXTRA)) {
                null -> false
                is Boolean -> value
                is Number -> value.toDouble() != 0.0
                else -> value.toString().trim().lowercase() !in setOf("false", "no", "0")
            }
        return Request(journeyId = id, speed = speed, loops = loops)
    }

    /** Reads the extras of the launch intent. */
    fun request(intent: Intent?): Request? {
        val extras = intent?.extras ?: return null
        // `Bundle.get` is the only untyped read, and the extras arrive in several types.
        @Suppress("DEPRECATION")
        return request { key -> extras.get(key) }
    }

    /**
     * Fetches the journey and attaches its playback. Returns the provider name, or the
     * failure when the journey cannot be fetched; the diagnostics log records the
     * reason. The relay is not attached in either case.
     */
    suspend fun attach(
        request: Request,
        sdk: Proximiio,
    ): Result<String> =
        try {
            val journey = sdk.fetchJourney(request.journeyId)
            val provider =
                JourneyPlaybackProvider(
                    journey = journey,
                    configuration =
                        JourneyPlaybackConfiguration(
                            speed = request.speed,
                            loops = request.loops,
                            // As for the relay: keep playing with the screen locked.
                            runsInBackground = true,
                        ),
                )
            val looping = if (request.loops) ", looping" else ""
            Proximiio.recordDiagnosticsEvent(
                ProximiioDiagnosticsEventKind.state,
                "journey playback: ${journey.displayName}, ${provider.configuration.speed}x$looping",
            )
            sdk.attachPositionProvider(provider)
            Result.success(provider.name)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Proximiio.recordDiagnosticsEvent(
                ProximiioDiagnosticsEventKind.state,
                "journey playback failed: ${error.message ?: error}",
            )
            Result.failure(error)
        }
}
