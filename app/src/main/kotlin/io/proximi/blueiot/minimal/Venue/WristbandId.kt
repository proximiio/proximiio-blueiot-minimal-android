//
//  WristbandId.kt
//  BlueiotMinimal
//
//  One spelling rule for the number printed on the band, and the one place it is
//  stored. Both are tiny, and both are tested, because a wristband id that is read
//  one way here and another way by the relay does not fail loudly — it just matches
//  nothing, and the dot never arrives.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayMessage

/** A Blueiot wristband (tag) id, once it is known to be one. */
class WristbandId private constructor(
    /**
     * The id as a number. `ULong` because that is the width of the protocol: the
     * cloud relay carries `tagId` as a decimal `u64`.
     */
    val value: ULong,
) {
    /**
     * The canonical spelling: **decimal**, the same characters printed on the band.
     * This is what is stored, what is shown back, and what the relay is configured
     * with.
     */
    val canonical: String get() = value.toString()

    /** The same tag on the wire, for the echo under the text field. */
    val hexadecimal: String get() = "0x" + value.toString(HEX_RADIX).uppercase()

    override fun equals(other: Any?): Boolean = other is WristbandId && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = canonical

    companion object {
        private const val HEX_RADIX = 16

        /**
         * Reads whatever a person typed — or pasted out of a vendor screen — into the
         * one tag it names.
         *
         * The rule is the SDK's own, [BlueiotCloudRelayMessage.decimalTagId], which is
         * the same function the relay client matches incoming ids with. Called rather
         * than re-implemented, so this app cannot read an id differently from the relay
         * that serves it.
         *
         * **A bare number is decimal.** That is what is printed on the band and what
         * Blueiot's own tooling shows: `1234567890` is one thousand million and change,
         * not a hex string that happens to have no letters in it. An explicit `0x` says
         * hex, an id with letters in it is hex because it cannot be anything else, and
         * the codec's 16-digit rendering (`0000000000001B59`) is hex too.
         *
         * `null` only when the text names no number at all. The SDK's rule refuses
         * nothing — it hands back text it could not read, so a mistyped id fails to
         * match rather than matching the wrong tag — and this is where that becomes a
         * "no".
         */
        fun of(text: String): WristbandId? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val value = BlueiotCloudRelayMessage.decimalTagId(trimmed).toULongOrNull() ?: return null
            return WristbandId(value)
        }

        /** Whether [text] names a wristband at all — for a text field's inline validation. */
        fun isValid(text: String): Boolean = of(text) != null
    }
}

/**
 * Where the wristband id lives between launches.
 *
 * `SharedPreferences`, not `EncryptedSharedPreferences`: a tag id is not a secret, it is
 * printed in large type on the band in the visitor's hand.
 */
object WristbandStore {
    private const val KEY = "WristbandID"
    private const val FILE = "BlueiotMinimal"

    /** The one preferences file this app keeps; the visit lives in it too. */
    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * The stored id, re-read through [WristbandId.of] rather than trusted as
     * characters. That round trip is what lets the stored spelling change without
     * moving anybody's band: any spelling an earlier build wrote still names the same
     * tag under today's rule.
     */
    fun load(store: SharedPreferences): WristbandId? = store.getString(KEY, null)?.let(WristbandId::of)

    fun save(id: WristbandId, store: SharedPreferences) {
        store.edit { putString(KEY, id.canonical) }
    }
}
