package com.m2y.eq

import kotlin.math.roundToInt

/**
 * Colour of a gain value as a continuous gradient: green at 0 dB; boosts go green → orange (+6) → red (+12);
 * cuts go green → light blue (−4) → grey (−8) → purple (−12). Pure ARGB math, so it is unit-tested.
 */
object GainColors {
    private const val GREEN = 0xFF30D158.toInt()
    private const val ORANGE = 0xFFFF9500.toInt()
    private const val RED = 0xFFFF3B30.toInt()
    private const val LIGHT_BLUE = 0xFF5AC8FA.toInt()
    private const val GREY = 0xFF8E8E93.toInt()
    private const val PURPLE = 0xFFAF52DE.toInt()

    private val STOPS = listOf(
        -12f to PURPLE, -8f to GREY, -4f to LIGHT_BLUE, 0f to GREEN, 6f to ORANGE, 12f to RED,
    )

    fun at(db: Float): Int {
        val v = db.coerceIn(STOPS.first().first, STOPS.last().first)
        val i = STOPS.indexOfLast { it.first <= v }.coerceAtMost(STOPS.lastIndex - 1)
        val (d0, c0) = STOPS[i]
        val (d1, c1) = STOPS[i + 1]
        return blend(c0, c1, (v - d0) / (d1 - d0))
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t).roundToInt() and 0xFF
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
