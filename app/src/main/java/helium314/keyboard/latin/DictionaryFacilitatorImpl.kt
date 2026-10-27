/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin

import android.content.Context
import android.util.LruCache
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.DictionaryFacilitator.DictionaryInitializationListener
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.decapitalize
import helium314.keyboard.latin.common.mightBeEmoji
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.dictionary.DictionaryStats
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import helium314.keyboard.latin.dictionary.UserBinaryDictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Facilitates interaction with different kinds of dictionaries. Provides APIs
 * to instantiate and select the correct dictionaries (based on language and settings),
 * update entries and fetch suggestions.
 *
 *
 * Currently AndroidSpellCheckerService and LatinIME both use DictionaryFacilitator as
 * a client for interacting with dictionaries.
 */
class DictionaryFacilitatorImpl : DictionaryFacilitator {
    private var dictionaryGroups = listOf(DictionaryGroup())

    @Volatile
    private var mLatchForWaitingLoadingMainDictionaries = CountDownLatch(0)

    // todo: write cache never set, and never read (only written)
    //  tried to use read cache for a while, but small performance improvements are not worth the work,
    //  see https://github.com/HeliBorg/HeliBoard/issues/307
    private var mValidSpellingWordReadCache: LruCache<String, Boolean>? = null
    private var mValidSpellingWordWriteCache: LruCache<String, Boolean>? = null

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun setValidSpellingWordReadCache(cache: LruCache<String, Boolean>) {
        mValidSpellingWordReadCache = cache
    }

    override fun setValidSpellingWordWriteCache(cache: LruCache<String, Boolean>) {
        mValidSpellingWordWriteCache = cache
    }

    // judging by usage before adding multilingual typing, this should check primary group locale only
    override fun isForLocale(locale: Locale?): Boolean {
        return locale != null && locale == dictionaryGroups[0].locale
    }

    override fun onStartInput() {
    }

    override fun onFinishInput() {
        for (dictGroup in dictionaryGroups) {
            DictionaryFacilitator.ALL_DICTIONARY_TYPES.forEach { dictGroup.getDict(it)?.onFinishInput() }
        }
    }

    override fun isActive(): Boolean {
        return dictionaryGroups[0].locale.language.isNotEmpty()
    }

    override fun getMainLocale(): Locale {
        return dictionaryGroups[0].locale
    }

    override fun getCurrentLocale(): Locale {
        return currentlyPreferredDictionaryGroup.locale
    }

    override fun usesSameSettings(locales: List<Locale>): Boolean {
        return locales.size == dictionaryGroups.size
                && locales.none { findDictionaryGroupWithLocale(dictionaryGroups, it) == null }
    }

    // -------------- managing (loading & closing) dictionaries ------------

    override fun resetDictionaries(
        context: Context,
        newLocale: Locale,
        forceReloadMainDictionary: Boolean,
        dictNamePrefix: String,
        listener: DictionaryInitializationListener?
    ) {
        Log.i(TAG, "resetDictionaries, force reloading main dictionary: $forceReloadMainDictionary")

        val locales = getUsedLocales(newLocale, context)

        val subDictTypesToUse = listOf(Dictionary.TYPE_USER) // M2Y Board: no user history, nothing is learned

        val (newDictionaryGroups, existingDictsToCleanup) =
            getNewDictGroupsAndDictsToCleanup(locales, subDictTypesToUse, forceReloadMainDictionary, dictNamePrefix, context)

        // Replace Dictionaries.
        val oldDictionaryGroups: List<DictionaryGroup>
        synchronized(this) {
            oldDictionaryGroups = dictionaryGroups
            dictionaryGroups = newDictionaryGroups
            if (hasAtLeastOneUninitializedMainDictionary()) {
                asyncReloadUninitializedMainDictionaries(context, locales, listener)
            }
        }

        listener?.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary())

        // Clean up old dictionaries.
        existingDictsToCleanup.forEach { (locale, dictTypes) ->
            val dictGroupToCleanup = findDictionaryGroupWithLocale(oldDictionaryGroups, locale) ?: return@forEach
            for (dictType in dictTypes) {
                dictGroupToCleanup.closeDict(dictType)
            }
        }

        mValidSpellingWordWriteCache?.evictAll()
        mValidSpellingWordReadCache?.evictAll()
    }

    /** creates dictionaryGroups for [newLocales] with given [newSubDictTypes], trying to re-use existing dictionaries.
     * returns the new dictionaryGroups and unused dictionary types by locale */
    private fun getNewDictGroupsAndDictsToCleanup(
        newLocales: Collection<Locale>,
        newSubDictTypes: Collection<String>,
        forceReload: Boolean,
        dictNamePrefix: String,
        context: Context
    ): Pair<List<DictionaryGroup>, Map<Locale, List<String>>> {
        // Gather all dictionaries by locale. We may remove some from the list later.
        val existingDictsToCleanup = HashMap<Locale, MutableList<String>>()
        for (dictGroup in dictionaryGroups) {
            existingDictsToCleanup[dictGroup.locale] = DictionaryFacilitator.ALL_DICTIONARY_TYPES
                .filterTo(mutableListOf()) { dictGroup.hasDict(it) }
        }

        // create new dictionary groups and remove dictionaries to re-use from existingDictsToCleanup
        val newDictionaryGroups = mutableListOf<DictionaryGroup>()
        for (locale in newLocales) {
            // get existing dictionary group for new locale
            val oldDictGroupForLocale = findDictionaryGroupWithLocale(dictionaryGroups, locale)
            val dictTypesToCleanupForLocale = existingDictsToCleanup[locale]

            // create new or re-use already loaded main dict
            val mainDict: Dictionary?
            if (forceReload || oldDictGroupForLocale == null
                || !oldDictGroupForLocale.hasDict(Dictionary.TYPE_MAIN)
            ) {
                mainDict = null // null main dicts will be loaded later in asyncReloadUninitializedMainDictionaries
            } else {
                mainDict = oldDictGroupForLocale.getDict(Dictionary.TYPE_MAIN)
                dictTypesToCleanupForLocale?.remove(Dictionary.TYPE_MAIN)
            }

            // create new or re-use already loaded sub-dicts
            val subDicts: MutableMap<String, ExpandableBinaryDictionary> = HashMap()
            for (subDictType in newSubDictTypes) {
                val subDict: ExpandableBinaryDictionary
                if (forceReload || oldDictGroupForLocale == null
                    || !oldDictGroupForLocale.hasDict(subDictType)
                ) {
                    // Create a new dictionary.
                    subDict = createSubDict(subDictType, context, locale, null, dictNamePrefix) ?: continue
                } else {
                    // Reuse the existing dictionary.
                    subDict = oldDictGroupForLocale.getSubDict(subDictType) ?: continue
                    dictTypesToCleanupForLocale?.remove(subDictType)
                }
                subDicts[subDictType] = subDict
            }
            val newDictGroup = DictionaryGroup(locale, mainDict, subDicts, context)
            newDictionaryGroups.add(newDictGroup)
        }
        return newDictionaryGroups to existingDictsToCleanup
    }

    private fun asyncReloadUninitializedMainDictionaries(
        context: Context, locales: Collection<Locale>, listener: DictionaryInitializationListener?
    ) {
        val latchForWaitingLoadingMainDictionary = CountDownLatch(1)
        mLatchForWaitingLoadingMainDictionaries = latchForWaitingLoadingMainDictionary
        scope.launch {
            try {
                val useEmojiDict = Settings.getValues().mSuggestEmojis
                val dictGroupsWithNewMainDict = locales.mapNotNull {
                    val dictionaryGroup = findDictionaryGroupWithLocale(dictionaryGroups, it)
                    if (dictionaryGroup == null) {
                        Log.w(TAG, "Expected a dictionary group for $it but none found")
                        return@mapNotNull null // This should never happen
                    }
                    if (dictionaryGroup.getDict(Dictionary.TYPE_MAIN)?.isInitialized == true) null
                    else dictionaryGroup to DictionaryFactory.createMainDictionaryCollection(context, it, useEmojiDict)
                }
                synchronized(this) {
                    dictGroupsWithNewMainDict.forEach { (dictGroup, mainDict) ->
                        dictGroup.setMainDict(mainDict)
                    }
                }

                listener?.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary())
                latchForWaitingLoadingMainDictionary.countDown()
            } catch (e: Throwable) {
                Log.e(TAG, "could not initialize main dictionaries for $locales", e)
            }
        }
    }

    override fun closeDictionaries() {
        onFinishInput() // the dictionaries will save updates to file
        val dictionaryGroupsToClose: List<DictionaryGroup>
        synchronized(this) {
            dictionaryGroupsToClose = dictionaryGroups
            dictionaryGroups = listOf(DictionaryGroup())
        }
        for (dictionaryGroup in dictionaryGroupsToClose) {
            for (dictType in DictionaryFacilitator.ALL_DICTIONARY_TYPES) {
                dictionaryGroup.closeDict(dictType)
            }
        }
    }

    // The main dictionaries are loaded asynchronously. Don't cache the return value of these methods.
    override fun hasAtLeastOneInitializedMainDictionary(): Boolean =
        dictionaryGroups.any { it.getDict(Dictionary.TYPE_MAIN)?.isInitialized == true }

    override fun hasAtLeastOneUninitializedMainDictionary(): Boolean =
        dictionaryGroups.any { it.getDict(Dictionary.TYPE_MAIN)?.isInitialized != true }

    @Throws(InterruptedException::class)
    override fun waitForLoadingMainDictionaries(timeout: Long, unit: TimeUnit) {
        mLatchForWaitingLoadingMainDictionaries.await(timeout, unit)
    }

    // -------------- actual dictionary stuff like getting suggestions ------------

    override fun adjustConfidences(word: String, wasAutoCapitalized: Boolean) {
        if (dictionaryGroups.size == 1 || word.contains(Constants.WORD_SEPARATOR))
            return

        // if suggestion was auto-capitalized, check against both the suggestion and the de-capitalized suggestion
        val decapitalizedSuggestion = if (wasAutoCapitalized) word.decapitalize(currentLocale) else word
        val validWord = dictionaryGroups.map { isValidWord(word, DictionaryFacilitator.ALL_DICTIONARY_TYPES, it) }
        dictionaryGroups.forEach {
            if (isValidWord(word, DictionaryFacilitator.ALL_DICTIONARY_TYPES, it)) {
                it.increaseConfidence()
                return@forEach
            }
            // also increase confidence if suggestion was auto-capitalized and the lowercase variant it valid
            if (wasAutoCapitalized && isValidWord(decapitalizedSuggestion, DictionaryFacilitator.ALL_DICTIONARY_TYPES, it)) {
                it.increaseConfidence()
            } else {
                it.decreaseConfidence()
                if (validWord.any { it })
                    it.decreaseConfidence() // if the word is valid in another language decrease confidence even more
            }
        }
    }

    /** the dictionaryGroup with most confidence, first group when tied  */
    private val currentlyPreferredDictionaryGroup: DictionaryGroup get() = dictionaryGroups.maxBy { it.confidence }

    // TODO: Revise the way to fusion suggestion results.
    override fun getSuggestionResults(
        composedData: ComposedData, ngramContext: NgramContext, keyboard: Keyboard,
        settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, inputStyle: Int
    ): SuggestionResults {
        val proximityInfoHandle = keyboard.proximityInfo.nativeProximityInfo
        val weightOfLangModelVsSpatialModel = floatArrayOf(Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL)

        val waitForOtherDicts = if (dictionaryGroups.size == 1) null else CountDownLatch(dictionaryGroups.size - 1)
        val suggestionsArray = Array<List<SuggestedWordInfo>?>(dictionaryGroups.size) { null }
        for (i in 1..dictionaryGroups.lastIndex) {
            scope.launch {
                suggestionsArray[i] = getSuggestions(composedData, ngramContext, settingsValuesForSuggestion, sessionId,
                    proximityInfoHandle, weightOfLangModelVsSpatialModel, dictionaryGroups[i])
                waitForOtherDicts?.countDown()
            }
        }
        suggestionsArray[0] = getSuggestions(composedData, ngramContext, settingsValuesForSuggestion, sessionId,
            proximityInfoHandle, weightOfLangModelVsSpatialModel, dictionaryGroups[0])
        val suggestionResults = SuggestionResults(
            SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext, false
        )
        waitForOtherDicts?.await()

        suggestionsArray.forEach {
            if (it == null) return@forEach
            suggestionResults.addAll(it)
            suggestionResults.mRawSuggestions?.addAll(it)
        }

        includeAtLeastTwoWordSuggestions(suggestionResults, suggestionsArray, composedData.mTypedWord)

        return suggestionResults
    }

    private fun getSuggestions(
        composedData: ComposedData, ngramContext: NgramContext,
        settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int,
        proximityInfoHandle: Long, weightOfLangModelVsSpatialModel: FloatArray, dictGroup: DictionaryGroup
    ): List<SuggestedWordInfo> {
        val suggestions = ArrayList<SuggestedWordInfo>()
        val weightForLocale = dictGroup.getWeightForLocale(dictionaryGroups, composedData.mIsBatchMode)
        for (dictType in DictionaryFacilitator.ALL_DICTIONARY_TYPES) {
            val dictionary = dictGroup.getDict(dictType) ?: continue
            val dictionarySuggestions = dictionary.getSuggestions(composedData, ngramContext, proximityInfoHandle,
                settingsValuesForSuggestion, sessionId, weightForLocale, weightOfLangModelVsSpatialModel
            ) ?: continue

            // For some reason "garbage" words are produced when glide typing. For user history
            // and main dictionaries we can filter them out by checking whether the dictionary
            // actually contains the word. But personal and addon dictionaries may contain shortcuts,
            // which do not pass an isInDictionary check (e.g. emojis).
            // (if the main dict contains shortcuts to non-words, this will break!)
            val checkForGarbage = composedData.mIsBatchMode && dictType == Dictionary.TYPE_MAIN

            for (info in dictionarySuggestions) {
                val word = info.word
                if (SupportedEmojis.isUnsupported(word)) // don't add unsupported emojis
                    continue
                if (checkForGarbage
                    // consider the user might use custom main dictionary containing shortcuts
                    //  assume this is unlikely to happen, and take care about common shortcuts that are not actual words (emoji, symbols)
                    && word.length > 2 // should exclude most symbol shortcuts
                    && info.mSourceDict.mDictType == dictType // dictType is always main, but info.mSourceDict.mDictType contains the actual dict (main dict is a dictionary group)
                    && !mightBeEmoji(word) // simplified check for performance reasons
                    && !dictionary.isInDictionary(word)
                )
                    continue

                if (word.length == 1 && info.mSourceDict.mDictType == Dictionary.TYPE_EMOJI && !StringUtils.mightBeEmoji(word[0].code))
                    continue

                suggestions.add(info)
            }
        }
        return suggestions
    }

    // Spell checker is using this, and has its own instance of DictionaryFacilitatorImpl,
    // meaning that it always has default mConfidence. So we cannot choose to only check preferred
    // locale, and instead simply return true if word is in any of the available dictionaries
    override fun isValidSpellingWord(word: String): Boolean {
        mValidSpellingWordReadCache?.get(word)?.let { return it }
        val result = dictionaryGroups.any { isValidWord(word, DictionaryFacilitator.ALL_DICTIONARY_TYPES, it) }
        mValidSpellingWordReadCache?.put(word, result)
        return result
    }

    // this is unused, so leave it for now (redirecting to isValidWord seems to defeat the purpose...)
    override fun isValidSuggestionWord(word: String): Boolean {
        return isValidWord(word, DictionaryFacilitator.ALL_DICTIONARY_TYPES, dictionaryGroups[0])
    }

    // todo: move into dictionaryGroup?
    private fun isValidWord(word: String, dictionariesToCheck: Array<String>, dictionaryGroup: DictionaryGroup): Boolean {
        if (word.isEmpty()) return false
        return dictionariesToCheck.any { dictionaryGroup.getDict(it)?.isValidWord(word) == true }
    }

    override fun localesAndConfidences(): String? {
        if (dictionaryGroups.size < 2) return null
        return dictionaryGroups.joinToString(", ") { "${it.locale} ${it.confidence}" }
    }

    override fun dumpDictionaryForDebug(dictName: String) {
        val dictToDump = dictionaryGroups[0].getSubDict(dictName)
        if (dictToDump == null) {
            Log.e(TAG, ("Cannot dump $dictName. The dictionary is not being used for suggestion or cannot be dumped."))
            return
        }
        dictToDump.dumpAllWordsForDebug()
    }

    override fun getDictionaryStats(context: Context): List<DictionaryStats> =
        DictionaryFacilitator.DYNAMIC_DICTIONARY_TYPES.flatMap { dictType ->
            dictionaryGroups.mapNotNull { it.getSubDict(dictType)?.dictionaryStats }
        }

    override fun dump(context: Context) = getDictionaryStats(context).joinToString("\n")

    companion object {
        private val TAG = DictionaryFacilitatorImpl::class.java.simpleName

        private fun createSubDict(
            dictType: String, context: Context, locale: Locale, dictFile: File?, dictNamePrefix: String
        ): ExpandableBinaryDictionary? {
            try {
                return when (dictType) {
                    Dictionary.TYPE_USER -> UserBinaryDictionary.getDictionary(context, locale, dictFile, dictNamePrefix)
                    else -> throw IllegalArgumentException("unknown dictionary type $dictType")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot create dictionary: $dictType", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Cannot create dictionary: $dictType", e)
            }
            return null
        }

        private fun findDictionaryGroupWithLocale(dictGroups: List<DictionaryGroup>?, locale: Locale): DictionaryGroup? {
            return dictGroups?.firstOrNull { it.locale == locale }
        }

        private fun getUsedLocales(mainLocale: Locale, context: Context): Collection<Locale> {
            val locales = hashSetOf(mainLocale)
            // adding secondary locales is a bit tricky since they depend on the subtype
            // but usually this is called with the selected subtype locale
            val selectedSubtype = SubtypeSettings.getSelectedSubtype(context.prefs())
            if (selectedSubtype.locale() == mainLocale) {
                locales.addAll(getSecondaryLocales(selectedSubtype.extraValue))
            } else {
                // probably we're called from the spell checker when using a different app as keyboard
                // so best bet is adding all secondary locales for matching main locale
                SubtypeSettings.getEnabledSubtypes(false).forEach {
                    if (it.locale() == mainLocale)
                        locales.addAll(getSecondaryLocales(it.extraValue))
                }
            }
            return locales
        }

        /** Include at least two non-emoji, non-typed word results if possible, so that the first two shown suggestions can be non-emoji */
        private fun includeAtLeastTwoWordSuggestions(
            suggestionResults: SuggestionResults,
            suggestionsArray: Array<List<SuggestedWordInfo>?>,
            typedWord: String
        ) {
            if (suggestionResults.size <= 2) return
            var nonEmojiNonTypedWordCount = 0
            suggestionResults.forEach {
                if (isEmojiOrTypedWord(it, typedWord)) return@forEach
                ++nonEmojiNonTypedWordCount
                if (nonEmojiNonTypedWordCount >= 2) return
            }
            val allResults = SuggestionResults(Int.MAX_VALUE, false, false)
            suggestionsArray.forEach {
                if (it == null) return@forEach
                allResults.addAll(it)
            }
            var addedWord: String? = null
            for (i in 0 until 2 - nonEmojiNonTypedWordCount) {
                val firstNonEmojiNonTypedWord = allResults.firstOrNull {
                    !suggestionResults.contains(it) && !isEmojiOrTypedWord(it, typedWord)
                        && addedWord?.compareTo(it.word, true) != 0
                } ?: continue
                // The conditions above guarantee that there are at least two EmojiOrTypedWord items
                val lastEmojiOrTypedWord = suggestionResults.last { isEmojiOrTypedWord(it, typedWord) }
                suggestionResults.remove(lastEmojiOrTypedWord)
                suggestionResults.add(firstNonEmojiNonTypedWord)
                addedWord = firstNonEmojiNonTypedWord.word
            }
        }

        private fun isEmojiOrTypedWord(info: SuggestedWordInfo, typedWord: String): Boolean =
            info.isEmoji || info.word.compareTo(typedWord, true) == 0
    }
}

/** A group of dictionaries that work together for a single language. */
private class DictionaryGroup(
    val locale: Locale = Locale(""),
    private var mainDict: Dictionary? = null,
    subDicts: Map<String, ExpandableBinaryDictionary> = emptyMap(),
    context: Context? = null
) {
    private val subDicts: ConcurrentHashMap<String, ExpandableBinaryDictionary> = ConcurrentHashMap(subDicts)

    // --------------- Confidence for multilingual typing -------------------

    // Confidence that the most probable language is actually the language the user is
    // typing in. For now, this is simply the number of times a word from this language
    // has been committed in a row, with an exception when typing a single word not contained
    // in this language.
    var confidence = 1

    // allow to go above max confidence, for better determination of currently preferred language
    // when decreasing confidence or getting weight factor, limit to maximum
    fun increaseConfidence() {
        confidence += 1
    }

    // If confidence is above max, drop to max confidence. This does not change weights and
    // allows conveniently typing single words not in the dictionary without affecting suggestions
    // however, when the word is valid in a different active language, this is called twice to allow for better switching
    fun decreaseConfidence() {
        if (confidence > MAX_CONFIDENCE) confidence = MAX_CONFIDENCE
        else if (confidence > 0) {
            confidence -= 1
        }
    }

    fun getWeightForLocale(groups: List<DictionaryGroup>, isGesturing: Boolean) =
        getWeightForLocale(groups, if (isGesturing) 0.05f else 0.15f)

    // might need some more tuning
    fun getWeightForLocale(groups: List<DictionaryGroup>, step: Float): Float {
        if (groups.size == 1) return 1f
        if (confidence < 2) return 1f - step * (MAX_CONFIDENCE - confidence)
        for (group in groups) {
            if (group !== this && group.confidence >= confidence) return 1f - step / 2f
        }
        return 1f
    }


    // --------------- Dictionary handling -------------------

    fun setMainDict(newMainDict: Dictionary?) {
        // Close old dictionary if exists. Main dictionary can be assigned multiple times.
        val oldDict = mainDict
        mainDict = newMainDict
        if (oldDict != null && newMainDict !== oldDict)
            oldDict.close()
    }

    fun getDict(dictType: String): Dictionary? {
        if (dictType == Dictionary.TYPE_MAIN) {
            return mainDict
        }
        return getSubDict(dictType)
    }

    fun getSubDict(dictType: String): ExpandableBinaryDictionary? {
        return subDicts[dictType]
    }

    fun hasDict(dictType: String): Boolean {
        if (dictType == Dictionary.TYPE_MAIN) {
            return mainDict != null
        }
        return subDicts.containsKey(dictType)
    }

    fun closeDict(dictType: String) {
        val dict = if (Dictionary.TYPE_MAIN == dictType) {
            mainDict
        } else {
            subDicts.remove(dictType)
        }
        dict?.close()
    }

    companion object {
        private val TAG = DictionaryGroup::class.java.simpleName
        const val MAX_CONFIDENCE = 2
    }
}
