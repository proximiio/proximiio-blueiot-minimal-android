//
//  GuidanceLine.kt
//  BlueiotMinimal
//
//  Renders one `RouteGuidance` as a single line of text. The map library follows the
//  route it is drawing and republishes a `RouteGuidance` on every position: the next
//  manoeuvre, the distance left to it, whether the visitor is off route, and whether
//  they have arrived.
//
//  A single route and a journey leg produce the same value, so both screens use this
//  composable.
//
package io.proximi.blueiot.minimal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import io.proximi.map.core.MapLevelFormat
import io.proximi.map.core.RouteGuidance
import io.proximi.map.core.RouteManoeuvre
import kotlin.math.roundToInt

@Composable
fun GuidanceLine(guidance: RouteGuidance?) {
    if (guidance == null) return
    Text(
        text = GuidanceLine.sentence(guidance),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

object GuidanceLine {
    /**
     * One sentence for one position, in priority order: arrival, off route, then the
     * next manoeuvre and the distance to it.
     *
     * `distanceToManoeuvreMeters` is the remaining distance to the next manoeuvre.
     * `RouteManoeuvre.legMeters` is the planned length of the leg and does not change.
     *
     * Being off route is reported, not acted on. `RouteGuidance.isOffRoute` latches
     * after `RouteFollowRules.offRouteFixes` consecutive positions beyond
     * `offRouteMeters` (3 and 12 m in `RouteFollowRules.VENUE_WALK`) and clears on the
     * first position back inside, so this app adds no detector of its own.
     */
    fun sentence(guidance: RouteGuidance): String {
        if (guidance.hasArrived) return "You have arrived."
        if (guidance.isOffRoute) return "You have left the route."
        val metres = guidance.distanceToManoeuvreMeters.roundToInt()
        return "${instruction(guidance.manoeuvre?.kind)} · $metres m"
    }

    /**
     * `RouteManoeuvre.Kind` carries no display strings, and neither does the SDK's
     * `RouteInstruction.Kind` beneath it, so these sentences are the app's. This is the
     * one function to localise.
     */
    fun instruction(kind: RouteManoeuvre.Kind?): String =
        when (kind) {
            RouteManoeuvre.Kind.TurnLeft -> "Turn left"
            RouteManoeuvre.Kind.TurnSlightLeft -> "Bear left"
            RouteManoeuvre.Kind.TurnSharpLeft -> "Turn sharp left"
            RouteManoeuvre.Kind.TurnRight -> "Turn right"
            RouteManoeuvre.Kind.TurnSlightRight -> "Bear right"
            RouteManoeuvre.Kind.TurnSharpRight -> "Turn sharp right"
            // The level changer the route uses, so the sentence and the map pin name the
            // same feature: "elevator", "escalator", "staircase", "ramp".
            is RouteManoeuvre.Kind.LevelChange ->
                "Take the ${kind.change.featureType} to level ${MapLevelFormat.trimmed(kind.change.toLevel)}"
            RouteManoeuvre.Kind.Arrive -> "Arrive"
            else -> "Continue straight"
        }
}
