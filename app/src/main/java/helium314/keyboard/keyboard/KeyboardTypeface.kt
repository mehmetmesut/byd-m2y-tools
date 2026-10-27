// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard

import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.ui.text.font.FontFamily

/** M2Y Board: system font only; custom keyboard and emoji font files are not supported. */
object KeyboardTypeface {
    @JvmStatic
    fun customTypeface(): Typeface? = null

    @JvmStatic
    fun emojiTypeface(): Typeface? = null

    @JvmStatic
    fun customFontFamily(): FontFamily? = null

    @JvmStatic
    fun resolve(
        @Suppress("UNUSED_PARAMETER") text: CharSequence?,
        defaultTypeface: Typeface = Typeface.DEFAULT,
    ): Typeface = defaultTypeface

    @JvmStatic
    fun applyToTextView(textView: TextView) {
        textView.typeface = Typeface.DEFAULT
    }

    @JvmStatic
    fun applyToTextView(textView: TextView, @Suppress("UNUSED_PARAMETER") text: CharSequence?, defaultTypeface: Typeface) {
        textView.typeface = defaultTypeface
    }
}
