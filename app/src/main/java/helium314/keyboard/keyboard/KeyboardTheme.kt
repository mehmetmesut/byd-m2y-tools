/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.Color
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors
import helium314.keyboard.latin.utils.ResourceUtils
import androidx.core.graphics.toColorInt

class KeyboardTheme // Note: The themeId should be aligned with "themeId" attribute of Keyboard style in values/themes-<style>.xml.
private constructor(val themeId: Int, @JvmField val mStyleId: Int) {
    override fun equals(other: Any?) = if (other === this) true
        else (other as? KeyboardTheme)?.themeId == themeId

    override fun hashCode(): Int {
        return themeId
    }

    companion object {
        // old themes, now called styles
        const val STYLE_MATERIAL = "Material"

        // M2Y Board: the only colours, iPhone light by day and iPhone dark by night (system night mode)

        // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
        // attributes' values in attrs.xml.
        private const val THEME_ID_LXX_BASE = 1
        private const val THEME_ID_LXX_BASE_BORDER = 2

        // M2Y Board: the only style, Material with key borders
        private val KEYBOARD_THEME = KeyboardTheme(THEME_ID_LXX_BASE_BORDER, R.style.KeyboardTheme_LXX_Base_Border)

        @JvmStatic
        fun getKeyboardTheme(context: Context): KeyboardTheme {
            return KEYBOARD_THEME
        }

        fun getThemeActionAndEmojiKeyLabelFlags(themeId: Int): Int {
            return if (themeId == THEME_ID_LXX_BASE) Key.LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO else 0
        }

        @JvmStatic
        fun getColorsForCurrentTheme(context: Context): Colors {
            val themeStyle = STYLE_MATERIAL
            return if (ResourceUtils.isNight(context.resources)) DefaultColors( // measured from the user's iPhone dark keyboard image (26.10.2026)
                themeStyle,
                true,
                "#6F7173".toColorInt(), // Enter key: lighter grey, not blue
                "#1E1E20".toColorInt(),
                "#3B3D3F".toColorInt(),
                "#3B3D3F".toColorInt(), // function keys share the letter key colour
                "#3B3D3F".toColorInt(),
                Color.WHITE,
                "#AEAEB2".toColorInt(),
                spaceBarText = Color.WHITE,
            ) else DefaultColors(
                themeStyle,
                true,
                "#007AFF".toColorInt(),
                "#D1D3D9".toColorInt(),
                Color.WHITE,
                "#ABB0BA".toColorInt(),
                Color.WHITE,
                Color.BLACK,
                "#8E8E93".toColorInt(),
                spaceBarText = Color.BLACK,
            )
        }
    }
}
