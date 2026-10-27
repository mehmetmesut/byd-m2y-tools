// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.compat.isUserLocked
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.encodeBase36
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutType.Companion.folder
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ScriptUtils.SCRIPT_LATIN
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.getResourceSubtypes
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

fun checkVersionUpgrade(context: Context) {
    val prefs = context.prefs()
    val oldVersion = prefs.getInt(Settings.PREF_VERSION_CODE, 0)
    if (oldVersion != BuildConfig.VERSION_CODE)
        AppUpgrade.onUpgrade(context)
}

fun transferOldPinnedClips(context: Context) {
    @Serializable
    data class OldClipboardHistoryEntry (
        var timeStamp: Long,
        val content: String,
        var isPinned: Boolean = false
    )
    if (isUserLocked(context) || isDeviceLocked(context)) return
    try {
        val pinnedClipString = context.protectedPrefs().getString("pinned_clips", "")
        if (pinnedClipString.isNullOrBlank())
            return
        val pinnedClips: List<OldClipboardHistoryEntry> = Json.decodeFromString(pinnedClipString)
        val dao = ClipboardDao.getInstance(context) ?: return
        pinnedClips.forEach { dao.addClip(it.timeStamp, it.isPinned, it.content) }
        context.protectedPrefs().edit { remove("pinned_clips") }
    } catch (e: Throwable) {
        Log.e("upgrade", "error transferring old pinned clips", e)
    }
}

private object AppUpgrade {
    fun onUpgrade(context: Context) {
        val prefs = context.prefs()
        val oldVersion = prefs.getInt(Settings.PREF_VERSION_CODE, 0)
        if (oldVersion == 0) {
            // fresh install -> no need to upgrade, as this does things like keeping old defaults which is on wanted on actual upgrade
            prefs.edit { putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE) }
            return
        }
        // clear extracted dictionaries, in case updated version contains newer ones
        DictionaryInfoUtils.getCacheDirectories(context).forEach {
            for (file in it.listFiles()!!) {
                if (!file.name.endsWith(USER_DICTIONARY_SUFFIX))
                    file.delete()
            }
        }
        if (oldVersion <= 1000) { // upgrade old custom layouts name
            val oldShiftSymbolsFile = getCustomLayoutFile("custom.shift_symbols", context)
            if (oldShiftSymbolsFile.exists()) {
                oldShiftSymbolsFile.renameTo(getCustomLayoutFile("custom.symbols_shifted", context))
            }
            if (prefs.contains("enabled_input_styles")) {
                // rename subtype setting, and clean old subtypes that might remain in some cases
                val subtypesPref = prefs.getString("enabled_input_styles", "")!!
                    .split(";").filter { it.isNotEmpty() }
                    .map {
                        val localeAndLayout = it.split(":").toMutableList()
                        localeAndLayout[0] = localeAndLayout[0].constructLocale().toLanguageTag()
                        localeAndLayout.joinToString(":")
                    }.toSet().joinToString(";")
                prefs.edit { remove("enabled_input_styles").putString(Settings.PREF_ENABLED_SUBTYPES, subtypesPref) }
            }
            if (prefs.contains("selected_input_style")) {
                val selectedSubtype = prefs.getString("selected_input_style", "")
                prefs.edit { remove("selected_input_style").putString(Settings.PREF_SELECTED_SUBTYPE, selectedSubtype) }
            }
        }
        if (oldVersion <= 2201) {
            val additionalSubtypeString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
            if (additionalSubtypeString.contains(".")) { // means there are custom layouts
                val subtypeStrings = additionalSubtypeString.split(";")
                val newSubtypeStrings = subtypeStrings.mapNotNull {
                    if ("." !in it) // not a custom subtype, nothing to do
                        return@mapNotNull it
                    val split = it.split(":").toMutableList()
                    if (split.size < 2) return@mapNotNull null // should never happen
                    val oldName = split[1]
                    val newName = oldName.substringBeforeLast(".") + "."
                    if (oldName == newName) return@mapNotNull split.joinToString(":") // should never happen
                    val oldFile = getCustomLayoutFile(oldName, context)
                    val newFile = getCustomLayoutFile(newName, context)
                    if (!oldFile.exists()) return@mapNotNull null // should never happen
                    if (newFile.exists()) newFile.delete() // should never happen
                    oldFile.renameTo(newFile)
                    val enabledSubtypes = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, "")!!
                    if (enabledSubtypes.contains(oldName))
                        prefs.edit { putString(Settings.PREF_ENABLED_SUBTYPES, enabledSubtypes.replace(oldName, newName)) }
                    val selectedSubtype = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!
                    if (selectedSubtype.contains(oldName))
                        prefs.edit { putString(Settings.PREF_SELECTED_SUBTYPE, selectedSubtype.replace(oldName, newName)) }
                    split[1] = newName
                    split.joinToString(":")
                }
                prefs.edit { putString(Settings.PREF_ADDITIONAL_SUBTYPES, newSubtypeStrings.joinToString(";")) }
            }
            // rename other custom layouts
            LayoutUtilsCustom.onLayoutFileChanged()
            File(DeviceProtectedUtils.getFilesDir(context), "layouts").listFiles()?.forEach {
                val newFile = getCustomLayoutFile(it.name.substringBeforeLast(".") + ".", context)
                if (newFile.name == it.name) return@forEach
                if (newFile.exists()) newFile.delete() // should never happen
                it.renameTo(newFile)
            }
        }
        if (oldVersion <= 2303) {
            File(DeviceProtectedUtils.getFilesDir(context), "layouts").listFiles()?.forEach { file ->
                val folder = DeviceProtectedUtils.getFilesDir(context)
                if (file.isDirectory) return@forEach
                when (file.name) {
                    "custom.symbols." -> {
                        val dir = File(folder, LayoutType.SYMBOLS.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("symbols")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.SYMBOLS.name, name) }
                    }
                    "custom.symbols_shifted." -> {
                        val dir = File(folder, LayoutType.MORE_SYMBOLS.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("symbols_shifted")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.MORE_SYMBOLS.name, name) }
                    }
                    "custom.symbols_arabic." -> {
                        val dir = File(folder, LayoutType.SYMBOLS.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("symbols_arabic")}."
                        file.renameTo(File(dir, name))
                    }
                    "custom.numpad." -> {
                        val dir = File(folder, LayoutType.NUMPAD.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("numpad")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMPAD.name, name) }
                    }
                    "custom.numpad_landscape." -> {
                        val dir = File(folder, LayoutType.NUMPAD_LANDSCAPE.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("numpad_landscape")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMPAD_LANDSCAPE.name, name) }
                    }
                    "custom.number." -> {
                        val dir = File(folder, LayoutType.NUMBER.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("number")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMBER.name, name) }
                    }
                    "custom.phone." -> {
                        val dir = File(folder, LayoutType.PHONE.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("phone")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.PHONE.name, name) }
                    }
                    "custom.phone_symbols." -> {
                        val dir = File(folder, LayoutType.PHONE_SYMBOLS.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("phone_symbols")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.PHONE_SYMBOLS.name, name) }
                    }
                    "custom.number_row." -> {
                        val dir = File(folder, LayoutType.NUMBER_ROW.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("number_row")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMBER_ROW.name, name) }
                    }
                    "custom.emoji_bottom_row." -> {
                        val dir = File(folder, LayoutType.EMOJI_BOTTOM.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("emoji_bottom_row")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.EMOJI_BOTTOM.name, name) }
                    }
                    "custom.clip_bottom_row." -> {
                        val dir = File(folder, LayoutType.CLIPBOARD_BOTTOM.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("clip_bottom_row")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.CLIPBOARD_BOTTOM.name, name) }
                    }
                    "custom.functional_keys." -> {
                        val dir = File(folder, LayoutType.FUNCTIONAL.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("functional_keys")}."
                        file.renameTo(File(dir, name))
                        prefs.edit { putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name, name) }
                    }
                    "custom.functional_keys_symbols." -> {
                        val dir = File(folder, LayoutType.FUNCTIONAL.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("functional_keys_symbols")}."
                        file.renameTo(File(dir, name))
                    }
                    "custom.functional_keys_symbols_shifted." -> {
                        val dir = File(folder, LayoutType.FUNCTIONAL.folder)
                        dir.mkdirs()
                        val name = "custom.${encodeBase36("functional_keys_symbols_shifted")}."
                        file.renameTo(File(dir, name))
                    }
                    else -> {
                        // main layouts
                        val dir = File(folder, LayoutType.MAIN.folder)
                        dir.mkdirs()
                        file.renameTo(File(dir, file.name))
                    }
                }
            }
            if (prefs.contains(Settings.PREF_ADDITIONAL_SUBTYPES))
                prefs.edit {
                    putString(
                        Settings.PREF_ADDITIONAL_SUBTYPES, prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                            .replace(":", Separators.SET)
                    )
                }
        }
        if (oldVersion <= 2304) {
            // rename layout files for latin scripts, and adjust layouts stored in prefs accordingly
            LayoutUtilsCustom.getLayoutFiles(LayoutType.MAIN, context).forEach {
                val locale = it.name.substringAfter("custom.").substringBefore(".").constructLocale()
                if (locale.script() != SCRIPT_LATIN) return@forEach
                // change language tag to SCRIPT_LATIN, but
                //  avoid overwriting if 2 layouts have a different language tag, but the same name
                val layoutDisplayName = LayoutUtilsCustom.getDisplayName(it.name)
                var newFile = File(it.parentFile!!, LayoutUtilsCustom.getLayoutName(layoutDisplayName, LayoutType.MAIN, locale))
                var i = 1
                while (newFile.exists()) // make sure name is not already in use, e.g. custom.en.abcd. and custom.it.abcd. would both be custom.Latn.abcd
                    newFile = File(it.parentFile!!, LayoutUtilsCustom.getLayoutName(layoutDisplayName + i++, LayoutType.MAIN, locale))
                it.renameTo(newFile)
                // modify prefs
                listOf(Settings.PREF_ENABLED_SUBTYPES, Settings.PREF_SELECTED_SUBTYPE, Settings.PREF_ADDITIONAL_SUBTYPES).forEach { key ->
                    val value = prefs.getString(key, "")!!
                    if (it.name in value)
                        prefs.edit { putString(key, value.replace(it.name, newFile.name)) }
                }
                LayoutUtilsCustom.onLayoutFileChanged()
            }
        }
        if (oldVersion <= 2305) {
            (prefs.all.keys.filter { it.startsWith(Settings.PREF_POPUP_KEYS_ORDER) || it.startsWith(Settings.PREF_POPUP_KEYS_HINT_ORDER) } +
                listOf("toolbar_keys", "pinned_toolbar_keys", "clipboard_toolbar_keys")).forEach {
                if (!prefs.contains(it)) return@forEach
                val newValue = prefs.getString(it, "")!!.replace(",", Separators.KV).replace(";", Separators.ENTRY)
                prefs.edit { putString(it, newValue) }
            }
            listOf(Settings.PREF_ENABLED_SUBTYPES, Settings.PREF_SELECTED_SUBTYPE, Settings.PREF_ADDITIONAL_SUBTYPES).forEach {
                if (!prefs.contains(it)) return@forEach
                val value = prefs.getString(it, "")!!.replace(":", Separators.SET)
                prefs.edit { putString(it, value) }
            }
            prefs.all.keys.filter { it.startsWith("secondary_locales_") }.forEach {
                val newValue = prefs.getString(it, "")!!.replace(";", Separators.KV)
                prefs.edit { putString(it, newValue) }
            }
        }
        if (oldVersion <= 2306) {
            // upgrade additional, enabled, and selected subtypes to same format of locale and (filtered) extra value
            if (prefs.contains(Settings.PREF_ADDITIONAL_SUBTYPES)) {
                val new = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                    .split(Separators.SETS)
                    .filter { it.isNotEmpty() }
                    .mapNotNull { pref ->
                        val oldSplit = pref.split(Separators.SET)
                        if (oldSplit.size < 3) return@mapNotNull null
                        val languageTag = oldSplit[0]
                        val mainLayoutName = oldSplit[1]
                        val extraValue = oldSplit[2]
                        SettingsSubtype(
                            languageTag.constructLocale(),
                            ExtraValue.KEYBOARD_LAYOUT_SET + "=MAIN" + Separators.KV + mainLayoutName + "," + extraValue
                        ).toAdditionalSubtype().toSettingsSubtype().toPref()
                    }.joinToString(Separators.SETS)
                prefs.edit { putString(Settings.PREF_ADDITIONAL_SUBTYPES, new) }
            }
            listOf(Settings.PREF_ENABLED_SUBTYPES, Settings.PREF_SELECTED_SUBTYPE).forEach { key ->
                if (!prefs.contains(key)) return@forEach
                val resourceSubtypes = getResourceSubtypes(context.resources)
                val additionalSubtypeString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
                val additionalSubtypes = SubtypeUtilsAdditional.createAdditionalSubtypes(additionalSubtypeString)
                val new = prefs.getString(key, "")!!.split(Separators.SETS).filter { it.isNotEmpty() }.joinToString(Separators.SETS) { pref ->
                    val oldSplit = pref.split(Separators.SET)
                    val languageTag = oldSplit[0]
                    if (oldSplit.size == 1)
                        return@joinToString resourceSubtypes.first { it.locale().language == languageTag.constructLocale().language }
                            .toSettingsSubtype().toPref()
                    val mainLayoutName = oldSplit[1]
                    // we now need more information than just locale and main layout name, get it from existing subtypes
                    val filtered = additionalSubtypes.filter {
                        it.locale().toLanguageTag() == languageTag && (it.mainLayoutNameOrQwerty()) == mainLayoutName
                    }
                    if (filtered.isNotEmpty())
                        return@joinToString filtered.first().toSettingsSubtype().toPref()
                    // find best matching resource subtype
                    val goodMatch = resourceSubtypes.filter {
                        it.locale().toLanguageTag() == languageTag && (it.mainLayoutNameOrQwerty()) == mainLayoutName
                    }
                    if (goodMatch.isNotEmpty())
                        return@joinToString goodMatch.first().toSettingsSubtype().toPref()
                    // not sure how we can get here, but better deal with it
                    val okMatch = resourceSubtypes.filter {
                        it.locale().language == languageTag.constructLocale().language && (it.mainLayoutNameOrQwerty()) == mainLayoutName
                    }
                    if (okMatch.isNotEmpty())
                        okMatch.first().toSettingsSubtype().toPref()
                    else resourceSubtypes.first { it.locale().language == languageTag.constructLocale().language }
                        .toSettingsSubtype().toPref()
                }
                prefs.edit { putString(key, new) }
            }
        }
        if (oldVersion <= 2307) {
            prefs.all.keys.forEach {
                if (!it.startsWith(Settings.PREF_POPUP_KEYS_ORDER) && !it.startsWith(Settings.PREF_POPUP_KEYS_HINT_ORDER))
                    return@forEach
                prefs.edit { putString(it, prefs.getString(it, "")!!.replace("popup_keys_", "")) }
            }
        }
        if (oldVersion <= 2308) {
            SubtypeSettings.reloadEnabledSubtypes(context)
            prefs.all.keys.toList().forEach { key ->
                if (key.startsWith(Settings.PREF_POPUP_KEYS_ORDER+"_")) {
                    val locale = key.substringAfter(Settings.PREF_POPUP_KEYS_ORDER+"_").constructLocale()
                    SubtypeSettings.getEnabledSubtypes().forEach {
                        if (it.locale() == locale && !SubtypeSettings.isAdditionalSubtype(it)) {
                            SubtypeUtilsAdditional.changeAdditionalSubtype(it.toSettingsSubtype(), it.toSettingsSubtype(), context)
                        }
                    }
                    val additional = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                    additional.split(Separators.SETS).filter { it.isNotEmpty() }.forEach inner@{
                        val subtype = it.toSettingsSubtype()
                        if (subtype.locale != locale) return@inner
                        val newSubtype = subtype.with(ExtraValue.POPUP_ORDER, prefs.getString(key, ""))
                        SubtypeUtilsAdditional.changeAdditionalSubtype(subtype, newSubtype, context)
                    }
                    prefs.edit { remove(key) }
                }
                if (key.startsWith(Settings.PREF_POPUP_KEYS_HINT_ORDER+"_")) {
                    val locale = key.substringAfter(Settings.PREF_POPUP_KEYS_HINT_ORDER+"_").constructLocale()
                    SubtypeSettings.getEnabledSubtypes().forEach {
                        if (it.locale() == locale && !SubtypeSettings.isAdditionalSubtype(it)) {
                            SubtypeUtilsAdditional.changeAdditionalSubtype(it.toSettingsSubtype(), it.toSettingsSubtype(), context)
                        }
                    }
                    val additional = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                    additional.split(Separators.SETS).filter { it.isNotEmpty() }.forEach inner@{
                        val subtype = it.toSettingsSubtype()
                        if (subtype.locale != locale) return@inner
                        val newSubtype = subtype.with(ExtraValue.HINT_ORDER, prefs.getString(key, ""))
                        SubtypeUtilsAdditional.changeAdditionalSubtype(subtype, newSubtype, context)
                    }
                    prefs.edit { remove(key) }
                }
                if (key.startsWith("secondary_locales_")) {
                    val locale = key.substringAfter("secondary_locales_").constructLocale()
                    SubtypeSettings.getEnabledSubtypes().forEach {
                        if (it.locale() == locale && !SubtypeSettings.isAdditionalSubtype(it)) {
                            SubtypeUtilsAdditional.changeAdditionalSubtype(it.toSettingsSubtype(), it.toSettingsSubtype(), context)
                        }
                    }
                    val additional = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                    val secondaryLocales = prefs.getString(key, "")!!.split(Separators.KV).filter { it.isNotBlank() }.joinToString(Separators.KV)
                    additional.split(Separators.SETS).filter { it.isNotEmpty() }.forEach inner@{
                        val subtype = it.toSettingsSubtype()
                        if (subtype.locale != locale) return@inner
                        val newSubtype = subtype.with(ExtraValue.SECONDARY_LOCALES, secondaryLocales)
                        SubtypeUtilsAdditional.changeAdditionalSubtype(subtype, newSubtype, context)
                    }
                    prefs.edit { remove(key) }
                }
            }
        }
        if (oldVersion <= 2309) {
            if (prefs.contains("auto_correction_confidence")) {
                val value = when (prefs.getString("auto_correction_confidence", "0")) {
                    "1" -> 0.067f
                    "2" -> -1f
                    else -> 0.185f
                }
                prefs.edit { remove("auto_correction_confidence").putFloat("auto_correct_threshold", value) }
            }
        }
        if (oldVersion <= 2310) {
            listOf(
                Settings.PREF_ENABLED_SUBTYPES,
                Settings.PREF_SELECTED_SUBTYPE,
                Settings.PREF_ADDITIONAL_SUBTYPES
            ).forEach { key ->
                val value = prefs.getString(key, "")!!
                if ("bengali," in value) {
                    prefs.edit { putString(key, value.replace("bengali,", "bengali_inscript,")) }
                }
            }
        }
        if (oldVersion <= 3101) {
            @SuppressLint("UseKtx") val e = prefs.edit()
            prefs.all.toMap().forEach { (key, value) ->
                if (key == "side_padding_scale") {
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 2), value as Float)
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 2, 2), value)
                } else if (key == "side_padding_scale_landscape") {
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 1, 2), value as Float)
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 3, 2), value)
                } else if (key == "bottom_padding_scale") {
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 1), value as Float)
                } else if (key == "bottom_padding_scale_landscape") {
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 1, 1), value as Float)
                } else if (key == "split_spacer_scale") {
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SPLIT_SPACER_SCALE_PREFIX, 0, 1), value as Float)
                } else if (key == "split_spacer_scale_landscape") {
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SPLIT_SPACER_SCALE_PREFIX, 1, 1), value as Float)
                } else if (key == "keyboard_height_scale") {
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 1, 1), value as Float)
                    e.putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 1, 1), value)
                } else {
                    if (key == Settings.PREF_ADDITIONAL_SUBTYPES || key == Settings.PREF_ENABLED_SUBTYPES) {
                        val subtypes = prefs.getString(key, "")!!.split(Separators.SETS).filter { it.isNotEmpty() }.map {
                            val st = it.toSettingsSubtype()
                            if (st.locale.language == "ko") st.with(ExtraValue.COMBINING_RULES, "hangul")
                            else st
                        }
                        e.putString(key, subtypes.joinToString(Separators.SETS) { it.toPref() })
                    } else if (key == Settings.PREF_SELECTED_SUBTYPE) {
                        val subtype = prefs.getString(key, "")!!.toSettingsSubtype()
                        if (subtype.locale.language == "ko")
                            e.putString(key, subtype.with(ExtraValue.COMBINING_RULES, "hangul").toPref())
                    }
                    return@forEach
                }
                e.remove(key)
            }
            e.apply()
        }
        if (oldVersion <= 3901) {
            prefs.edit {
                val prefixes2 = listOf(Settings.PREF_SPLIT_SPACER_SCALE_PREFIX, Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX,
                    Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, Settings.PREF_BOTTOM_ROW_SCALE_PREFIX)
                prefixes2.forEach { prefix ->
                    for (i in 0..1) {
                        val key = createPrefKeyForBooleanSettings(prefix, i, 1)
                        if (prefs.contains(key)) {
                            val newKey = createPrefKeyForBooleanSettings(prefix, i, 2)
                            putFloat(newKey, prefs.getFloat(key, 0f))
                            remove(key)
                        }
                    }
                }
                val prefixes3 = listOf(Settings.PREF_SIDE_PADDING_SCALE_PREFIX)
                prefixes3.forEach { prefix ->
                    for (i in 0..3) {
                        val key = createPrefKeyForBooleanSettings(prefix, i, 2)
                        if (prefs.contains(key)) {
                            val newKey = createPrefKeyForBooleanSettings(prefix, i, 3)
                            putFloat(newKey, prefs.getFloat(key, 0f))
                            remove(key)
                        }
                    }
                }
            }
            prefs.edit {
                if (prefs.contains(Settings.PREF_SPACE_HORIZONTAL_SWIPE))
                    putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, prefs.getString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, "")!!.uppercase())
                if (prefs.contains(Settings.PREF_SPACE_VERTICAL_SWIPE))
                    putString(Settings.PREF_SPACE_VERTICAL_SWIPE, prefs.getString(Settings.PREF_SPACE_VERTICAL_SWIPE, "")!!.uppercase())
            }
            if (prefs.contains("auto_correct_threshold")) {
                val newValue = when (prefs.getFloat("auto_correct_threshold", 0f)) {
                    in -2f..0f -> 1f
                    in 0f..0.1f -> 0.65f
                    else -> 0.24f
                }
                prefs.edit {
                    remove("auto_correct_threshold")
                    putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, newValue)
                }
            }
            prefs.edit {
                if (!prefs.getBoolean("narrow_key_gaps", false) && !Settings.getInstance().isTablet) {
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2), 1.75f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 1, 2), 1.1f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 2, 2), 1.75f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 3, 2), 1.1f)
                }
                remove("narrow_key_gaps")
            }
        }
        prefs.edit { remove("emoji_recent_keys"); remove("recent_emojis") } // M2Y Board: recent emojis are not kept
        prefs.edit { REMOVED_PREFS.forEach { remove(it) } } // M2Y Board: fixed settings no longer stored
        LayoutUtilsCustom.onLayoutFileChanged() // just to be sure
        prefs.edit { putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE) }
    }

    // old variant for old folder structure
    private fun getCustomLayoutFile(layoutName: String, context: Context): File =
        File(File(DeviceProtectedUtils.getFilesDir(context), "layouts"), layoutName)
}

// M2Y Board: preferences of settings that are now fixed
private val REMOVED_PREFS = listOf("toolbar_custom_key_codes", "sound_on", "suggest_emojis", "inline_emoji_search", "center_suggestion_text_to_enter", "show_suggestions", "always_show_suggestions", "always_show_suggestions_except_web_text", "var_toolbar_direction", "next_word_prediction", "suggest_punctuation", "punctuation_suggestions", "suggest_clipboard_content", "keypress_sound_volume", "show_hints", "enable_clipboard_history", "clipboard_history_retention_time", "clipboard_history_pinned_first", "quick_pin_toolbar_keys", "pinned_toolbar_keys", "toolbar_keys", "auto_show_toolbar", "auto_hide_toolbar", "clipboard_toolbar_keys", "toolbar_mode", "toolbar_hiding_global", "toolbar_swipe_down_to_hide", "spellcheck_suggest", "only_toolbar_with_hw_keyboard", "debug_mode", "long_press_symbols_for_numpad", "abc_after_clip", "abc_after_emoji", "abc_after_numpad_space", "abc_after_symbol_space", "delete_swipe", "enable_emoji_alt_physical_key", "enable_hardware_keyboard", "key_longpress_timeout", "language_swipe_distance", "language_switch_key", "remove_redundant_popups", "show_dpad_key", "show_emoji_descriptions", "show_number_row_hints", "show_number_row_in_symbols", "show_popup_hints", "show_tld_popup_keys", "prefs_long_press_keyboard_to_change_lang", "touchpad_edge_scroll", "touchpad_sensitivity", "url_detection", "vibrate_in_dnd_mode", "vibration_duration_settings", "vibrate_on", "horizontal_space_swipe", "vertical_space_swipe", "force_non_distinct_multitouch", "sliding_key_input_preview", "show_debug_settings", "show_suggestion_infos")
