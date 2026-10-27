package com.m2y.eq

import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How much of the boost is taken back before the EQ (input gain). */
enum class Headroom { CLEAN, BALANCED, STRONG }

/** Everything the engine writes for a setting; compared with the last write so only changed values cost a binder call. */
data class EngineState(
    val inputLeft: Float, val inputRight: Float, val bassHz: Float, val bassDb: Float, val bands: List<Float>, val enabled: Boolean,
)

/** Pure equalizer model: band layouts, presets and slider mapping. No Android types, so it is unit-tested. */
data class EqSettings(
    val gains: List<Float>,        // dB per band; 10 = octave, 25 = FINE (what the engine can really resolve)
    val bassDb: Float = 0f,        // low-shelf boost below bassHz
    val loudnessDb: Float = 0f,    // extra input gain (there is no limiter)
    val enabled: Boolean = true,
    val bassHz: Float = BASS_DEFAULT_HZ,
    val balance: Float = 0f,       // -1 = left only … +1 = right only
    val surround: Float = 0f,      // Virtualizer strength 0…1; 0 = effect not created
    val headroom: Headroom = Headroom.BALANCED,
    val intensity: Float = 1f,     // curve strength 0…2 (0 % … 200 %); scales the bands, not bass or loudness
    val matchLevel: Boolean = false, // fair A/B: the average gain is taken back so Hi-Fi and Referans sound equally loud
) {
    init {
        require(gains.size in LAYOUTS) { "band count must be one of ${LAYOUTS.keys}, got ${gains.size}" }
    }

    /** Extra input gain per channel (left, right): loudness minus the attenuation of the side we pan away from. */
    fun channelGains(): Pair<Float, Float> {
        val b = balance.coerceIn(-1f, 1f)
        return (loudnessDb - BALANCE_MAX_CUT_DB * maxOf(0f, b)) to (loudnessDb - BALANCE_MAX_CUT_DB * maxOf(0f, -b))
    }

    val centers: FloatArray get() = LAYOUTS.getValue(gains.size)

    /** Step [t] (0…1) of a smooth transition to [to]; used for the 20–50 ms profile change ramp. */
    fun lerp(to: EqSettings, t: Float): EqSettings {
        require(to.gains.size == gains.size) { "cannot blend ${gains.size} into ${to.gains.size} bands" }
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return to.copy(
            gains = gains.zip(to.gains, ::mix),
            bassDb = mix(bassDb, to.bassDb),
            loudnessDb = mix(loudnessDb, to.loudnessDb),
            balance = mix(balance, to.balance),
            intensity = mix(intensity, to.intensity),
        )
    }

    /** Any sound setting differs from [preset]; on/off and the A/B level match are comparison tools, not part of a preset. */
    fun differsFrom(preset: EqSettings): Boolean =
        copy(enabled = true, matchLevel = false) != preset.resampled(gains.size).copy(enabled = true, matchLevel = false)

    fun withBand(index: Int, db: Float) = copy(gains = gains.toMutableList().also { it[index] = clampBand(db) })

    /** Same curve on another layout: gains are interpolated on a log-frequency axis. */
    fun resampled(bands: Int): EqSettings =
        if (bands == gains.size) this else copy(gains = LAYOUTS.getValue(bands).map { interpolate(centers, gains, it, round = true) })

    /**
     * The curve the engine runs: always the FINE layout, scaled by [intensity]. A 10-band setting is spread smoothly
     * (unrounded) over it, so the frequency-domain EQ has no jumps at the wide octave band edges.
     */
    fun engineGains(): List<Float> {
        val curve = if (gains.size == FINE.size) gains else FINE.map { interpolate(centers, gains, it, round = false) }
        return curve.map { (it * intensity).coerceIn(-ENGINE_MAX_DB, ENGINE_MAX_DB) }
    }

    fun engineState(): EngineState {
        val input = inputGainDb(this)
        val (left, right) = channelGains()
        return EngineState(input + left, input + right, bassHz, bassDb, engineGains(), enabled)
    }

    /** Same settings with the engine's curve (intensity already applied); used to blend between any two settings. */
    fun toEngine(): EqSettings = copy(gains = engineGains(), intensity = 1f)

    companion object {
        val OCTAVE = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

        /**
         * Bands the engine can really tell apart. DynamicsProcessing (Android 10, frequency variant) works on FFT bins of
         * sampleRate / blockSize; with 20 ms frames at 48 kHz the block is 1024, one bin every 46,875 Hz. Below 300 Hz a
         * band per bin (47, 94 … 281 Hz), above that 1/3 octave. Every band owns at least one bin at 44,1 and 48 kHz.
         */
        val FINE = floatArrayOf(
            47f, 94f, 141f, 188f, 234f, 281f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f, 2000f,
            2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f,
        )
        val LAYOUTS = mapOf(OCTAVE.size to OCTAVE, FINE.size to FINE)

        /** The 30-band 1/3-octave grid of releases up to 1.25.07.68.0; stored settings and presets are converted from it. */
        val LEGACY_THIRD = floatArrayOf(
            25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f, 630f,
            800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f,
        )

        // Safe range is ±6 dB; the user may go further (±12), the UI then shows the value in orange.
        const val SAFE_DB = 6f
        const val BAND_MIN_DB = -12f
        const val BAND_MAX_DB = 12f
        const val ENGINE_MAX_DB = 15f // band × intensity ceiling
        const val BASS_MAX_DB = 15f
        const val LOUDNESS_MAX_DB = 15f
        const val INTENSITY_MAX = 2f

        const val BASS_DEFAULT_HZ = 80f
        const val BASS_MIN_HZ = 40f
        const val BASS_MAX_HZ = 150f
        const val BALANCE_MAX_CUT_DB = 20f

        const val STEP_DB = 0.5f
        const val TOP_CUTOFF_HZ = 20000f

        // DynamicsProcessing geometry the bands are designed for (see FINE)
        const val ENGINE_FRAME_MS = 20f
        const val ENGINE_BLOCK = 1024

        val FLAT = EqSettings(List(OCTAVE.size) { 0f })

        /** Reference profile: every band 0 dB and no extra processing; the factory Infinity DSP stays as tuned. */
        const val REFERENCE_PRESET = "BYD"

        /**
         * The sound of Flat Equalizer 6.3.5.8 (com.jazibkhan.equalizer, DynamicsProcessing mode; read from its APK) in our
         * chain. Flat gives slider k a postEq band whose cutoff is the slider's own label (31,25 … 16k, 125 → 145 Hz), so it
         * lifts the octave *below* the label; bass is preEq band 0 up to 80 Hz at 15 dB × %, loudness the limiter post-gain
         * at 20 dB × %, no input gain. At its 10 ms frame (512-point FFT, 93,75 Hz bins at 48 kHz) that is one gain per bin,
         * and overlap-add blends neighbouring bins (modelled linear in dB). That response is sampled at the FINE centres and
         * the part our own bass band adds (centres below bassHz + ½ bin) is taken out of the bands. Our loudness is input gain
         * and there is no limiter, so with STRONG headroom the level matches Flat and, like Flat, loud peaks clip.
         */
        fun fromFlat(sliders: List<Float>, bassPercent: Float, loudnessPercent: Float): EqSettings {
            require(sliders.size == FLAT_CUTOFFS.size) { "Flat has ${FLAT_CUTOFFS.size} sliders" }
            val bassDb = FLAT_BASS_MAX_DB * bassPercent / 100f
            fun stopBin(hz: Float) = (hz / FLAT_BIN_HZ).roundToInt() // DPFrequency: a band ends at round(cutoff / bin)
            fun binDb(bin: Int): Float {
                val band = FLAT_CUTOFFS.indexOfFirst { bin <= stopBin(it) } // above the last cutoff: unchanged
                return (if (band < 0) 0f else sliders[band]) + if (bin <= stopBin(BASS_DEFAULT_HZ)) bassDb else 0f
            }
            val gains = FINE.map { hz ->
                val x = hz / FLAT_BIN_HZ
                val lo = x.toInt()
                val flat = binDb(lo) + (x - lo) * (binDb(lo + 1) - binDb(lo))
                val db = flat - if (hz < BASS_DEFAULT_HZ + BIN_HZ / 2) bassDb else 0f
                (db / STEP_DB).roundToInt() * STEP_DB
            }
            return EqSettings(gains, bassDb, FLAT_LOUDNESS_MAX_DB * loudnessPercent / 100f, headroom = Headroom.STRONG)
        }
        private const val FLAT_BIN_HZ = 48000f / 512
        private const val FLAT_BASS_MAX_DB = 15f
        private const val FLAT_LOUDNESS_MAX_DB = 20f

        /** Converts a 30-band 1/3-octave curve (old storage, preset tables) to the FINE layout. */
        fun fromLegacyThird(gains: List<Float>): List<Float> {
            require(gains.size == LEGACY_THIRD.size) { "legacy curve needs ${LEGACY_THIRD.size} bands" }
            return FINE.map { interpolate(LEGACY_THIRD, gains, it, round = true) }
        }

        // Pop/Rock/Rap/Klasik/AI are written on the 1/3-octave table they were tuned on, for this car (Infinity system,
        // SUV cabin): 4–5 kHz stays ≤ +0,5 dB (A-pillar tweeters and windscreen glare), 250–315 Hz is never boosted
        // (door-panel boxiness). M2Y is the user's own Flat Equalizer setting (see fromFlat); Konuşma is an octave curve.
        private fun third(vararg from25Hz: Float) = EqSettings(fromLegacyThird(from25Hz.toList()))
        private val FLAT_CUTOFFS = floatArrayOf(31.25f, 62.5f, 145f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f) // before PRESETS
        val PRESETS: Map<String, EqSettings> = linkedMapOf(
            // the Flat curve's bands; bass and loudness set by the user (Flat would give +3 / +7 dB)
            "M2Y" to fromFlat(listOf(2f, 3f, 2f, -1f, -2f, 0f, 1f, 2f, 2f, 1f), bassPercent = 20f, loudnessPercent = 35f)
                .copy(bassDb = 2.5f, loudnessDb = 6.8f),
            REFERENCE_PRESET to FLAT,
            // "AI": transparent calibration for the SEAL U Infinity system (boot subwoofer, 4 door woofers, centre mid,
            // 4 tweeters) in a closed SUV cabin: gentle 40–80 Hz punch, 250–400 Hz boxiness cut, 2–2,5 kHz vocal clarity,
            // 4–5 kHz flat, 8–12,5 kHz air. Max ±1,5 dB at 100 %; the intensity slider makes it stronger.
            "AI" to third(-.5f, .5f, 1f, 1.5f, 1.5f, 1.5f, 1f, .5f, 0f, -.5f, -1f, -1.5f, -1f, -.5f, 0f,
                0f, 0f, .5f, .5f, 1f, 1f, .5f, 0f, 0f, .5f, 1f, 1.5f, 1.5f, 1f, 0f),
            "Pop" to third(0f, 0f, .5f, .5f, 1f, 1f, .5f, .5f, 0f, -.5f, -.5f, -.5f, 0f, 0f, 0f,
                .5f, .5f, .5f, 1f, 1f, 1.5f, 1f, .5f, 0f, 0f, .5f, 1f, 1f, .5f, 0f),
            "Rock" to third(0f, 0f, 0f, .5f, .5f, 1f, 1.5f, 1f, .5f, 0f, -.5f, -.5f, 0f, 0f, 0f,
                .5f, .5f, .5f, 1f, 1.5f, 1.5f, 1f, .5f, .5f, 0f, 0f, .5f, .5f, .5f, 0f),
            "Rap" to third(0f, 1f, 1.5f, 2f, 2f, 1.5f, 1f, .5f, 0f, -.5f, -1f, -.5f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, .5f, .5f, .5f, .5f, 0f, .5f, 1f, 1f, .5f, .5f, 0f),
            "Klasik" to third(0f, 0f, 0f, 0f, 0f, 0f, -.5f, -.5f, -.5f, -.5f, 0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, .5f, .5f, .5f, .5f, .5f, .5f, .5f, 1f, 1f, .5f, 0f),
            // Speech clarity from the centre speaker at 1–2 kHz; 250–315 Hz and 4–5 kHz kept out of the boost (car rules)
            "Konuşma" to EqSettings(listOf(-3f, -2f, -1f, -1f, 1f, 3f, 3f, .5f, 0f, -1f)),
        )

        /**
         * Input gain before the EQ (there is no limiter after it). CLEAN takes back the largest boost plus a true-peak margin
         * (quietest; loudness still adds on top); BALANCED lets the first [BALANCED_FREE_DB] of boost through, loud peaks may
         * clip; STRONG takes nothing back (as loud as other EQ apps, peaks clip). Cuts never raise the gain.
         */
        fun preampDb(s: EqSettings): Float {
            val boost = (s.engineGains() + s.bassDb).max()
            if (boost <= 0f) return 0f
            return when (s.headroom) {
                Headroom.CLEAN -> -(boost + PREAMP_MARGIN_DB)
                Headroom.BALANCED -> -maxOf(0f, boost - BALANCED_FREE_DB)
                Headroom.STRONG -> 0f
            }
        }
        const val PREAMP_MARGIN_DB = 2f
        const val BALANCED_FREE_DB = 3f

        /** What the engine sets as input gain: the headroom rule, or for a fair A/B the average gain taken back. */
        fun inputGainDb(s: EqSettings): Float =
            if (s.matchLevel) -(averageGainDb(s) + maxOf(0f, s.loudnessDb)) else preampDb(s)

        /**
         * Average gain the curve adds where music has its energy (94 Hz – 8 kHz), each band weighted by its width in
         * octaves, the bass shelf included. Used to match loudness for a fair comparison.
         */
        fun averageGainDb(s: EqSettings): Float {
            val curve = s.engineGains()
            val cut = cutoffs(FINE)
            var sum = 0f
            var weight = 0f
            for (i in 1 until FINE.size) {
                if (FINE[i] < LEVEL_LOW_HZ || FINE[i] > LEVEL_HIGH_HZ) continue
                val w = log2(cut[i] / cut[i - 1])
                // the engine's bass band takes whole bins up to round(bassHz / bin), i.e. centres below bassHz + ½ bin
                sum += w * (curve[i] + if (FINE[i] < s.bassHz + BIN_HZ / 2) s.bassDb else 0f)
                weight += w
            }
            return if (weight > 0f) sum / weight else 0f
        }
        private const val LEVEL_LOW_HZ = 90f
        private const val LEVEL_HIGH_HZ = 8000f
        private const val BIN_HZ = 48000f / ENGINE_BLOCK

        const val DEFAULT_PRESET = "M2Y"

        /** Upper cutoff of each band = geometric mean of neighbouring centres; the last band reaches 20 kHz. */
        fun cutoffs(centers: FloatArray): FloatArray = FloatArray(centers.size) { i ->
            if (i == centers.lastIndex) TOP_CUTOFF_HZ else sqrt(centers[i] * centers[i + 1])
        }

        /** Log-frequency interpolation of a curve given at [centers]; outside the range the end value holds. */
        fun interpolate(centers: FloatArray, gains: List<Float>, hz: Float, round: Boolean): Float {
            if (hz <= centers.first()) return gains.first()
            if (hz >= centers.last()) return gains.last()
            val i = centers.indexOfLast { it <= hz }
            val t = (ln(hz) - ln(centers[i])) / (ln(centers[i + 1]) - ln(centers[i]))
            val db = gains[i] + t * (gains[i + 1] - gains[i])
            return if (round) (db / STEP_DB).roundToInt() * STEP_DB else db
        }

        fun clampBand(db: Float) = db.coerceIn(BAND_MIN_DB, BAND_MAX_DB)

        /** Indices of [next] that differ from [previous]; all of them when there is no comparable previous curve. */
        fun changedBands(previous: List<Float>?, next: List<Float>): List<Int> =
            next.indices.filter { previous == null || previous.size != next.size || previous[it] != next[it] }

        /** Sliders work in integer steps; range min..max maps to 0..steps. */
        fun dbToProgress(db: Float, min: Float, max: Float) = ((db.coerceIn(min, max) - min) / STEP_DB).roundToInt()
        fun progressToDb(progress: Int, min: Float) = min + progress * STEP_DB
        fun steps(min: Float, max: Float) = ((max - min) / STEP_DB).roundToInt()

        fun label(hz: Float): String = when { // Turkish decimal comma: 31,5 · 1,25k
            hz >= 1000f -> (hz / 1000f).let { if (it % 1f == 0f) "${it.toInt()}k" else "${it}k" }
            else -> if (hz % 1f == 0f) hz.toInt().toString() else hz.toString()
        }.replace('.', ',')
    }
}
