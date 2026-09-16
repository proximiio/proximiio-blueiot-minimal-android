//
//  WristbandIdTests.kt
//  BlueiotMinimalTests
//
//  The spelling rule and its persistence, and nothing else. These two are worth
//  testing because they fail silently: a wristband read one way here and another way
//  by the relay matches nothing, and the symptom is "the dot never arrives" rather
//  than an error. The screens are not tested — they have no logic to get wrong.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

class WristbandIdTests {
    // MARK: - Spelling

    /** The number printed on a real band, as it reads. */
    @Test
    fun bareNumberIsDecimal() {
        assertEquals(1_234_567_890UL, WristbandId.of("1234567890")?.value)
        // And specifically NOT hex: 0x1234567890 would be 78_187_493_520.
        assertEquals(5555UL, WristbandId.of("5555")?.value)
    }

    /** The three spellings of one tag all name it. */
    @Test
    fun everySpellingOfOneTagAgrees() {
        val decimal = WristbandId.of("7001")
        assertEquals(decimal, WristbandId.of("0x1B59"))
        assertEquals(decimal, WristbandId.of("0000000000001B59"))
        assertEquals(7001UL, decimal?.value)
    }

    /** Letters can only be hex, whatever case they arrive in. */
    @Test
    fun lettersAreHex() {
        assertEquals(1_234_567_890UL, WristbandId.of("499602D2")?.value)
        assertEquals(1_234_567_890UL, WristbandId.of("499602d2")?.value)
        assertEquals(1_234_567_890UL, WristbandId.of("0X499602D2")?.value)
    }

    /** Pasted ids carry whitespace; that is not a typo. */
    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(1_234_567_890UL, WristbandId.of("  1234567890\n")?.value)
    }

    /** Whatever spelling came in, one goes out — decimal, the band's own. */
    @Test
    fun canonicalSpellingIsDecimal() {
        assertEquals("7001", WristbandId.of("0x1B59")?.canonical)
        assertEquals("0x1B59", WristbandId.of("0x1B59")?.hexadecimal)
    }

    @Test
    fun textThatNamesNoTagIsRefused() {
        assertNull(WristbandId.of(""))
        assertNull(WristbandId.of("   "))
        assertNull(WristbandId.of("-5"))
        assertNull(WristbandId.of("band 7001"))
        assertNull(WristbandId.of("0x"))
        assertFalse(WristbandId.isValid("not a band"))
        assertTrue(WristbandId.isValid("7001"))
    }
}

/**
 * The store is `SharedPreferences`, which is the one Android API in the app's logic —
 * so this half runs under Robolectric and the spelling half above does not.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35, not the newest: Robolectric's API 36 image needs Java 21 and this project
// builds on 17. `SharedPreferences` is the whole of what these exercise, and it has
// not moved.
@Config(sdk = [35])
class WristbandStoreTests {
    @Test
    fun savedIdSurvivesAndComesBackCanonical() {
        val store = freshPreferences()
        val typed = requireNotNull(WristbandId.of("0x1B59"))

        WristbandStore.save(typed, store)

        assertEquals("the store holds the band's own spelling", "7001", store.getString("WristbandID", null))
        assertEquals(typed, WristbandStore.load(store))
    }

    /**
     * A value an earlier build wrote in another spelling still names the same tag,
     * because loading re-reads it through the rule instead of trusting characters.
     */
    @Test
    fun aStoredForeignSpellingStillNamesTheSameTag() {
        val store = freshPreferences()
        store.edit().putString("WristbandID", "0x1B59").apply()

        assertEquals(7001UL, WristbandStore.load(store)?.value)
    }

    @Test
    fun nothingStoredMeansNoWristband() {
        assertNull(WristbandStore.load(freshPreferences()))
    }

    /** A file of its own, so a test never reads or writes the app's real store. */
    private fun freshPreferences(): SharedPreferences =
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("WristbandIdTests.${UUID.randomUUID()}", Context.MODE_PRIVATE)
}
