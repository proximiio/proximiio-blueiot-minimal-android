//
//  VisitRules.kt
//  BlueiotMinimal
//
//  The rules behind the visit's text and controls: when a new visit is put in the
//  shortest order, what the plan says about the order, what the bar says during a
//  stop-off, and ending the navigator once. `JourneyBar` and `JourneyPlanSheet` call
//  these and hold no rule of their own.
//
package io.proximi.blueiot.minimal

import io.proximi.map.core.Journey
import io.proximi.map.core.JourneyOrderProposal
import io.proximi.map.core.JourneyStop
import kotlin.math.roundToInt

/** The shortest order applied once to a new visit, without a tap. */
object StartOrder {
    /**
     * Whether a visit can still be put in the shortest order without a tap.
     *
     * `true` while no stop is reached, done or skipped and no stop-off is in the plan.
     * The stop being walked to may still move. A visit restored from disk is not checked
     * here: `JourneyBar` orders only a visit it created.
     */
    fun isOwed(journey: Journey): Boolean =
        journey.stops.size > 1 &&
            journey.stops.all {
                (it.state == JourneyStop.State.PENDING || it.state == JourneyStop.State.ACTIVE) &&
                    it.kind == JourneyStop.Kind.PLANNED
            }

    /** Shown on the bar while no position has arrived. The order is measured on the first one. */
    const val WAITING_NOTE = "Your stops are put in the shortest order when your position arrives."

    /**
     * The note on the bar after the order was measured.
     *
     * @param proposal the result of `proposeOrder(JourneyOrderOrigin.VISITOR)`.
     * @param applied whether `apply(proposal)` took it.
     * @return `null` when nothing was measured, or when a shorter order was refused as
     *   stale.
     */
    fun note(
        proposal: JourneyOrderProposal?,
        applied: Boolean,
    ): String? {
        if (proposal == null) return null
        val meters = proposal.savedMeters.roundToInt()
        if (!proposal.isImprovement || meters < 1) return "Your stops are already in the shortest order."
        return if (applied) "Stops put in the shortest order: $meters m less to walk." else null
    }
}

/** The order row in **Your visit**. */
sealed interface OrderAdvice {
    /** Fewer than two stops can move. No row is shown. */
    data object None : OrderAdvice

    /**
     * A proposal is being measured, or the one held no longer matches the plan and is
     * measured again.
     */
    data object Measuring : OrderAdvice

    /** `proposeOrder` returned `null`: a stop in the current order has no route. */
    data object Unmeasurable : OrderAdvice

    /** No order is at least 1 m shorter. */
    data object AlreadyShortest : OrderAdvice

    /** A shorter order, [meters] less to walk. A tap applies it. */
    data class Save(
        val meters: Int,
    ) : OrderAdvice

    /** The row's text. [Save] is a button label; the others are plain text. */
    val text: String?
        get() =
            when (this) {
                None -> null
                Measuring -> "Measuring the shortest order…"
                Unmeasurable -> "The order cannot be measured: a stop has no route."
                AlreadyShortest -> "Your stops are already in the shortest order."
                is Save -> "Save $meters m by reordering"
            }

    companion object {
        fun of(
            proposal: JourneyOrderProposal?,
            movableStops: Int,
            isMeasuring: Boolean,
            canApply: Boolean,
        ): OrderAdvice {
            if (movableStops <= 1) return None
            if (isMeasuring) return Measuring
            if (proposal == null) return Unmeasurable
            if (!canApply) return Measuring
            val meters = proposal.savedMeters.roundToInt()
            return if (proposal.isImprovement && meters >= 1) Save(meters) else AlreadyShortest
        }
    }
}

/**
 * The text of a stop-off: a stop at the nearest place of one kind, inserted before the
 * planned stop by `JourneyNavigator.detour(stop)`.
 */
object StopOff {
    /** The header of the **Stop off** menu. */
    fun menuHeader(goingTo: String?): String {
        if (goingTo == null) return "Go to the nearest one first. Your plan continues afterwards."
        return "Go to the nearest one before $goingTo. Your plan continues afterwards."
    }

    /** The bar's title during a stop-off. */
    fun title(stop: JourneyStop): String = "Stop off: ${stop.title}"

    /**
     * The line under the title during a stop-off.
     *
     * @param hasArrived whether the visitor has reached the stop-off.
     * @param next the planned stop the visit returns to, or `null` when none is left.
     */
    fun status(
        hasArrived: Boolean,
        next: String?,
    ): String {
        val then = next?.let { "then on to $it" } ?: "then the visit ends"
        return if (hasArrived) {
            "Tap Continue when you are done, $then."
        } else {
            "On the way, $then. Back to the plan cancels the stop-off."
        }
    }
}

/**
 * Ends a visit's navigator exactly once, before the map screen takes over.
 *
 * `JourneyNavigator.end()` sets `session.guidanceRules` to `null`. The map screen's
 * `onEnd` sets the single-route rules again. [end] calls `end()` first and then hands
 * over, and [disposed], called when the bar leaves the screen, does nothing after that.
 * A second `end()` after `onEnd` would switch single-route guidance off, with no
 * instruction line and no off-route line.
 */
class VisitEnding(
    private val endNavigator: () -> Unit,
) {
    /** `true` once the navigator has been ended. */
    var hasEnded: Boolean = false
        private set

    /** Ends the navigator, then calls [handBack]: the map screen's `onEnd`. */
    fun end(handBack: () -> Unit) {
        endOnce()
        handBack()
    }

    /** The bar left the screen. Ends the navigator unless [end] already did. */
    fun disposed() = endOnce()

    private fun endOnce() {
        if (hasEnded) return
        hasEnded = true
        endNavigator()
    }
}
