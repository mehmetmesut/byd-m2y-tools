package com.m2y.eq

import helium314.keyboard.latin.R

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Landscape control panel for the 1920x1080 / 240 dpi BYD centre screen, in the M2Y visual language
 * (ice background, 18 dp cards, uppercase section labels, large light numerals, blue accent). Built in code.
 * M2Y Tools: two sections, "Ekolayzır" and "Klavye" (entries that open the keyboard's settings screens).
 * The equalizer has two tabs: "Bantlar" (bands, bass, loudness) and "Ses" (bass frequency, balance, surround,
 * curve intensity, headroom and a level-matched A/B switch).
 */
class MainActivity : Activity() {
    private lateinit var store: EqStore
    private lateinit var settings: EqSettings
    private lateinit var p: Palette
    private val main = Handler(Looper.getMainLooper())
    private var applyPending = false // throttle: at most one apply per APPLY_DELAY_MS while dragging
    private var dirty = false        // something changed since the last apply
    private val applyNow = Runnable {
        applyPending = false
        dirty = false
        store.save(settings)
        EqService.start(this)
    }
    private var activePreset: String? = null
    private lateinit var m2yPreset: EqSettings // the saved "M2Y" preset; the save chip lights up when settings differ
    private lateinit var saveChip: TextView
    private lateinit var resetChip: TextView
    private val headroomChips = mutableMapOf<Headroom, TextView>()
    private lateinit var matchChip: TextView

    private lateinit var enabledChip: TextView
    private val tabChips = mutableMapOf<String, TextView>()
    private val pages = mutableMapOf<String, View>()
    private var tab = TAB_EQ
    private var section = SECTION_EQ
    private val sectionChips = mutableMapOf<String, TextView>()
    private lateinit var keyboardPage: View
    private val modeChips = mutableMapOf<Int, TextView>()
    private val presetChips = mutableMapOf<String, TextView>()
    private lateinit var bands: BandsView
    private val refreshers = mutableListOf<() -> Unit>() // re-sync sliders from settings
    private val tints = HashMap<Int, ColorStateList>() // slider tint per colour, so a drag allocates none

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Palette(Palette.isNight(resources.configuration))
        store = EqStore(this)
        settings = store.load()
        m2yPreset = store.userPreset() ?: EqSettings.PRESETS.getValue(EqSettings.DEFAULT_PRESET)
        val presets = EqSettings.PRESETS + (EqSettings.DEFAULT_PRESET to m2yPreset)
        activePreset = presets.entries.firstOrNull { it.value.resampled(settings.gains.size).gains == settings.gains }?.key
        setContentView(buildUi())
        render()
        EqService.start(this)
    }

    override fun onPause() {
        main.removeCallbacks(applyNow)
        if (dirty) applyNow.run() else applyPending = false
        super.onPause()
    }

    private fun buildUi(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = p.pageBackground()
        setPadding(dp(Palette.EDGE_DP), dp(Palette.GAP_DP), dp(Palette.EDGE_DP), dp(Palette.GAP_DP))
        addView(topBar())
        pages[TAB_EQ] = eqPage()
        pages[TAB_SOUND] = soundPage()
        keyboardPage = keyboardPage()
        (pages.values + keyboardPage).forEach { addView(it, LinearLayout.LayoutParams(-1, 0, 1f).also { lp -> lp.topMargin = dp(Palette.GAP_DP) }) }
    }

    private fun topBar() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(TOP_BAR_DP)
        addView(label("M2Y", 26f, p.accent, Typeface.BOLD))
        listOf(SECTION_EQ, SECTION_KEYBOARD).forEach { name ->
            sectionChips[name] = chip(name) { section = name; render() }.also { addView(it, margins(start = Palette.GAP_DP)) }
        }
        addView(label(getString(R.string.eq_slogan), 16f, p.secondary), LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(Palette.GAP_LARGE_DP) })
        listOf(TAB_EQ, TAB_SOUND).forEach { name ->
            tabChips[name] = chip(name) { tab = name; render() }.also { addView(it, margins(start = Palette.GAP_SMALL_DP)) }
        }
        addView(chip("ⓘ") { showAbout() }, margins(start = Palette.GAP_SMALL_DP))
        enabledChip = chip("") { settings = settings.copy(enabled = !settings.enabled); changed() }
        addView(enabledChip, margins(start = Palette.GAP_LARGE_DP))
    }

    // ---- Klavye section: each entry opens that keyboard settings screen ----

    private fun keyboardPage() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        listOf(
            "Diller ve Düzenler" to SettingsDestination.Languages,
            "Kişisel sözlük" to SettingsDestination.PersonalDictionaries,
            "Klavye hakkında" to SettingsDestination.About,
        ).forEachIndexed { i, (title, destination) ->
            addView(card().apply {
                addView(label(title, 22f, p.text))
                isClickable = true
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_DESTINATION, destination))
                }
            }, LinearLayout.LayoutParams(-1, -2).also { if (i > 0) it.topMargin = dp(Palette.GAP_DP) })
        }
    }

    // ---- Ekolayzır section, Bantlar tab ----

    private fun eqPage() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(presetRow())
        addView(bandsCard(), LinearLayout.LayoutParams(-1, 0, 1f).also { it.topMargin = dp(Palette.GAP_DP) })
        addView(cardRow(
            { dbCard(it, "BAS", 0f, EqSettings.BASS_MAX_DB, { settings.bassDb }) { v -> settings = settings.copy(bassDb = v) } },
            { dbCard(it, "SES YÜKSELTME", 0f, EqSettings.LOUDNESS_MAX_DB, { settings.loudnessDb }) { v -> settings = settings.copy(loudnessDb = v) } },
        ), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(Palette.GAP_DP) })
    }

    private fun presetRow() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(caps("ÖN AYAR"))
        EqSettings.PRESETS.keys.forEach { name ->
            presetChips[name] = chip(name) { choosePreset(name) }.also { addView(it, margins(start = Palette.GAP_SMALL_DP)) }
        }
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        listOf(EqSettings.OCTAVE.size, EqSettings.FINE.size).forEach { n ->
            modeChips[n] = chip("$n bant") { settings = settings.resampled(n); changed() }.also { addView(it, margins(start = Palette.GAP_SMALL_DP)) }
        }
        saveChip = chip(getString(R.string.eq_save_preset)) {
            store.saveUserPreset(settings) // every value: bands, bass, loudness, bass frequency, balance, surround
            m2yPreset = settings
            activePreset = EqSettings.DEFAULT_PRESET
            render()
        }
        addView(saveChip, margins(start = Palette.GAP_LARGE_DP))
        resetChip = chip("↺") {
            store.clearUserPreset()
            m2yPreset = FACTORY_M2Y
            choosePreset(EqSettings.DEFAULT_PRESET) // applies the factory M2Y values right away
        }.apply { contentDescription = getString(R.string.eq_reset_preset) }
        addView(resetChip, margins(start = Palette.GAP_SMALL_DP))
    }

    private fun bandsCard() = card().apply {
        addView(caps("BANTLAR  ·  dB"))
        bands = BandsView(context, p) { i, db ->
            settings = settings.withBand(i, db)
            bands.settings = settings
            if (activePreset != null) {
                activePreset = null
                presetChips.values.forEach { styleChip(it, false) }
            }
            updateSaveChip()
            scheduleApply()
        }
        addView(bands, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun choosePreset(name: String) {
        settings = if (name == EqSettings.DEFAULT_PRESET) {
            m2yPreset.resampled(settings.gains.size).copy(enabled = settings.enabled, matchLevel = settings.matchLevel) // the user's full snapshot
        } else {
            settings.copy(gains = EqSettings.PRESETS.getValue(name).resampled(settings.gains.size).gains)
        }
        // BYD = reference: no extra processing either, so it is a fair A/B baseline (balance is a seating choice, kept)
        if (name == EqSettings.REFERENCE_PRESET) settings = settings.copy(bassDb = 0f, loudnessDb = 0f, surround = 0f)
        activePreset = name
        changed()
    }

    // ---- Ses tab ----

    private fun soundPage() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(cardRow(
            { sliderCard(it, "BAS FREKANSI", ((EqSettings.BASS_MAX_HZ - EqSettings.BASS_MIN_HZ) / HZ_STEP).toInt(),
                { i -> EqSettings.BASS_MIN_HZ + i * HZ_STEP }, { v -> ((v - EqSettings.BASS_MIN_HZ) / HZ_STEP).roundToInt() },
                { v -> "${v.toInt()} Hz" }, "Infinity subwoofer'ı için 60–90 Hz önerilir", { settings.bassHz }) { v -> settings = settings.copy(bassHz = v) } },
            { sliderCard(it, "DENGE", BALANCE_STEPS * 2, { i -> (i - BALANCE_STEPS) / BALANCE_STEPS.toFloat() },
                { v -> (v * BALANCE_STEPS).roundToInt() + BALANCE_STEPS }, ::formatBalance, "Sol ← → Sağ", { settings.balance }) { v -> settings = settings.copy(balance = v) } },
        ), LinearLayout.LayoutParams(-1, -2))
        addView(cardRow(
            { sliderCard(it, "SANAL SURROUND", PERCENT, { i -> i / PERCENT.toFloat() }, { v -> (v * PERCENT).roundToInt() },
                { v -> if (v == 0f) "Kapalı" else "%${(v * PERCENT).roundToInt()}" }, "Araçta %20–40 yeterli; kapalıyken kaynak harcamaz",
                { settings.surround }) { v -> settings = settings.copy(surround = v) } },
            { sliderCard(it, "ÖN AYAR YOĞUNLUĞU", (EqSettings.INTENSITY_MAX * INTENSITY_STEPS).toInt(),
                { i -> i / INTENSITY_STEPS }, { v -> (v * INTENSITY_STEPS).roundToInt() },
                { v -> "%${(v * PERCENT).roundToInt()}" }, "Bant eğrisini yumuşatır ya da güçlendirir; bas ve ses yükseltme ayrı",
                { settings.intensity }) { v -> settings = settings.copy(intensity = v) } },
        ), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(Palette.GAP_DP) })
        addView(cardRow(::headroomCard, ::matchCard), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(Palette.GAP_DP) })
    }

    /** How much of the boost is taken back before the EQ; BALANCED is the default. */
    private fun headroomCard(parent: LinearLayout) {
        val card = card()
        card.addView(caps("SES GÜCÜ"))
        card.addView(label("Dengeli: güçlü ve temiz · Güçlü: en yüksek ses · Temiz: hiç sınırlama yok", 16f, p.secondary))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        HEADROOM_LABELS.forEachIndexed { i, (h, text) ->
            headroomChips[h] = chip(text) { settings = settings.copy(headroom = h); changed() }
                .also { row.addView(it, margins(start = if (i == 0) 0 else Palette.GAP_SMALL_DP)) }
        }
        card.addView(row, margins(top = Palette.GAP_SMALL_DP))
        parent.addView(card, LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** Level-matched A/B: Hi-Fi DSP and Referans play equally loud, so only the tone is compared. */
    private fun matchCard(parent: LinearLayout) {
        val card = card()
        card.addView(caps("ADİL KARŞILAŞTIRMA"))
        card.addView(label("Hi-Fi DSP ↔ Referans geçişinde ses düzeyi eşitlenir; karşılaştırma bitince kapatın", 16f, p.secondary))
        matchChip = chip("") { settings = settings.copy(matchLevel = !settings.matchLevel); changed() }
        card.addView(matchChip, margins(top = Palette.GAP_SMALL_DP))
        parent.addView(card, LinearLayout.LayoutParams(0, -2, 1f))
    }

    // ---- shared building blocks ----

    private fun cardRow(vararg cards: (LinearLayout) -> Unit) = LinearLayout(this).apply {
        cards.forEachIndexed { i, build ->
            if (i > 0) addView(View(context), LinearLayout.LayoutParams(dp(Palette.GAP_DP), 1))
            build(this)
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = p.rounded(p.card, dp(Palette.CARD_RADIUS_DP).toFloat())
        setPadding(dp(Palette.GAP_LARGE_DP), dp(Palette.GAP_DP), dp(Palette.GAP_LARGE_DP), dp(Palette.GAP_DP))
    }

    private fun dbCard(parent: LinearLayout, title: String, min: Float, max: Float, get: () -> Float, set: (Float) -> Unit) =
        sliderCard(parent, title, EqSettings.steps(min, max), { i -> EqSettings.progressToDb(i, min) },
            { v -> EqSettings.dbToProgress(v, min, max) }, ::formatDb, null, get, GainColors::at, set)

    private fun sliderCard(
        parent: LinearLayout, title: String, steps: Int, toValue: (Int) -> Float, toProgress: (Float) -> Int,
        format: (Float) -> String, hint: String?, get: () -> Float,
        gainColor: ((Float) -> Int)? = null, set: (Float) -> Unit,
    ) {
        val card = card()
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(caps(title), LinearLayout.LayoutParams(0, -2, 1f))
        val value = label("", 30f, p.text, light = true)
        head.addView(value)
        card.addView(head)
        if (hint != null) card.addView(label(hint, 16f, p.secondary))
        val bar = SeekBar(this).apply {
            max = steps
            progressTintList = ColorStateList.valueOf(p.accent)
            thumbTintList = ColorStateList.valueOf(p.accent)
            progressBackgroundTintList = ColorStateList.valueOf(p.track)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = toValue(progress)
                    value.text = format(v)
                    tint(sb, value, gainColor?.invoke(v), v)
                    if (fromUser) { set(v); updateSaveChip(); scheduleApply() }
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(SLIDER_DP)))
        parent.addView(card, LinearLayout.LayoutParams(0, -2, 1f))
        refreshers += {
            bar.progress = toProgress(get())
            value.text = format(get())
            tint(bar, value, gainColor?.invoke(get()), get())
        }
    }

    /** dB sliders take the gain gradient colour (green at 0 → orange → red); other sliders keep the accent. */
    private fun tint(bar: SeekBar, value: TextView, color: Int?, v: Float) {
        val c = color ?: p.accent
        val list = tints.getOrPut(c) { ColorStateList.valueOf(c) }
        bar.progressTintList = list
        bar.thumbTintList = list
        value.setTextColor(p.text)
    }

    private fun changed() {
        render()
        scheduleApply()
    }

    private fun render() {
        styleChip(enabledChip, settings.enabled)
        enabledChip.text = if (settings.enabled) "Hi-Fi DSP" else "Referans"
        val eq = section == SECTION_EQ
        sectionChips.forEach { (name, chip) -> styleChip(chip, name == section) }
        tabChips.forEach { (name, chip) -> styleChip(chip, name == tab); chip.visibility = if (eq) View.VISIBLE else View.GONE }
        enabledChip.visibility = if (eq) View.VISIBLE else View.GONE
        pages.forEach { (name, page) -> page.visibility = if (eq && name == tab) View.VISIBLE else View.GONE }
        keyboardPage.visibility = if (eq) View.GONE else View.VISIBLE
        modeChips.forEach { (n, chip) -> styleChip(chip, settings.gains.size == n) }
        headroomChips.forEach { (h, chip) -> styleChip(chip, settings.headroom == h) }
        styleChip(matchChip, settings.matchLevel)
        matchChip.text = if (settings.matchLevel) "Eşit ses düzeyi: açık" else "Eşit ses düzeyi: kapalı"
        presetChips.forEach { (name, chip) -> styleChip(chip, name == activePreset) }
        bands.settings = settings
        refreshers.forEach { it() }
        updateSaveChip()
    }

    /** Active only while something differs from the saved M2Y preset, so the user sees there is a change to keep. */
    private fun updateSaveChip() {
        val changed = settings.differsFrom(m2yPreset)
        styleChip(saveChip, changed)
        saveChip.isEnabled = changed
        saveChip.alpha = if (changed) 1f else DISABLED_ALPHA
        // reset is offered while the saved M2Y or the current sound is not the factory M2Y
        val resettable = m2yPreset.differsFrom(FACTORY_M2Y) || settings.differsFrom(FACTORY_M2Y)
        resetChip.isEnabled = resettable
        resetChip.alpha = if (resettable) 1f else DISABLED_ALPHA
    }

    private fun scheduleApply() {
        dirty = true
        if (applyPending) return // the pending run reads the latest settings
        applyPending = true
        main.postDelayed(applyNow, APPLY_DELAY_MS)
    }

    private fun showAbout() {
        android.app.AlertDialog.Builder(this)
            .setTitle("${getString(R.string.tools_app_name)} ${packageManager.getPackageInfo(packageName, 0).versionName}")
            .setMessage(getString(R.string.eq_about_text))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun label(s: String, size: Float, color: Int, style: Int = Typeface.NORMAL, light: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = if (light) Typeface.create("sans-serif-light", style) else Typeface.create("sans-serif-medium", style)
    }

    private fun caps(s: String) = label(s, 15f, p.secondary).apply { letterSpacing = 0.08f }

    private fun chip(s: String, onClick: () -> Unit) = label(s, 16f, p.text).apply {
        gravity = Gravity.CENTER
        minHeight = dp(CHIP_DP)
        setPadding(dp(Palette.GAP_LARGE_DP), 0, dp(Palette.GAP_LARGE_DP), 0)
        isClickable = true
        setOnClickListener { onClick() }
        styleChip(this, false)
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        chip.background = p.rounded(if (selected) p.accent else p.page, dp(Palette.CHIP_RADIUS_DP).toFloat())
            .also { if (!selected) it.setStroke(dp(1), p.zeroLine) }
        chip.setTextColor(if (selected) Color.WHITE else p.text)
    }

    private fun margins(start: Int = 0, top: Int = 0) = LinearLayout.LayoutParams(-2, -2).also {
        it.marginStart = dp(start)
        it.topMargin = dp(top)
    }

    private fun formatDb(db: Float) = String.format(TR, "%+.1f dB", db)
    private fun formatBalance(b: Float) = when {
        abs(b) < 0.01f -> "Orta"
        b < 0 -> "Sol %${(abs(b) * PERCENT).roundToInt()}"
        else -> "Sağ %${(b * PERCENT).roundToInt()}"
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val SECTION_EQ = "Ekolayzır"
        const val SECTION_KEYBOARD = "Klavye"
        const val TAB_EQ = "Bantlar"
        const val TAB_SOUND = "Ses"
        const val APPLY_DELAY_MS = 150L
        const val TOP_BAR_DP = 60
        const val CHIP_DP = 56
        const val SLIDER_DP = 56
        val TR: Locale = Locale("tr")
        const val HZ_STEP = 5f
        const val BALANCE_STEPS = 20 // 5% per step each side
        const val PERCENT = 100
        const val INTENSITY_STEPS = 10f // 10 % per step
        val HEADROOM_LABELS = listOf(Headroom.CLEAN to "Temiz", Headroom.BALANCED to "Dengeli", Headroom.STRONG to "Güçlü")
        const val DISABLED_ALPHA = 0.45f
        val FACTORY_M2Y: EqSettings = EqSettings.PRESETS.getValue(EqSettings.DEFAULT_PRESET)
    }
}
