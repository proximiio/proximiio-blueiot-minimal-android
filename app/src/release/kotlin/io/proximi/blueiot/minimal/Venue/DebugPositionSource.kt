//
//  DebugPositionSource.kt
//  BlueiotMinimal
//
//  Release builds: the cloud relay is always the position source. The debug file of the
//  same name in `src/debug` can replace it with a journey playback.
//
package io.proximi.blueiot.minimal

import android.content.Intent
import io.proximi.sdk.Proximiio

object DebugPositionSource {
    @Suppress("UNUSED_PARAMETER")
    fun readLaunch(intent: Intent?) = Unit

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun attach(sdk: Proximiio): Result<String>? = null
}
