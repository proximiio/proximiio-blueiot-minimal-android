//
//  WristbandIdTests.kt
//  BlueiotMinimalTests
//
//  The label the prompt binds. The relay-api resolves every label format, so the app
//  must send the label as typed: a label rewritten here can name a different band.
//
package io.proximi.blueiot.minimal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WristbandIdTests {
    /** Characters inside the label are kept; only surrounding whitespace is removed. */
    @Test
    fun labelIsSentAsTyped() {
        assertEquals("0x1B59", WristbandId.of("0x1B59")?.label)
        assertEquals("0000000000001B59", WristbandId.of("0000000000001B59")?.label)
        assertEquals("1234567890", WristbandId.of("  1234567890\n")?.label)
    }

    /** A blank field names no wristband, so Connect stays disabled. */
    @Test
    fun blankTextIsNoLabel() {
        assertNull(WristbandId.of(""))
        assertNull(WristbandId.of("   "))
    }
}
