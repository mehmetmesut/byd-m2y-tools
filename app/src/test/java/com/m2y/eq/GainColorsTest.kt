package com.m2y.eq

import org.junit.Assert.assertEquals
import org.junit.Test

class GainColorsTest {
    @Test fun stopsHitTheirColours() {
        assertEquals(0xFF30D158.toInt(), GainColors.at(0f))
        assertEquals(0xFFFF9500.toInt(), GainColors.at(6f))
        assertEquals(0xFFFF3B30.toInt(), GainColors.at(12f))
        assertEquals(0xFF5AC8FA.toInt(), GainColors.at(-4f))
        assertEquals(0xFF8E8E93.toInt(), GainColors.at(-8f))
        assertEquals(0xFFAF52DE.toInt(), GainColors.at(-12f))
    }

    @Test fun betweenStopsIsABlendAndOutOfRangeClamps() {
        // halfway green (0x30,0xD1,0x58) → orange (0xFF,0x95,0x00) at +3 dB
        assertEquals(0xFF98B32C.toInt(), GainColors.at(3f))
        assertEquals(GainColors.at(12f), GainColors.at(40f))
        assertEquals(GainColors.at(-12f), GainColors.at(-40f))
    }
}
