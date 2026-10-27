package helium314.keyboard.latin.settings

import android.os.Build
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.POPUP_KEYS_LABEL_DEFAULT
import helium314.keyboard.latin.utils.POPUP_KEYS_ORDER_DEFAULT

object Defaults {
    // must correspond to a file name
    val LayoutType.default get() = when (this) {
        LayoutType.MAIN -> "qwerty"
        LayoutType.SYMBOLS -> "symbols"
        LayoutType.MORE_SYMBOLS -> "symbols_shifted"
        LayoutType.FUNCTIONAL -> if (Settings.getInstance().isTablet) "functional_keys_tablet" else "functional_keys"
        LayoutType.NUMBER -> "number"
        LayoutType.NUMBER_ROW -> "number_row"
        LayoutType.NUMPAD -> "numpad"
        LayoutType.NUMPAD_LANDSCAPE -> "numpad_landscape"
        LayoutType.DPAD -> "dpad"
        LayoutType.PHONE -> "phone"
        LayoutType.PHONE_SYMBOLS -> "phone_symbols"
        LayoutType.EMOJI_BOTTOM -> "emoji_bottom_row"
        LayoutType.CLIPBOARD_BOTTOM -> "clip_bottom_row"
    }

    private const val DEFAULT_SIZE_SCALE = 1.0f // 100%
    const val PREF_VIBRATE_ON = false
    const val PREF_VIBRATE_IN_DND_MODE = false
    const val PREF_SHOW_EMOJI_DESCRIPTIONS = false
    const val PREF_AUTO_CORRECT_CONFIDENCE = 0.24f
    const val PREF_AUTOCORRECT_SHORTCUTS = true
    const val PREF_BACKSPACE_REVERTS_AUTOCORRECT = true
    const val PREF_AUTOCORRECT_CAPITALIZED_SUGGESTION = true
    const val PREF_LANGUAGE_SWITCH_KEY = "internal"
    const val PREF_SHOW_DPAD_KEY = true
    // M2Y TR-Q: only Turkish and Russian, no extra layout variants
    const val PREF_ADDITIONAL_SUBTYPES = ""
    @JvmField
    // M2Y Board: 82% was tuned on the BYD center screen (1920x1080, 240 dpi)
    val PREF_KEYBOARD_HEIGHT_SCALE = Array(4) { 0.82f }
    @JvmField
    val PREF_BOTTOM_ROW_SCALE = Array(4) { DEFAULT_SIZE_SCALE }
    @JvmField
    // DEFAULT_SIZE_SCALE for portrait, 0 for landscape (normal and folded)
    val PREF_BOTTOM_PADDING_SCALE = arrayOf(DEFAULT_SIZE_SCALE, 0f, DEFAULT_SIZE_SCALE, 0f)
    @JvmField
    val PREF_SIDE_PADDING_SCALE = Array(8) { 0f }
    @JvmField
    val PREF_KEY_GAP_SCALE = Array(4) { DEFAULT_SIZE_SCALE }
    const val PREF_FONT_SCALE = DEFAULT_SIZE_SCALE
    const val PREF_HINT_FONT_SCALE = DEFAULT_SIZE_SCALE
    const val PREF_EMOJI_FONT_SCALE = DEFAULT_SIZE_SCALE
    const val PREF_EMOJI_KEY_FIT = true
    @JvmField
    val PREF_SPACE_HORIZONTAL_SWIPE = KeyboardActionListener.SwipeAction.MOVE_CURSOR.name
    @JvmField
    val PREF_SPACE_VERTICAL_SWIPE = KeyboardActionListener.SwipeAction.NONE.name
    const val PREF_DELETE_SWIPE = true
    const val PREF_VIBRATION_DURATION_SETTINGS = -1
    const val PREF_KEY_LONGPRESS_TIMEOUT = 300
    const val PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY = true
    // supporting hardware keyboard still has a bunch of issues
    // crash https://github.com/HeliBorg/HeliBoard/issues/2047 (possibly fixed with b7cb95fc9da213c99d82e8833fb5f950f39d232e)
    // different crash https://github.com/HeliBorg/HeliBoard/issues/2001
    //  LatinIME.isInputViewShown() returns true when there is no input view, thus crashing in onUpdateSelection
    // physical layout ignored https://github.com/HeliBorg/HeliBoard/issues/1957, https://github.com/HeliBorg/HeliBoard/issues/1949
    // physical layout ignored for uppercase letters only (?) https://github.com/HeliBorg/HeliBoard/issues/2030
    const val PREF_ENABLE_HARDWARE_KEYBOARD = false
    const val PREF_SHOW_SETUP_WIZARD_ICON = true
    const val PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD = false
    const val PREF_SHOW_NUMBER_ROW_IN_SYMBOLS = true
    const val PREF_LOCALIZED_NUMBER_ROW = true
    const val PREF_SHOW_NUMBER_ROW_HINTS = false
    const val PREF_CUSTOM_CURRENCY_KEY = ""
    const val PREF_POPUP_KEYS_ORDER = POPUP_KEYS_ORDER_DEFAULT
    const val PREF_POPUP_KEYS_HINT_ORDER = POPUP_KEYS_LABEL_DEFAULT
    const val PREF_SHOW_POPUP_HINTS = false
    const val PREF_SHOW_TLD_POPUP_KEYS = true
    const val PREF_MORE_POPUP_KEYS = "main"
    const val PREF_SPACE_TO_CHANGE_LANG = true
    const val PREF_LANGUAGE_SWIPE_DISTANCE = 5
    const val PREF_TOUCHPAD_SENSITIVITY = 50
    const val PREF_TOUCHPAD_EDGE_SCROLL = true
    const val PREF_CLIPBOARD_USE_FILES = false // M2Y: no image/file copies written to disk
    const val PREF_CLIPBOARD_FILES_SIZE_LIMIT = 20 // megabytes
    @JvmField
    val PREF_NAVBAR_COLOR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    // M2Y: Turkish Q is enabled and selected on a fresh install, independent of the system language
    const val PREF_ENABLED_SUBTYPES = "tr" + Separators.SET + "KeyboardLayoutSet=MAIN:turkish"
    const val PREF_SELECTED_SUBTYPE = PREF_ENABLED_SUBTYPES
    const val PREF_URL_DETECTION = false
    const val PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG = true // M2Y: no pop-up on the car screen
    const val PREF_ABC_AFTER_EMOJI = false
    const val PREF_ABC_AFTER_CLIP = false
    const val PREF_ABC_AFTER_SYMBOL_SPACE = true
    const val PREF_ABC_AFTER_NUMPAD_SPACE = false
    const val PREF_REMOVE_REDUNDANT_POPUPS = false
    const val PREF_SPACE_BAR_TEXT = ""
    const val PREF_TIMESTAMP_FORMAT = "dd-MM-yyyy HH:mm:ss" // M2Y: Turkish day-first order
    const val PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID = 0
    const val PREF_SAVE_SUBTYPE_PER_APP = false
}
