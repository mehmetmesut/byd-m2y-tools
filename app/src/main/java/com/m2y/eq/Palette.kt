package com.m2y.eq

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/** M2Y design tokens (shared with the other M2Y car apps), light and night. */
class Palette(night: Boolean) {
    val page = if (night) Color.parseColor("#07080A") else Color.parseColor("#E3E5E9")
    val pageTop = if (night) Color.parseColor("#0E1014") else Color.parseColor("#EEF1F5")
    val card = if (night) Color.parseColor("#101216") else Color.parseColor("#F2F2F0")
    val text = if (night) Color.parseColor("#D0D4D9") else Color.parseColor("#13161A")
    val secondary = if (night) Color.parseColor("#969CA4") else Color.parseColor("#5C636E")
    val accent = if (night) Color.parseColor("#0A84FF") else Color.parseColor("#007AFF")
    val track = if (night) Color.parseColor("#262A31") else Color.parseColor("#D5D8DD")
    val zeroLine = if (night) Color.parseColor("#3A3F47") else Color.parseColor("#C2C6CC")

    fun pageBackground() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pageTop, page))

    fun rounded(color: Int, radiusPx: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radiusPx }

    companion object {
        const val CARD_RADIUS_DP = 18
        const val CHIP_RADIUS_DP = 14
        const val GAP_SMALL_DP = 8
        const val GAP_DP = 14
        const val GAP_LARGE_DP = 20
        const val EDGE_DP = 24

        fun isNight(config: Configuration) =
            config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
