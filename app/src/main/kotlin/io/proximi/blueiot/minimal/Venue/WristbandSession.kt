//
//  WristbandSession.kt
//  BlueiotMinimal
//
//  The visitor's wristband session, held by the SDK's `BlueiotWristbandBinding`.
//
//  The binding client stores the session in Keystore-backed storage and confirms it
//  with the relay-api at launch (`restore()`). The app stores no wristband id of its
//  own: the binding state is the only record of which band this phone follows. A bind
//  of a band another phone follows is a take-over; the relay-api decides whether it is
//  allowed, and the other phone's session ends with `Superseded`.
//
//  The SDK never asks for location permission. When the relay-api needs the phone's
//  location for a take-over, `locationReadiness()` says so and `WristbandPrompt` asks
//  before the bind.
//
package io.proximi.blueiot.minimal

import android.Manifest
import android.content.Context
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioDiagnosticsEventKind
import io.proximi.sdk.blueiot.binding.BlueiotBindingConfiguration
import io.proximi.sdk.blueiot.binding.BlueiotBindingState
import io.proximi.sdk.blueiot.binding.BlueiotWristbandBinding
import io.proximi.sdk.blueiot.binding.location.BlueiotLocationReadiness
import io.proximi.sdk.recordDiagnosticsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One per process; `BlueiotMinimalApplication.wristband` creates it on first use.
 *
 * @param scope lives as long as the process. It runs `restore()` once and follows the
 *   binding state.
 */
class WristbandSession(
    context: Context,
    scope: CoroutineScope,
    configuration: BlueiotBindingConfiguration? = VenueConfiguration.binding,
) {
    /** What the app asks for before a bind. */
    enum class LocationAsk {
        /** A location permission. Android offers precise or approximate in one dialog. */
        PERMISSION,

        /** Precise location, when the visitor allowed approximate location only. */
        PRECISE_LOCATION,
    }

    /**
     * The binding client, or `null` while `VenueConfiguration.binding` is incomplete.
     * Created once per process.
     */
    val binding: BlueiotWristbandBinding? = configuration?.let { BlueiotWristbandBinding(context, it) }

    private val mutableState = MutableStateFlow(binding?.state ?: BlueiotBindingState.Unbound)
    private val mutableIsFollowing = MutableStateFlow(following(false, mutableState.value))

    /** The latest binding state. */
    val state: StateFlow<BlueiotBindingState> = mutableState.asStateFlow()

    /**
     * `true` while a session is active. A bind in progress does not change it, so the map
     * stays on screen while another band is connected from the sheet.
     */
    val isFollowing: StateFlow<Boolean> = mutableIsFollowing.asStateFlow()

    /**
     * The label of the last successful bind in this process. Fills the prompt's field
     * after a session ends. Not stored.
     */
    var lastLabel: String = ""
        private set

    init {
        binding?.let { binding ->
            scope.launch { binding.restore() }
            scope.launch { binding.stateChanges().collect(::update) }
        }
    }

    /**
     * What to ask for before a bind, or `null` when nothing needs to be asked. Reads the
     * relay-api policy (`GET /v1/meta`, cached by the SDK) and the grants.
     */
    suspend fun locationAsk(): LocationAsk? =
        when (binding?.locationReadiness()) {
            BlueiotLocationReadiness.PERMISSION_NEEDED -> LocationAsk.PERMISSION
            BlueiotLocationReadiness.PRECISE_LOCATION_NEEDED -> LocationAsk.PRECISE_LOCATION
            BlueiotLocationReadiness.READY, BlueiotLocationReadiness.NOT_REQUIRED, null -> null
        }

    /**
     * Binds this phone to the band with [label], as typed. The relay-api resolves every
     * label format. The new session replaces any current one.
     *
     * @throws io.proximi.sdk.blueiot.binding.BlueiotBindingError, or
     *   [VenueConfiguration.SetupIncomplete] without a binding configuration.
     */
    suspend fun bind(label: String) {
        val binding = binding ?: throw VenueConfiguration.SetupIncomplete()
        binding.bind(tagID = label)
        lastLabel = label.trim()
    }

    /**
     * Ends the visit. The state becomes `Ended(UserEnded)`.
     *
     * @throws io.proximi.sdk.blueiot.binding.BlueiotBindingError when the relay-api cannot
     *   be reached; the session is kept.
     */
    suspend fun end() {
        binding?.end()
    }

    private fun update(state: BlueiotBindingState) {
        mutableState.value = state
        mutableIsFollowing.value = following(mutableIsFollowing.value, state)
        Proximiio.recordDiagnosticsEvent(ProximiioDiagnosticsEventKind.state, "wristband: ${WristbandCopy.logLine(state)}")
    }

    companion object {
        /** Whether a session is active after [state], given the value before it. */
        fun following(
            wasFollowing: Boolean,
            state: BlueiotBindingState,
        ): Boolean =
            when (state) {
                is BlueiotBindingState.Active -> true
                BlueiotBindingState.Unbound, is BlueiotBindingState.Ended -> false
                BlueiotBindingState.Binding -> wasFollowing
            }

        /**
         * The runtime permissions requested for either [LocationAsk]. Android shows its
         * precise-location choice only when both are requested together; the visitor can
         * still pick approximate.
         */
        val locationPermissions: Array<String>
            get() = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
