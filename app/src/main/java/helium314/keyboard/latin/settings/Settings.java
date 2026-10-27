/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import helium314.keyboard.compat.ConfigurationCompatKt;
import helium314.keyboard.keyboard.KeyboardActionListener;
import helium314.keyboard.latin.AudioAndHapticFeedbackManager;
import helium314.keyboard.latin.InputAttributes;
import helium314.keyboard.latin.PunctuationSuggestions;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.RichInputMethodManager;
import helium314.keyboard.latin.RichInputMethodSubtype;
import helium314.keyboard.latin.common.StringUtils;
import helium314.keyboard.latin.utils.KtxKt;
import helium314.keyboard.latin.utils.LayoutType;
import helium314.keyboard.latin.utils.Log;
import helium314.keyboard.latin.utils.RunInLocaleKt;
import helium314.keyboard.latin.utils.StatsUtils;
import helium314.keyboard.latin.utils.SubtypeSettings;

import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public final class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();

    // theme-related stuff

    public static final String PREF_LAYOUT_PREFIX = "layout_";

    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_VIBRATE_IN_DND_MODE = "vibrate_in_dnd_mode";
    public static final String PREF_SHOW_EMOJI_DESCRIPTIONS = "show_emoji_descriptions";
    public static final String PREF_AUTO_CORRECT_CONFIDENCE = "auto_correct_confidence";
    public static final String PREF_LANGUAGE_SWITCH_KEY = "language_switch_key";
    public static final String PREF_SHOW_DPAD_KEY = "show_dpad_key";
    public static final String PREF_ADDITIONAL_SUBTYPES = "additional_subtypes";
    public static final String PREF_SPLIT_SPACER_SCALE_PREFIX = "split_spacer_scale";
    public static final String PREF_KEYBOARD_HEIGHT_SCALE_PREFIX = "keyboard_height_scale";
    public static final String PREF_BOTTOM_ROW_SCALE_PREFIX = "bottom_row_scale";
    public static final String PREF_BOTTOM_PADDING_SCALE_PREFIX = "bottom_padding_scale";
    public static final String PREF_SIDE_PADDING_SCALE_PREFIX = "side_padding_scale";
    public static final String PREF_KEY_GAP_SCALE_PREFIX = "key_gap_scale";
    public static final String PREF_SPACE_HORIZONTAL_SWIPE = "horizontal_space_swipe";
    public static final String PREF_SPACE_VERTICAL_SWIPE = "vertical_space_swipe";
    public static final String PREF_DELETE_SWIPE = "delete_swipe";
    public static final String PREF_VIBRATION_DURATION_SETTINGS = "vibration_duration_settings";
    public static final String PREF_KEY_LONGPRESS_TIMEOUT = "key_longpress_timeout";
    public static final String PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY = "enable_emoji_alt_physical_key";
    public static final String PREF_ENABLE_HARDWARE_KEYBOARD = "enable_hardware_keyboard";
    public static final String PREF_SHOW_SETUP_WIZARD_ICON = "show_setup_wizard_icon";
    public static final String PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD = "long_press_symbols_for_numpad";

    public static final String PREF_SHOW_NUMBER_ROW_IN_SYMBOLS = "show_number_row_in_symbols";
    public static final String PREF_LOCALIZED_NUMBER_ROW = "localized_number_row";
    public static final String PREF_SHOW_NUMBER_ROW_HINTS = "show_number_row_hints";
    public static final String PREF_CUSTOM_CURRENCY_KEY = "custom_currency_key";

    public static final String PREF_POPUP_KEYS_ORDER = "popup_keys_order";
    public static final String PREF_POPUP_KEYS_HINT_ORDER = "popup_keys_labels_order";
    public static final String PREF_SHOW_POPUP_HINTS = "show_popup_hints";
    public static final String PREF_MORE_POPUP_KEYS = "more_popup_keys";
    public static final String PREF_SHOW_TLD_POPUP_KEYS = "show_tld_popup_keys";

    public static final String PREF_SPACE_TO_CHANGE_LANG = "prefs_long_press_keyboard_to_change_lang";
    public static final String PREF_LANGUAGE_SWIPE_DISTANCE = "language_swipe_distance";
    public static final String PREF_TOUCHPAD_SENSITIVITY = "touchpad_sensitivity";
    public static final String PREF_TOUCHPAD_EDGE_SCROLL = "touchpad_edge_scroll";

    public static final String PREF_CLIPBOARD_USE_FILES = "clipboard_histor_usey_files";
    public static final String PREF_CLIPBOARD_FILES_SIZE_LIMIT = "clipboard_history_files_size_limit";

    public static final String PREF_ENABLED_SUBTYPES = "enabled_subtypes";
    public static final String PREF_SELECTED_SUBTYPE = "selected_subtype";
    public static final String PREF_URL_DETECTION = "url_detection";
    public static final String PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG = "dont_show_missing_dict_dialog";
    public static final String PREF_ABC_AFTER_EMOJI = "abc_after_emoji";
    public static final String PREF_ABC_AFTER_CLIP = "abc_after_clip";
    public static final String PREF_ABC_AFTER_SYMBOL_SPACE = "abc_after_symbol_space";
    public static final String PREF_ABC_AFTER_NUMPAD_SPACE = "abc_after_numpad_space";
    public static final String PREF_REMOVE_REDUNDANT_POPUPS = "remove_redundant_popups";
    public static final String PREF_TIMESTAMP_FORMAT = "timestamp_format";

    // Emoji
    public static final String PREF_EMOJI_MAX_SDK = "emoji_max_sdk";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_ID = "last_shown_emoji_category_id";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID = "last_shown_emoji_category_page_id";

    public static final String PREF_VERSION_CODE = "version_code";
    public static final String PREF_SAVE_SUBTYPE_PER_APP = "save_subtype_per_app";
    public static final String PREF_SAVED_APP_SUBTYPE_PREFIX = "saved_app_subtype_";

    private Context mContext;
    private SharedPreferences mPrefs;
    private SettingsValues mSettingsValues;
    private final ReentrantLock mSettingsValuesLock = new ReentrantLock();

    // static cache for background images to avoid potentially slow reload on every settings reload

    private static final Settings sInstance = new Settings();

    // preferences that are not used in SettingsValues and thus should not trigger reload when changed
    private static boolean reloadOnChanged(String key) {
        return switch (key) {
            case PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID, PREF_LAST_SHOWN_EMOJI_CATEGORY_ID,
                 PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, PREF_SELECTED_SUBTYPE -> false;
            default -> !key.startsWith(PREF_SAVED_APP_SUBTYPE_PREFIX);
        };
    }

    public static Settings getInstance() {
        return sInstance;
    }

    public static SettingsValues getValues() {
        return sInstance.mSettingsValues;
    }

    public static Context getCurrentContext() {
        return sInstance.mContext;
    }

    public static void init(final Context context) {
        sInstance.onCreate(context);
    }

    private Settings() {
        // Intentional empty constructor for singleton.
    }

    private void onCreate(final Context context) {
        mContext = context;
        mPrefs = KtxKt.prefs(context);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (key != null && !reloadOnChanged(key))
            return;
        mSettingsValuesLock.lock();
        try {
            if (mSettingsValues == null) {
                // TODO: Introduce a static function to register this class and ensure that
                // loadSettings must be called before "onSharedPreferenceChanged" is called.
                Log.w(TAG, "onSharedPreferenceChanged called before loadSettings.");
                return;
            }
            loadSettings(mContext, mSettingsValues.mLocale, mSettingsValues.mInputAttributes);
            StatsUtils.onLoadSettings(mSettingsValues);
        } finally {
            mSettingsValuesLock.unlock();
        }
        if (PREF_ADDITIONAL_SUBTYPES.equals(key)) {
            SubtypeSettings.INSTANCE.reloadEnabledSubtypes(mContext);
        }
    }

    /** convenience function for the rare situations where we need to load settings but may not have a keyboard */
    public void loadSettings(final Context context) {
        if (mSettingsValues != null) return;
        final Locale locale = ConfigurationCompatKt.locale(context.getResources().getConfiguration());
        final InputAttributes inputAttributes = new InputAttributes(new EditorInfo(), false, context.getPackageName());
        loadSettings(context, locale, inputAttributes);
    }

    public void loadSettings(final Context context, final Locale locale,
                             @NonNull final InputAttributes inputAttributes) {
        mSettingsValuesLock.lock();
        mContext = context;
        try {
            final SharedPreferences prefs = mPrefs;
            Log.i(TAG, "loadSettings");
            mSettingsValues = RunInLocaleKt.runInLocale(context, locale,
                    ctx -> new SettingsValues(ctx, prefs, ctx.getResources(), inputAttributes));
        } finally {
            mSettingsValuesLock.unlock();
        }
    }

    public void startListener() {
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    // TODO: Remove this method and add proxy method to SettingsValues.
    public SettingsValues getCurrent() {
        return mSettingsValues;
    }

    public static int readScreenMetrics(final Resources res) {
        return res.getInteger(R.integer.config_screen_metrics);
    }

    public static boolean readVibrationEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON)
                && AudioAndHapticFeedbackManager.getInstance().hasVibrator();
    }

    public static KeyboardActionListener.SwipeAction readHorizontalSpaceSwipe(SharedPreferences prefs) {
        try {
            String value = prefs.getString(PREF_SPACE_HORIZONTAL_SWIPE, Defaults.PREF_SPACE_HORIZONTAL_SWIPE);
            return KeyboardActionListener.SwipeAction.valueOf(value);
        } catch (IllegalArgumentException e) {
            return KeyboardActionListener.SwipeAction.NONE;
        }
    }

    public static KeyboardActionListener.SwipeAction readVerticalSpaceSwipe(SharedPreferences prefs) {
        try {
            String value = prefs.getString(PREF_SPACE_VERTICAL_SWIPE, Defaults.PREF_SPACE_VERTICAL_SWIPE);
            return KeyboardActionListener.SwipeAction.valueOf(value);
        } catch (IllegalArgumentException e) {
            return KeyboardActionListener.SwipeAction.NONE;
        }
    }

    public static boolean readFullscreenModeAllowed(final Resources res) {
        return res.getBoolean(R.bool.config_fullscreen_mode_allowed);
    }

    public static boolean readShowSetupWizardIcon(final SharedPreferences prefs,
                                                  final Context context) {
        if (!prefs.contains(PREF_SHOW_SETUP_WIZARD_ICON)) {
            final ApplicationInfo appInfo = context.getApplicationInfo();
            final boolean isApplicationInSystemImage =
                    (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            // Default value
            return !isApplicationInSystemImage;
        }
        return prefs.getBoolean(PREF_SHOW_SETUP_WIZARD_ICON, Defaults.PREF_SHOW_SETUP_WIZARD_ICON);
    }

    public static boolean readHasHardwareKeyboard(final Configuration conf) {
        // The standard way of finding out whether we have a hardware keyboard. This code is taken
        // from InputMethodService#onEvaluateInputShown, which canonically determines this.
        // In a nutshell, we have a keyboard if the configuration says the type of hardware keyboard
        // is NOKEYS and if it's not hidden (e.g. folded inside the device).
        return conf.keyboard != Configuration.KEYBOARD_NOKEYS
                && conf.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    public boolean isTablet() {
        return mContext.getResources().getInteger(R.integer.config_screen_metrics) >= 3;
    }

    @SuppressLint("DiscouragedApi")
    public int getStringResIdByName(final String name) {
        return mContext.getResources().getIdentifier(name, "string", mContext.getPackageName());
    }

    public String getInLocale(@StringRes final int resId, final Locale locale) {
        return RunInLocaleKt.runInLocale(mContext, locale, (ctx) -> ctx.getString(resId));
    }

    public String readCustomCurrencyKey() {
        return mPrefs.getString(PREF_CUSTOM_CURRENCY_KEY, Defaults.PREF_CUSTOM_CURRENCY_KEY);
    }

    // "default" layout as in this is used if nothing else is specified in the subtype
    public static String readDefaultLayoutName(final LayoutType type, final SharedPreferences prefs) {
        return prefs.getString(PREF_LAYOUT_PREFIX + type.name(), Defaults.INSTANCE.getDefault(type));
    }

    public static void writeDefaultLayoutName(@Nullable final String name, final LayoutType type, final SharedPreferences prefs) {
        if (name == null) prefs.edit().remove(PREF_LAYOUT_PREFIX + type.name()).apply();
        else prefs.edit().putString(PREF_LAYOUT_PREFIX + type.name(), name).apply();
    }

    public void saveSubtypeForApp(RichInputMethodSubtype subtype, String packageName) {
        if (isSubtypePerApp() && ! StringUtils.isEmpty(packageName)) {
            mPrefs.edit().putString(PREF_SAVED_APP_SUBTYPE_PREFIX + packageName,
                                    SettingsSubtype.Companion.toSettingsSubtype(subtype.getRawSubtype()).toPref()).apply();
        }
    }

    public RichInputMethodSubtype getSubtypeForApp(String packageName) {
        if (! isSubtypePerApp() || StringUtils.isEmpty(packageName)) return null;
        var subtypePref = mPrefs.getString(PREF_SAVED_APP_SUBTYPE_PREFIX + packageName, null);
        if (subtypePref == null) return null;
        var settingsSubtype = SettingsSubtype.Companion.toSettingsSubtype(subtypePref);
        var subtype = settingsSubtype.toEnabledSubtype();
        if (subtype == null) subtype = RichInputMethodManager.getInstance().findSubtypeForHintLocale(settingsSubtype.getLocale());
        return subtype != null? RichInputMethodSubtype.Companion.get(subtype) : null;
    }

    private boolean isSubtypePerApp() {
        return mPrefs.getBoolean(PREF_SAVE_SUBTYPE_PER_APP, Defaults.PREF_SAVE_SUBTYPE_PER_APP);
    }

    // M2Y Board: fixed punctuation suggestions shown on an empty strip (the value chosen in the removed setting)
    public static PunctuationSuggestions readPunctuationSuggestions(Context context) {
        return PunctuationSuggestions.newPunctuationSuggestions(new String[] { "-", "@", "_" });
    }
}
