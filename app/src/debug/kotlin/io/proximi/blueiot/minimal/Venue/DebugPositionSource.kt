//
//  DebugPositionSource.kt
//  BlueiotMinimal
//
//  Debug builds: a position source that replaces the cloud relay when the launch intent
//  asks for one (`JourneyPlaybackLaunch`). Release builds compile the file of the same
//  name in `src/release`, which never replaces the relay.
//
package io.proximi.blueiot.minimal

import android.content.Intent
import io.proximi.sdk.Proximiio

object DebugPositionSource {
    private var request: JourneyPlaybackLaunch.Request? = null

    /** Keeps the playback the launch intent requests. Called by `MainActivity`. */
    fun readLaunch(intent: Intent?) {
        request = JourneyPlaybackLaunch.request(intent)
    }

    /**
     * Attaches the requested playback in place of the relay. `null` when none was
     * requested. Otherwise the provider name, or the failure; the relay stays detached
     * either way.
     */
    suspend fun attach(sdk: Proximiio): Result<String>? = request?.let { JourneyPlaybackLaunch.attach(it, sdk) }
}
