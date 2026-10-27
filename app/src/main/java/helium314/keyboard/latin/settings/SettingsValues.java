/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;

import helium314.keyboard.compat.ConfigurationCompatKt;
import helium314.keyboard.compat.IsLockedCompatKt;
import helium314.keyboard.keyboard.KeyboardActionListener;
import helium314.keyboard.keyboard.KeyboardTheme;
import helium314.keyboard.keyboard.internal.KeyboardState;
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos;
import helium314.keyboard.latin.InputAttributes;
import helium314.keyboard.latin.PunctuationSuggestions;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.utils.FoldableUtils;
import helium314.keyboard.latin.utils.ScriptUtils;
import helium314.keyboard.latin.utils.SubtypeSettings;
import helium314.keyboard.latin.utils.SubtypeUtilsKt;
import helium314.keyboard.latin.utils.ToolbarMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using {@link helium314.keyboard.latin.utils.RunInLocaleKt}.
 */
// Non-final for testing via mock library.
public class SettingsValues {
    // From resources:
    public final SpacingAndPunctuations mSpacingAndPunctuations;
    public final long mDoubleSpacePeriodTimeout;
    // From configuration:
    public final Locale mLocale;
    public final boolean mHasHardwareKeyboard;
    public final int mDisplayOrientation;
    // From preferences
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mVibrateInDndMode;
    public final boolean mSuggestEmojis;
    public final boolean mInlineEmojiSearch;
    public final boolean mShowEmojiDescriptions;
    public final boolean mKeyPreviewPopupOn;
    public final boolean mShowsVoiceInputKey;
    public final boolean mLanguageSwitchKeyToOtherImes;
    public final boolean mLanguageSwitchKeyToOtherSubtypes;
    public final boolean mShowsNumberRow;
    public final boolean mShowsNumberRowInSymbols;
    public final boolean mLocalizedNumberRow;
    public final boolean mShowNumberRowHints;
    public final boolean mShowsHints;
    public final boolean mShowsPopupHints;
    public final boolean mShowTldPopupKeys;
    public final boolean mSpaceForLangChange;
    public final boolean mShowsDpadKey;
    public final boolean mVarToolbarDirection;
    public final boolean mUseDoubleSpacePeriod;
    public final boolean mBlockPotentiallyOffensive;
    public final KeyboardActionListener.SwipeAction mSpaceSwipeHorizontal;
    public final KeyboardActionListener.SwipeAction mSpaceSwipeVertical;
    public final int mLanguageSwipeDistance;
    public final int mTouchpadSensitivity;
    public final boolean mTouchpadEdgeScroll;
    public final boolean mDeleteSwipeEnabled;
    public final boolean mAutospaceAfterPunctuation;
    public final boolean mAutospaceAfterSuggestion;
    public final boolean mClipboardHistoryEnabled;
    public final long mClipboardHistoryRetentionTime;
    public final boolean mClipboardHistoryPinnedFirst;
    public final float mKeyGapScale;
    public final String mShowMorePopupKeys;
    public final List<String> mPopupKeyOrder;
    public final List<String> mPopupKeyHintOrder;
    public final List<Locale> mSecondaryLocales;
    public final boolean mBigramPredictionEnabled;// Use bigrams to predict the next word when there is no input for it yet
    public final boolean mSuggestPunctuation;
    public final boolean mCenterSuggestionTextToEnter;
    public final boolean mSlidingKeyInputPreviewEnabled;
    public final int mKeyLongpressTimeout;
    public final boolean mEnableEmojiAltPhysicalKey;
    public final boolean mEnableHardwareKeyboard;
    public final boolean mCustomNavBarColor;
    public final float mKeyboardHeightScale;
    public final float mBottomRowScale;
    public final boolean mUrlDetectionEnabled;
    public final float mBottomPaddingScale;
    public final float mSidePaddingScale;
    public final ToolbarMode mToolbarMode;
    public final boolean mToolbarSwipeDownToHide;
    public final boolean mToolbarHidingGlobal;
    public final boolean mAlphaAfterEmojiInEmojiView;
    public final boolean mAlphaAfterClipHistoryEntry;
    public final EnumSet<KeyboardState.Mode> mAlphaAfterSpace = EnumSet.noneOf(KeyboardState.Mode.class);
    public final boolean mRemoveRedundantPopups;
    public final String mSpaceBarText;
    public final float mFontSizeMultiplier;
    public final float mHintFontSizeMultiplier;
    public final float mFontSizeMultiplierEmoji;
    public final boolean mEmojiKeyFit;
    public final PunctuationSuggestions mPunctuationSuggestions;
    public final int mKeypressVibrationDuration;
    public final boolean mAutoCorrectCapitalizedSuggestion;
    public final boolean mBackspaceRevertsAutocorrect;
    public final boolean mAutoCorrectShortcuts;
    public final boolean mSuggestionsEnabled;
    public final boolean mSuggestClipboardContent;
    public final boolean mLongPressSymbolsForNumpad;

    // From the input box
    @NonNull
    public final InputAttributes mInputAttributes;

    // Deduced settings
    public final boolean mSuggestionStripHiddenPerUserSettings;
    public final boolean mAutoCorrectEnabled;
    public final float mAutoCorrectionThreshold;
    public final int mScoreLimitForAutocorrect;
    public final SettingsValuesForSuggestion mSettingsValuesForSuggestion;
    public final boolean mIsLocked;

    // User-defined colors
    public final Colors mColors;

    // creation of Colors and SpacingAndPunctuations are the slowest parts in here, but still ok
    public SettingsValues(final Context context, final SharedPreferences prefs, final Resources res,
                          @NonNull final InputAttributes inputAttributes) {
        mLocale = ConfigurationCompatKt.locale(res.getConfiguration());
        mDisplayOrientation = res.getConfiguration().orientation;
        final InputMethodSubtype selectedSubtype = SubtypeSettings.INSTANCE.getSelectedSubtype(prefs);

        // Store the input attributes
        mInputAttributes = inputAttributes;

        // Get the settings preferences
        mIsLocked = IsLockedCompatKt.isDeviceLocked(context);
        // we want to hide the toolbar / suggestion strip entirely if device is locked
        // M2Y Board: toolbar is fixed (keys + suggestions, swipe down hides it), there is no toolbar screen
        mToolbarMode = mIsLocked ? ToolbarMode.HIDDEN : ToolbarMode.EXPANDABLE;
        mToolbarSwipeDownToHide = true;
        mToolbarHidingGlobal = true;
        mAutoCap = ScriptUtils.scriptSupportsUppercase(mLocale); // M2Y Board: always on, no setting
        mVibrateOn = Settings.readVibrationEnabled(prefs);
        mVibrateInDndMode = prefs.getBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, Defaults.PREF_VIBRATE_IN_DND_MODE);
        mSuggestEmojis = false; // M2Y Board: fixed suggestion settings, no screen
        mInlineEmojiSearch = false;
        mShowEmojiDescriptions = prefs.getBoolean(Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, Defaults.PREF_SHOW_EMOJI_DESCRIPTIONS);
        mKeyPreviewPopupOn = false; // M2Y Board: no key preview popup, saves a window update per key press
        mSlidingKeyInputPreviewEnabled = true;
        mShowsVoiceInputKey = mInputAttributes.mShouldShowVoiceInputKey;
        String languagePref = prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, Defaults.PREF_LANGUAGE_SWITCH_KEY);
        mLanguageSwitchKeyToOtherImes = languagePref.equals("input_method") || languagePref.equals("both");
        mLanguageSwitchKeyToOtherSubtypes = mIsLocked || languagePref.equals("internal") || languagePref.equals("both");
        mShowsNumberRow = true; // M2Y Board: the number row is part of the fixed layout, no setting
        mShowsNumberRowInSymbols = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, Defaults.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS);
        mLocalizedNumberRow = SubtypeUtilsKt.getHasLocalizedNumberRow(selectedSubtype, prefs);
        mShowNumberRowHints = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW_HINTS, Defaults.PREF_SHOW_NUMBER_ROW_HINTS);
        mShowsHints = false; // M2Y Board: iPhone keys carry no long-press hint letters
        mShowsPopupHints = prefs.getBoolean(Settings.PREF_SHOW_POPUP_HINTS, Defaults.PREF_SHOW_POPUP_HINTS);
        mShowTldPopupKeys = prefs.getBoolean(Settings.PREF_SHOW_TLD_POPUP_KEYS, Defaults.PREF_SHOW_TLD_POPUP_KEYS);
        mSpaceForLangChange = prefs.getBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, Defaults.PREF_SPACE_TO_CHANGE_LANG);
        mShowsDpadKey = prefs.getBoolean(Settings.PREF_SHOW_DPAD_KEY, Defaults.PREF_SHOW_DPAD_KEY);
        mVarToolbarDirection = mToolbarMode != ToolbarMode.HIDDEN;
        mUseDoubleSpacePeriod = inputAttributes.mIsGeneralTextInput; // M2Y Board: always on for normal text
        mBlockPotentiallyOffensive = false; // M2Y Board: no word filter
        mUrlDetectionEnabled = prefs.getBoolean(Settings.PREF_URL_DETECTION, Defaults.PREF_URL_DETECTION);
        mAutoCorrectEnabled = false;
        mCenterSuggestionTextToEnter = false;
        float autoCorrectConfidence = Defaults.PREF_AUTO_CORRECT_CONFIDENCE;
        // confidence -> threshold and score limit are just some formulas that give something similar to the old values, so that confidence can be in a nice 0-1 range
        mAutoCorrectionThreshold = mAutoCorrectEnabled
                ? 0.5f - 0.5f * (float)Math.pow(autoCorrectConfidence, 0.33)
                : Float.MAX_VALUE;
        mScoreLimitForAutocorrect = (int)((1.14 - 0.2 * Math.pow(autoCorrectConfidence + 0.47, 3.5)) * 900000);
        mAutoCorrectCapitalizedSuggestion = Defaults.PREF_AUTOCORRECT_CAPITALIZED_SUGGESTION;
        mAutoCorrectShortcuts = Defaults.PREF_AUTOCORRECT_SHORTCUTS;
        mBackspaceRevertsAutocorrect = Defaults.PREF_BACKSPACE_REVERTS_AUTOCORRECT;
        mBigramPredictionEnabled = true;
        mSuggestPunctuation = true;
        mSuggestClipboardContent = true;
        mDoubleSpacePeriodTimeout = 1100; // ms
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.getConfiguration());
        boolean isLandscape = mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean isFolded = FoldableUtils.INSTANCE.isFolded();
        // M2Y Board: sizes are fixed to the Defaults for the current orientation, there are no size settings
        final int sizeIndex = SettingsKt.findIndexOfDefaultSetting(isLandscape, isFolded);

        // Compute other readable settings
        mKeyLongpressTimeout = prefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, Defaults.PREF_KEY_LONGPRESS_TIMEOUT);
        mKeypressVibrationDuration = prefs.getInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, Defaults.PREF_VIBRATION_DURATION_SETTINGS);
        mEnableEmojiAltPhysicalKey = prefs.getBoolean(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, Defaults.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY);
        mEnableHardwareKeyboard = prefs.getBoolean(Settings.PREF_ENABLE_HARDWARE_KEYBOARD, Defaults.PREF_ENABLE_HARDWARE_KEYBOARD);
        mSuggestionStripHiddenPerUserSettings = mToolbarMode == ToolbarMode.HIDDEN || mToolbarMode == ToolbarMode.TOOLBAR_KEYS;
        mSuggestionsEnabled = mInputAttributes.mShouldShowSuggestions && !mSuggestionStripHiddenPerUserSettings;
        mBottomRowScale = Defaults.PREF_BOTTOM_ROW_SCALE[sizeIndex];
        mSpaceSwipeHorizontal = Settings.readHorizontalSpaceSwipe(prefs);
        mSpaceSwipeVertical = Settings.readVerticalSpaceSwipe(prefs);
        mLanguageSwipeDistance = prefs.getInt(Settings.PREF_LANGUAGE_SWIPE_DISTANCE, Defaults.PREF_LANGUAGE_SWIPE_DISTANCE);
        mTouchpadSensitivity = prefs.getInt(Settings.PREF_TOUCHPAD_SENSITIVITY,
            Defaults.PREF_TOUCHPAD_SENSITIVITY);
        mTouchpadEdgeScroll = prefs.getBoolean(Settings.PREF_TOUCHPAD_EDGE_SCROLL, Defaults.PREF_TOUCHPAD_EDGE_SCROLL);
        mDeleteSwipeEnabled = prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE);
        mAutospaceAfterPunctuation = true; // M2Y Board: fixed, no setting
        mAutospaceAfterSuggestion = true;
        mClipboardHistoryEnabled = true; // M2Y Board: clipboard history kept 10 minutes, pinned clips first
        mClipboardHistoryRetentionTime = 10; // minutes
        mClipboardHistoryPinnedFirst = true;

        mKeyboardHeightScale = Defaults.PREF_KEYBOARD_HEIGHT_SCALE[sizeIndex];
        mSecondaryLocales = SubtypeUtilsKt.getSecondaryLocales(selectedSubtype.getExtraValue());
        mShowMorePopupKeys = SubtypeUtilsKt.getMoreKeys(selectedSubtype, prefs,
            selectedSubtype.isAsciiCapable() ? Defaults.PREF_MORE_POPUP_KEYS : LocaleKeyboardInfos.POPUP_KEYS_NORMAL);
        mColors = KeyboardTheme.getColorsForCurrentTheme(context);

        mPopupKeyOrder = SubtypeUtilsKt.getPopupKeyOrder(selectedSubtype, prefs);
        mPopupKeyHintOrder = SubtypeUtilsKt.getPopupKeyHintOrder(selectedSubtype, prefs);
        mCustomNavBarColor = Defaults.PREF_NAVBAR_COLOR;
        mKeyGapScale = Defaults.PREF_KEY_GAP_SCALE[sizeIndex];
        mSettingsValuesForSuggestion = new SettingsValuesForSuggestion(mBlockPotentiallyOffensive);
        mSpacingAndPunctuations = new SpacingAndPunctuations(res, mUrlDetectionEnabled);
        mBottomPaddingScale = Defaults.PREF_BOTTOM_PADDING_SCALE[sizeIndex];
        mSidePaddingScale = Defaults.PREF_SIDE_PADDING_SCALE[SettingsKt.findIndexOfDefaultSetting(isLandscape, false, isFolded)];
        mLongPressSymbolsForNumpad = prefs.getBoolean(Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD, Defaults.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD);
        mAlphaAfterEmojiInEmojiView = prefs.getBoolean(Settings.PREF_ABC_AFTER_EMOJI, Defaults.PREF_ABC_AFTER_EMOJI);
        mAlphaAfterClipHistoryEntry = prefs.getBoolean(Settings.PREF_ABC_AFTER_CLIP, Defaults.PREF_ABC_AFTER_CLIP);
        if (prefs.getBoolean(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, Defaults.PREF_ABC_AFTER_SYMBOL_SPACE)) {
            mAlphaAfterSpace.add(KeyboardState.Mode.SYMBOLS);
            mAlphaAfterSpace.add(KeyboardState.Mode.SYMBOLS_SHIFTED);
        }
        if (prefs.getBoolean(Settings.PREF_ABC_AFTER_NUMPAD_SPACE, Defaults.PREF_ABC_AFTER_NUMPAD_SPACE)) {
            mAlphaAfterSpace.add(KeyboardState.Mode.NUMPAD);
        }
        mRemoveRedundantPopups = prefs.getBoolean(Settings.PREF_REMOVE_REDUNDANT_POPUPS, Defaults.PREF_REMOVE_REDUNDANT_POPUPS);
        mSpaceBarText = Defaults.PREF_SPACE_BAR_TEXT;
        mFontSizeMultiplier = Defaults.PREF_FONT_SCALE;
        mHintFontSizeMultiplier = Defaults.PREF_HINT_FONT_SCALE;
        mFontSizeMultiplierEmoji = Defaults.PREF_EMOJI_FONT_SCALE;
        mEmojiKeyFit = Defaults.PREF_EMOJI_KEY_FIT;
        mPunctuationSuggestions = Settings.readPunctuationSuggestions(context);
    }

    public boolean needsToLookupSuggestions() {
        return mInputAttributes.mShouldShowSuggestions
                && (mAutoCorrectEnabled || mSuggestionsEnabled);
    }

    public boolean isWordSeparator(final int code) {
        return mSpacingAndPunctuations.isWordSeparator(code);
    }

    public boolean isWordConnector(final int code) {
        return mSpacingAndPunctuations.isWordConnector(code);
    }

    public boolean isWordCodePoint(final int code) {
        return mSpacingAndPunctuations.isWordCodePoint(code);
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyPrecededBySpace(code);
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyFollowedBySpace(code);
    }

    public boolean shouldInsertSpacesAutomatically() {
        return mInputAttributes.mShouldInsertSpacesAutomatically;
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return mInputAttributes.isSameInputType(editorInfo);
    }

    public boolean hasSameOrientation(final Configuration configuration) {
        return mDisplayOrientation == configuration.orientation;
    }

    public boolean isSecondaryStripVisible() {
        return mToolbarMode != ToolbarMode.HIDDEN || !mToolbarHidingGlobal;
    }

    public String dump() {
        final StringBuilder sb = new StringBuilder("Current settings :");
        sb.append("\n   mSpacingAndPunctuations = ");
        sb.append("" + mSpacingAndPunctuations.dump());
        sb.append("\n   mAutoCap = ");
        sb.append("" + mAutoCap);
        sb.append("\n   mVibrateOn = ");
        sb.append("" + mVibrateOn);
        sb.append("\n   mKeyPreviewPopupOn = ");
        sb.append("" + mKeyPreviewPopupOn);
        sb.append("\n   mShowsVoiceInputKey = ");
        sb.append("" + mShowsVoiceInputKey);
        sb.append("\n   mLanguageSwitchKeyToOtherImes = ");
        sb.append("" + mLanguageSwitchKeyToOtherImes);
        sb.append("\n   mLanguageSwitchKeyToOtherSubtypes = ");
        sb.append("" + mLanguageSwitchKeyToOtherSubtypes);
        sb.append("\n   mUseDoubleSpacePeriod = ");
        sb.append("" + mUseDoubleSpacePeriod);
        sb.append("\n   mBlockPotentiallyOffensive = ");
        sb.append("" + mBlockPotentiallyOffensive);
        sb.append("\n   mBigramPredictionEnabled = ");
        sb.append("" + mBigramPredictionEnabled);
        sb.append("\n   mSlidingKeyInputPreviewEnabled = ");
        sb.append("" + mSlidingKeyInputPreviewEnabled);
        sb.append("\n   mKeyLongpressTimeout = ");
        sb.append("" + mKeyLongpressTimeout);
        sb.append("\n   mLocale = ");
        sb.append("" + mLocale);
        sb.append("\n   mInputAttributes = ");
        sb.append("" + mInputAttributes);
        sb.append("\n   mKeypressVibrationDuration = ");
        sb.append("" + mKeypressVibrationDuration);
        sb.append("\n   mAutoCorrectEnabled = ");
        sb.append("" + mAutoCorrectEnabled);
        sb.append("\n   mAutoCorrectionThreshold = ");
        sb.append("" + mAutoCorrectionThreshold);
        sb.append("\n   mSuggestionsEnabledPerUserSettings = ");
        sb.append("" + mSuggestionsEnabled);
        sb.append("\n   mDisplayOrientation = ");
        sb.append("" + mDisplayOrientation);
        sb.append("\n   mAppWorkarounds = ");
        return sb.toString();
    }
}
