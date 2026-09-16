//
//  WristbandId.kt
//  BlueiotMinimal
//
//  The spelling rule for the number printed on a wristband, and where that number is
//  stored. The rule is the SDK's `BlueiotCloudRelayMessage.decimalTagId`, so this app
//  cannot read an id differently from the relay client that matches it.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayMessage

/** A BlueIoT wristband (tag) id. */
class WristbandId private constructor(
    /**
     * The id as a number. `ULong` matches the protocol width: the cloud relay carries
     * `tagId` as a decimal `u64`.
     */
    val value: ULong,
) {
    /**
     * The canonical spelling: decimal, the characters printed on the band. This is what
     * is stored, what is shown back, and what `BlueiotCloudRelayConfiguration.tagId` is
     * set to.
     */
    val canonical: String get() = value.toString()

    /** The same tag in hexadecimal, for the echo under the text field. */
    val hexadecimal: String get() = "0x" + value.toString(HEX_RADIX).uppercase()

    override fun equals(other: Any?): Boolean = other is WristbandId && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = canonical

    companion object {
        private const val HEX_RADIX = 16

        /**
         * Parses typed or pasted text into the one tag id it names.
         *
         * The rule is [BlueiotCloudRelayMessage.decimalTagId], the same function the
         * relay client matches incoming ids with. It is called rather than
         * re-implemented.
         *
         * A bare number is decimal: `1234567890` is one thousand million and change, not
         * a hexadecimal string with no letters in it. A `0x` prefix is hexadecimal, text
         * containing letters is hexadecimal, and the codec's 16-digit rendering
         * (`0000000000001B59`) is hexadecimal.
         *
         * Returns `null` only when the text names no number. The SDK rule refuses
         * nothing and returns text it could not read unchanged, so a mistyped id matches
         * no tag rather than the wrong one; this is where that becomes `null`.
         */
        fun of(text: String): WristbandId? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val value = BlueiotCloudRelayMessage.decimalTagId(trimmed).toULongOrNull() ?: return null
            return WristbandId(value)
        }

        /** Whether [text] names a wristband id, for a text field's inline validation. */
        fun isValid(text: String): Boolean = of(text) != null
    }
}

/**
 * Stores the wristband id between launches.
 *
 * `SharedPreferences`, not `EncryptedSharedPreferences`: a tag id is not a secret, it is
 * printed on the band.
 */
object WristbandStore {
    private const val KEY = "WristbandID"
    private const val FILE = "BlueiotMinimal"

    /** The app's one preferences file. The visit is stored in it too. */
    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * The stored id, re-parsed through [WristbandId.of] rather than trusted as
     * characters, so a spelling written by an earlier build still names the same tag
     * under the current rule.
     */
    fun load(store: SharedPreferences): WristbandId? = store.getString(KEY, null)?.let(WristbandId::of)

    fun save(id: WristbandId, store: SharedPreferences) {
        store.edit { putString(KEY, id.canonical) }
    }
}
