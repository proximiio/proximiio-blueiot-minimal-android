//
//  WristbandId.kt
//  BlueiotMinimal
//
//  The wristband label the visitor typed, and the app's preferences file. The app reads
//  no number out of the label: the relay-api resolves every label format, so the label
//  is sent as typed. The SDK's binding client stores the session; the app stores no
//  wristband id of its own.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences

/** A wristband label, as typed, without surrounding whitespace. */
@JvmInline
value class WristbandId(
    val label: String,
) {
    companion object {
        /**
         * The label in [text], or `null` when [text] is blank. Characters inside the label
         * are kept: `0x1B59` stays `0x1B59`.
         */
        fun of(text: String): WristbandId? = text.trim().ifEmpty { null }?.let(::WristbandId)
    }
}

/**
 * The app's one preferences file. It holds the visit (`JourneyStore`) and the location
 * prompt's flag (`LocationPrompt`). The wristband session is not in it: the SDK keeps
 * that in Keystore-backed storage.
 *
 * `SharedPreferences`, not `EncryptedSharedPreferences`: nothing in it is a secret.
 */
object WristbandStore {
    private const val FILE = "BlueiotMinimal"

    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
