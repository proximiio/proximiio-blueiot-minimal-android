//
//  DebugPositionSource.kt
//  BlueiotMinimal
//
//  Release builds: the cloud relay is always the position source, and the map shows no
//  journey picker. The debug file of the same name in `src/debug` can replace the relay
//  with a journey playback.
//
package io.proximi.blueiot.minimal

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object DebugPositionSource {
    @Suppress("UNUSED_PARAMETER")
    fun readLaunch(intent: Intent?) = Unit

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun follow(venue: Venue): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun Overlay(
        venue: Venue,
        modifier: Modifier,
    ) = Unit
}
