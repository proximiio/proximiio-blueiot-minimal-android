//
//  JourneyPlayback.kt
//  BlueiotMinimal
//
//  Debug builds only. The rules behind the journey picker and its playback controls: the
//  picker's rows and list states, the playback options, and the playback session state.
//  The views are in `JourneyPickerSheet.kt`; the attach and detach calls are in
//  `DebugPositionSource.kt`. This file is in the `debug` source set, so release builds
//  do not contain it.
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.journey.JourneyPlaybackProvider
import io.proximi.sdk.journey.JourneyPlaybackState
import io.proximi.sdk.journey.ProximiioJourney
import io.proximi.sdk.journey.ProximiioJourneyTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** How a journey is played. Both the launch extras and the picker produce one. */
data class JourneyPlaybackOptions(
    val speed: Double = 1.0,
    val loops: Boolean = false,
) {
    /** The diagnostics log text, for example `2x, looping`. */
    val logLine: String get() = JourneyFormat.speed(speed) + if (loops) ", looping" else ""

    companion object {
        /** The speeds the picker offers, in journey seconds per real second. */
        val pickerSpeeds: List<Double> = listOf(1.0, 2.0, 5.0)
    }
}

/** One journey in the picker. */
data class JourneyPickerRow(
    val id: String,
    val journey: ProximiioJourney,
    val title: String,
    /**
     * Distance, duration, waypoint count and levels. `null` for a journey that cannot be
     * played; its timeline is not meaningful.
     */
    val summary: String?,
    /** `validationFailure()` of the journey. `null` for a playable journey. */
    val failure: String?,
) {
    val isPlayable: Boolean get() = failure == null

    companion object {
        /** One row per journey, in the order given. A journey without an id is identified by its position. */
        fun rows(journeys: List<ProximiioJourney>): List<JourneyPickerRow> =
            journeys.mapIndexed { index, journey ->
                val failure = journey.validationFailure()
                val name = journey.displayName
                JourneyPickerRow(
                    id = journey.id ?: "journey-$index",
                    journey = journey,
                    title = name.ifEmpty { "Untitled journey" },
                    summary = if (failure == null) summary(journey) else null,
                    failure = failure,
                )
            }

        /** For example `412 m · 6 min · 9 waypoints · levels 0, 1`. */
        fun summary(journey: ProximiioJourney): String {
            val timeline = ProximiioJourneyTimeline(journey)
            val levels = journey.waypoints.mapNotNull { it.level?.toInt() }.toSortedSet().toList()
            val levelText = if (levels.size == 1) "level ${levels[0]}" else "levels " + levels.joinToString(", ")
            return listOf(
                JourneyFormat.distance(timeline.totalDistance),
                JourneyFormat.duration(timeline.duration),
                "${journey.waypoints.size} waypoints",
                levelText,
            ).joinToString(" · ")
        }
    }
}

/** What the picker shows: a spinner, a message or the list. */
sealed interface JourneyPickerContent {
    data object Loading : JourneyPickerContent

    data object Empty : JourneyPickerContent

    data class Failed(val message: String) : JourneyPickerContent

    data class Loaded(val rows: List<JourneyPickerRow>) : JourneyPickerContent

    companion object {
        /** The content for the result of `Proximiio.journeys()`. */
        fun from(result: Result<List<ProximiioJourney>>): JourneyPickerContent =
            result.fold(
                onSuccess = { journeys -> if (journeys.isEmpty()) Empty else Loaded(JourneyPickerRow.rows(journeys)) },
                onFailure = { error -> Failed(error.message ?: error.toString()) },
            )
    }
}

/**
 * The playback controls' state. A value without SDK calls; [JourneyPlaybackController]
 * applies it to the provider. Each transition returns the next state.
 *
 * `Off` shows no controls. [begin] → `Starting` → [attached] → `Playing`; [pause] and
 * [resume] switch between `Playing` and `Paused`; the provider's `FINISHED` or `STOPPED`
 * state ends in `Finished`; a failed fetch ends in `Failed`. [end] returns to `Off` from
 * every state.
 */
data class JourneyPlaybackSession(
    val phase: Phase = Phase.Off,
    val title: String = "",
    val elapsed: Double = 0.0,
    val duration: Double = 0.0,
) {
    sealed interface Phase {
        data object Off : Phase

        data object Starting : Phase

        data object Playing : Phase

        data object Paused : Phase

        data object Finished : Phase

        data class Failed(val reason: String) : Phase
    }

    /** Whether the controls are on screen. When `false` the picker button is. */
    val showsControls: Boolean get() = phase != Phase.Off
    val canPause: Boolean get() = phase == Phase.Playing
    val canResume: Boolean get() = phase == Phase.Paused

    /** The second line of the controls. */
    val status: String
        get() =
            when (phase) {
                Phase.Off -> ""
                Phase.Starting -> "Starting…"
                Phase.Playing -> "Playing · $progress"
                Phase.Paused -> "Paused · $progress"
                Phase.Finished -> "Finished"
                is Phase.Failed -> "Failed: ${phase.reason}"
            }

    private val progress: String get() = "${JourneyFormat.clock(elapsed)} / ${JourneyFormat.clock(duration)}"

    fun begin(title: String): JourneyPlaybackSession = JourneyPlaybackSession(phase = Phase.Starting, title = title)

    fun attached(
        title: String,
        duration: Double,
    ): JourneyPlaybackSession = if (phase == Phase.Starting) copy(phase = Phase.Playing, title = title, duration = duration) else this

    fun fail(reason: String): JourneyPlaybackSession = if (phase == Phase.Starting) copy(phase = Phase.Failed(reason)) else this

    /** The paused state, or `null` when there is nothing to pause. */
    fun pause(): JourneyPlaybackSession? = if (phase == Phase.Playing) copy(phase = Phase.Paused) else null

    /** The playing state, or `null` when there is nothing to resume. */
    fun resume(): JourneyPlaybackSession? = if (phase == Phase.Paused) copy(phase = Phase.Playing) else null

    /**
     * Applies the provider's diagnostics. Only a running or paused session follows them;
     * `Off`, `Starting` and `Failed` belong to the app.
     */
    fun observe(
        state: JourneyPlaybackState,
        elapsed: Double,
    ): JourneyPlaybackSession {
        if (phase != Phase.Playing && phase != Phase.Paused) return this
        val ended = state == JourneyPlaybackState.FINISHED || state == JourneyPlaybackState.STOPPED
        return copy(elapsed = elapsed, phase = if (ended) Phase.Finished else phase)
    }

    fun end(): JourneyPlaybackSession = JourneyPlaybackSession()
}

/**
 * The playback that is attached, and its session for the views. `DebugPositionSource`
 * attaches and detaches the provider. Called on the main thread only.
 */
class JourneyPlaybackController {
    private val mutableSession = MutableStateFlow(JourneyPlaybackSession())
    val session: StateFlow<JourneyPlaybackSession> = mutableSession.asStateFlow()
    private var provider: JourneyPlaybackProvider? = null

    /** Shows `Starting…`. The provider attached before stays until [end] or [attached]. */
    fun begin(title: String) {
        mutableSession.value = mutableSession.value.begin(title)
    }

    fun attached(provider: JourneyPlaybackProvider) {
        this.provider = provider
        mutableSession.value = mutableSession.value.attached(provider.journey.displayName, provider.timeline.duration)
    }

    fun fail(reason: String) {
        mutableSession.value = mutableSession.value.fail(reason)
    }

    suspend fun togglePause() {
        val current = mutableSession.value
        current.pause()?.let {
            mutableSession.value = it
            provider?.pausePlayback()
            return
        }
        current.resume()?.let {
            mutableSession.value = it
            provider?.resumePlayback()
        }
    }

    /** Reads the provider's elapsed time and state. The controls call it once a second. */
    fun refresh() {
        val diagnostics = provider?.diagnostics?.value ?: return
        mutableSession.value = mutableSession.value.observe(diagnostics.state, diagnostics.elapsed)
    }

    /** Ends the provider's sample streams and clears the session. Call after detaching. */
    suspend fun end() {
        val ending = provider
        provider = null
        mutableSession.value = mutableSession.value.end()
        ending?.finish()
    }
}

/** The picker's and the controls' number formats. */
object JourneyFormat {
    /** `85 m`, `1.2 km`. */
    fun distance(metres: Double): String =
        if (metres < 1000) "${metres.roundToInt()} m" else String.format(Locale.ROOT, "%.1f km", metres / 1000)

    /** `45 s`, `6 min`, `1 h 5 min`. */
    fun duration(seconds: Double): String {
        val total = seconds.roundToInt()
        if (total < 60) return "$total s"
        val minutes = (total + 30) / 60
        if (minutes < 60) return "$minutes min"
        return "${minutes / 60} h ${minutes % 60} min"
    }

    /** `3:07`, `1:02:05`. */
    fun clock(seconds: Double): String {
        val total = max(0, seconds.toInt())
        val (h, m, s) = Triple(total / 3600, total / 60 % 60, total % 60)
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** `1x`, `2.5x`. */
    fun speed(speed: Double): String = if (speed % 1.0 == 0.0) "${speed.toInt()}x" else "${speed}x"
}
