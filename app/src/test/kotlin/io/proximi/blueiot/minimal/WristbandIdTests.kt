//
//  WristbandIdTests.kt
//  BlueiotMinimalTests
//
//  The wristband spelling rule and its persistence. Both fail silently: an id read one
//  way here and another way by the relay matches no tag, and the symptom is that no
//  position arrives. The screens have no logic and are not tested.
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

    /** A bare number is decimal, as printed on the band. */
    @Test
    fun bareNumberIsDecimal() {
        assertEquals(1_234_567_890UL, WristbandId.of("1234567890")?.value)
        // Not hexadecimal: 0x1234567890 would be 78_187_493_520.
        assertEquals(5555UL, WristbandId.of("5555")?.value)
    }

    /** The three spellings of one tag all resolve to it. */
    @Test
    fun everySpellingOfOneTagAgrees() {
        val decimal = WristbandId.of("7001")
        assertEquals(decimal, WristbandId.of("0x1B59"))
        assertEquals(decimal, WristbandId.of("0000000000001B59"))
        assertEquals(7001UL, decimal?.value)
    }

    /** Text containing letters is hexadecimal, in either case. */
    @Test
    fun lettersAreHex() {
        assertEquals(1_234_567_890UL, WristbandId.of("499602D2")?.value)
        assertEquals(1_234_567_890UL, WristbandId.of("499602d2")?.value)
        assertEquals(1_234_567_890UL, WristbandId.of("0X499602D2")?.value)
    }

    /** Surrounding whitespace, as a pasted id carries, is ignored. */
    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(1_234_567_890UL, WristbandId.of("  1234567890\n")?.value)
    }

    /** The canonical spelling is decimal, whatever spelling came in. */
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
 * The store is `SharedPreferences`, the one Android API in the app's logic, so this
 * class runs under Robolectric and the spelling tests above do not.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35, not 36: Robolectric's API 36 image requires Java 21 and this project builds on
// 17. These tests exercise `SharedPreferences` only.
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
     * Loading re-parses the stored text through the rule, so a spelling an earlier build
     * wrote still names the same tag.
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
