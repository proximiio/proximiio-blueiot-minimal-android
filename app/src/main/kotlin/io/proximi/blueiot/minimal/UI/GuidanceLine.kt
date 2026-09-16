//
//  GuidanceLine.kt
//  BlueiotMinimal
//
//  TURN-BY-TURN, IN ONE LINE.
//
//  The map library follows whatever route it is drawing and republishes a
//  `RouteGuidance` on every fix — which manoeuvre is next, how far is still to walk
//  to it, whether the visitor left the corridor, whether they arrived. One value
//  rather than six properties, so a composable that reads four of them recomposes once.
//
//  A single route and a journey leg produce the same value, which is why this is a
//  composable of its own rather than two copies of the same sentence.
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
     * One sentence for one fix, in the order a walker needs them: arrival ends the
     * walk, leaving the route interrupts it, and otherwise it is the turn in hand
     * and the metres still to walk to it.
     *
     * `distanceToManoeuvreMeters` is the number that shrinks —
     * `RouteManoeuvre.legMeters` is the planned length of the leg and never moves.
     *
     * Leaving the route is said, not acted on. The flag latches after three fixes
     * beyond twelve metres and clears itself on the first fix back inside, so a
     * detector of this app's own could only disagree with the one already running.
     */
    fun sentence(guidance: RouteGuidance): String {
        if (guidance.hasArrived) return "You have arrived."
        if (guidance.isOffRoute) return "You have left the route."
        val metres = guidance.distanceToManoeuvreMeters.roundToInt()
        return "${instruction(guidance.manoeuvre?.kind)} · $metres m"
    }

    /**
     * `RouteManoeuvre.Kind` carries no display strings, and neither does the SDK's
     * `RouteInstruction.Kind` underneath it: a library that shipped English would be
     * shipping the wrong language to most venues. These sentences are the app's, and
     * this is the one function to reach `stringResource` into.
     */
    fun instruction(kind: RouteManoeuvre.Kind?): String =
        when (kind) {
            RouteManoeuvre.Kind.TurnLeft -> "Turn left"
            RouteManoeuvre.Kind.TurnSlightLeft -> "Bear left"
            RouteManoeuvre.Kind.TurnSharpLeft -> "Turn sharp left"
            RouteManoeuvre.Kind.TurnRight -> "Turn right"
            RouteManoeuvre.Kind.TurnSlightRight -> "Bear right"
            RouteManoeuvre.Kind.TurnSharpRight -> "Turn sharp right"
            // The changer the route actually uses, so the sentence and the pin on the
            // map name the same thing: "elevator", "escalator", "staircase", "ramp".
            is RouteManoeuvre.Kind.LevelChange ->
                "Take the ${kind.change.featureType} to level ${MapLevelFormat.trimmed(kind.change.toLevel)}"
            RouteManoeuvre.Kind.Arrive -> "Arrive"
            else -> "Continue straight"
        }
}
