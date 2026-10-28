package com.m2y.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EqSettingsTest {
    private val fine = EqSettings.FINE.toList()
    private fun at(s: EqSettings, hz: Float) = s.gains[fine.indexOf(hz)]

    /**
     * Same band → FFT bin mapping as Android 10's DynamicsProcessing (DPFrequency::computeBinStartStop):
     * binStop = (int)(0.5 + cutoff * block / rate), bands take consecutive bins, a band with binStop < binStart gets none.
     */
    private fun binsPerBand(cutoffs: FloatArray, block: Int, rate: Int): List<Int> {
        var next = 0
        return cutoffs.map { c ->
            val stop = (0.5 + c * block / rate).toInt()
            val n = (minOf(stop, block / 2) - next + 1).coerceAtLeast(0)
            next = stop + 1
            n
        }
    }

    @Test fun everyFineBandOwnsAnFftBinAtBothRates() {
        val cut = EqSettings.cutoffs(EqSettings.FINE)
        for (rate in listOf(44100, 48000)) {
            val bins = binsPerBand(cut, EqSettings.ENGINE_BLOCK, rate)
            assertTrue("rate $rate: $bins", bins.all { it >= 1 })
        }
        // the frame duration really selects the 1024 block: next power of two of 20 ms worth of samples
        for (rate in listOf(44100, 48000)) {
            val desired = (EqSettings.ENGINE_FRAME_MS * rate / 1000).toInt()
            assertEquals(EqSettings.ENGINE_BLOCK, Integer.highestOneBit(desired) * 2)
        }
    }

    @Test fun changedBandsListsOnlyTheMovedOnes() {
        val a = EqSettings.PRESETS.getValue("Pop").gains
        assertEquals(a.indices.toList(), EqSettings.changedBands(null, a))
        assertEquals(a.indices.toList(), EqSettings.changedBands(listOf(0f), a)) // another layout: all
        assertEquals(emptyList<Int>(), EqSettings.changedBands(a, a))
        assertEquals(listOf(3), EqSettings.changedBands(a, a.toMutableList().also { it[3] += 0.5f }))
    }

    @Test fun engineStateSeesOnlyRealChanges() {
        val s = EqSettings.PRESETS.getValue("Pop")
        assertEquals(s.engineState(), s.copy(gains = s.gains.toList()).engineState())
        val bass = s.copy(bassDb = s.bassDb + 1f).engineState()
        assertEquals(emptyList<Int>(), EqSettings.changedBands(s.engineState().bands, bass.bands))
        assertTrue(bass.bassDb != s.engineState().bassDb)
        // balance moves one channel's input gain only
        val panned = s.copy(balance = 0.5f).engineState()
        assertEquals(s.engineState().inputRight, panned.inputRight, 0f)
        assertTrue(panned.inputLeft < s.engineState().inputLeft)
        assertFalse(s.copy(enabled = false).engineState().enabled)
    }

    @Test fun theOldThirtyBandGridLeftBandsWithoutBinsAt10ms() {
        // why the layout changed: at 10 ms (block 512) seven old bands owned no bin and did nothing
        val dead = binsPerBand(EqSettings.cutoffs(EqSettings.LEGACY_THIRD), 512, 48000).count { it == 0 }
        assertEquals(7, dead)
    }

    @Test fun cutoffsAreBetweenNeighbourCentresInBothLayouts() {
        for (centers in EqSettings.LAYOUTS.values) {
            val c = EqSettings.cutoffs(centers)
            for (i in 0 until centers.lastIndex) {
                assertTrue(c[i] > centers[i] && c[i] < centers[i + 1])
                assertTrue(c[i] < c[i + 1])
            }
            assertEquals(20000f, c.last(), 0f)
        }
        assertEquals(1414.2f, EqSettings.cutoffs(EqSettings.OCTAVE)[5], 0.1f) // sqrt(1000 * 2000)
    }

    @Test fun progressRoundTrip() {
        val min = EqSettings.BAND_MIN_DB
        val max = EqSettings.BAND_MAX_DB
        assertEquals(48, EqSettings.steps(min, max)) // ±12 dB in 0,5 dB steps
        for (db in listOf(-12f, -2.5f, 0f, 3f, 12f)) {
            assertEquals(db, EqSettings.progressToDb(EqSettings.dbToProgress(db, min, max), min), 0f)
        }
        assertEquals(48, EqSettings.dbToProgress(99f, min, max)) // clamped
    }

    @Test fun mmyPresetIsTheUsersSavedSetting() {
        // read from the car's eq.xml (preset_mmy*), 28.10.2026
        val m2y = EqSettings.PRESETS.getValue(EqSettings.DEFAULT_PRESET)
        assertEquals(listOf(2.5f, 2.5f, 2f, -.5f, -2f, 0f, 1.5f, 2f, 2f, 1f), m2y.gains)
        assertEquals(1f, m2y.bassDb, 0f)
        assertEquals(1.5f, m2y.loudnessDb, 0f)
        assertEquals(75f, m2y.bassHz, 0f)
        assertEquals(0f, m2y.balance, 0f)
        assertEquals(.05f, m2y.surround, 0f)
        assertEquals(Headroom.STRONG, m2y.headroom)
        assertEquals(1f, m2y.intensity, 0f)
    }

    @Test fun thirdOctavePresetsAreConvertedToTheFineLayout() {
        val rap = EqSettings.PRESETS.getValue("Rap")
        assertEquals(EqSettings.FINE.size, rap.gains.size)
        assertEquals(-1f, at(rap, 315f) - 0.5f, 0f)   // 315 Hz −0,5 kept as is (shared centre)
        assertEquals(1f, at(rap, 10000f), 0f)
        assertTrue(EqSettings.PRESETS.getValue(EqSettings.REFERENCE_PRESET).gains.all { it == 0f })
    }

    @Test fun legacyThirtyBandCurveConvertsWithoutLosingSharedCentres() {
        val legacy = EqSettings.LEGACY_THIRD.map { if (it == 1000f) 4f else if (it == 8000f) -3f else 0f }
        val converted = EqSettings.fromLegacyThird(legacy)
        assertEquals(EqSettings.FINE.size, converted.size)
        assertEquals(4f, converted[fine.indexOf(1000f)], 0f)
        assertEquals(-3f, converted[fine.indexOf(8000f)], 0f)
    }

    @Test fun resampleKeepsCurveAtSharedFrequencies() {
        val mmy = EqSettings.PRESETS.getValue("Konuşma") // an octave preset
        val f = mmy.resampled(EqSettings.FINE.size)
        for (hz in listOf(500f, 1000f, 2000f, 4000f, 8000f, 16000f)) { // in both layouts
            assertEquals(mmy.gains[EqSettings.OCTAVE.toList().indexOf(hz)], at(f, hz), 0f)
        }
        val back = f.resampled(10).gains
        for (i in 4 until back.size) assertEquals(mmy.gains[i], back[i], 0f)
    }

    @Test fun balanceAttenuatesOnlyTheOppositeSide() {
        val s = EqSettings.FLAT.copy(loudnessDb = 2f)
        assertEquals(2f to 2f, s.channelGains())
        assertEquals(2f to -8f, s.copy(balance = -0.5f).channelGains())
        assertEquals(-18f to 2f, s.copy(balance = 1f).channelGains())
        assertEquals(-18f to 2f, s.copy(balance = 5f).channelGains()) // clamped
    }

    @Test fun headroomModes() {
        val pop = EqSettings.PRESETS.getValue("Pop") // largest boost +1,5 dB
        assertEquals(-3.5f, EqSettings.preampDb(pop.copy(headroom = Headroom.CLEAN)), 0f)
        assertEquals(0f, EqSettings.preampDb(pop), 0f) // BALANCED is the default: first 3 dB pass
        val bass6 = EqSettings.FLAT.copy(bassDb = 6f)
        assertEquals(-8f, EqSettings.preampDb(bass6.copy(headroom = Headroom.CLEAN)), 0f)
        assertEquals(-3f, EqSettings.preampDb(bass6), 0f)
        assertEquals(0f, EqSettings.preampDb(bass6.copy(headroom = Headroom.STRONG)), 0f)
        assertEquals(0f, EqSettings.preampDb(EqSettings(List(10) { -4f }).copy(headroom = Headroom.CLEAN)), 0f) // cuts never raise
    }

    @Test fun intensityScalesTheCurveOnly() {
        val mmy = EqSettings.PRESETS.getValue("M2Y")
        val base = mmy.engineGains()
        val double = mmy.copy(intensity = 2f, bassDb = 3f).engineGains()
        base.zip(double).forEach { (a, b) -> assertEquals((a * 2).coerceIn(-15f, 15f), b, 0.001f) }
        assertTrue(mmy.copy(intensity = 0f).engineGains().all { it == 0f })
        assertEquals(15f, EqSettings(List(10) { 12f }).copy(intensity = 2f).engineGains().max(), 0f) // engine ceiling
    }

    @Test fun toEngineAppliesIntensityOnce() {
        val s = EqSettings.PRESETS.getValue("M2Y").copy(intensity = 1.5f)
        val e = s.toEngine()
        assertEquals(1f, e.intensity, 0f)
        assertEquals(s.engineGains(), e.engineGains()) // blending between engine copies does not scale twice
    }

    @Test fun levelMatchTakesBackTheAverageGain() {
        assertEquals(0f, EqSettings.averageGainDb(EqSettings.FLAT), 0f)
        val up3 = EqSettings(List(10) { 3f })
        assertEquals(3f, EqSettings.averageGainDb(up3), 0.001f)
        assertEquals(-3f, EqSettings.inputGainDb(up3.copy(matchLevel = true)), 0.001f)
        assertEquals(-5f, EqSettings.inputGainDb(up3.copy(matchLevel = true, loudnessDb = 2f)), 0.001f)
        assertEquals(EqSettings.preampDb(up3), EqSettings.inputGainDb(up3), 0f) // off: the headroom rule
        assertTrue(EqSettings.averageGainDb(EqSettings.FLAT.copy(bassDb = 6f)) in 0.1f..3f) // bass counts by its width
    }

    @Test fun everyPresetIsWithinRange() {
        EqSettings.PRESETS.values.forEach { p ->
            assertTrue(p.gains.size in EqSettings.LAYOUTS)
            assertTrue(p.gains.none { abs(it) > EqSettings.SAFE_DB })
        }
    }

    @Test fun aiProfileIsGentleAndShapedForTheCabin() {
        val ai = EqSettings.PRESETS.getValue("AI")
        assertEquals(EqSettings.FINE.size, ai.gains.size)
        assertTrue(ai.gains.all { it in -1.5f..1.5f })
        assertEquals(-1.5f, at(ai, 315f), 0f)                       // boxy cabin region cut
        assertTrue(at(ai, 4000f) <= 0f && at(ai, 5000f) <= 0f)      // no tweeter glare boost
        assertEquals(-3.5f, EqSettings.preampDb(ai.copy(headroom = Headroom.CLEAN)), 0f)
    }

    @Test fun lerpBlendsGainsAndKeepsTarget() {
        val a = EqSettings.FLAT // same layout as M2Y
        val b = EqSettings.PRESETS.getValue("M2Y").copy(loudnessDb = 4f, surround = 0.3f, intensity = 2f)
        val half = a.lerp(b, 0.5f)
        assertEquals(b.gains[0] / 2, half.gains[0], 0f)
        assertEquals(2f, half.loudnessDb, 0f)
        assertEquals(1.5f, half.intensity, 0f)
        assertEquals(0.3f, half.surround, 0f) // non-continuous fields jump to the target
        assertEquals(b, a.lerp(b, 1f))
    }

    @Test fun engineAlwaysRunsTheFineLayoutSmoothly() {
        val curve = EqSettings.PRESETS.getValue("Konuşma").engineGains()
        assertEquals(EqSettings.FINE.size, curve.size)
        assertEquals(1f, curve[fine.indexOf(500f)], 0.001f) // octave centres keep their value
        // Konuşma rises 2 dB per octave at 250 Hz–1 kHz; no FINE step may jump more than 1,7 dB
        assertTrue(curve.zipWithNext().all { (a, b) -> abs(b - a) <= 1.7f })
        val pop = EqSettings.PRESETS.getValue("Pop")
        assertEquals(pop.gains, pop.engineGains()) // FINE input is used as is at 100 %
    }

    @Test fun reviewedProfilesRespectCarRules() {
        EqSettings.PRESETS.filterKeys { it != "M2Y" }.values.map { it.engineGains() }.forEach { c ->
            assertTrue(c[fine.indexOf(5000f)] <= 0.5f)                               // no windscreen/tweeter glare
            assertTrue(c[fine.indexOf(281f)] <= 0f && c[fine.indexOf(315f)] <= 0f)  // no boxy boost
        }
    }

    @Test fun anyAdjustableChangeDiffersFromThePresetButComparisonToolsDoNot() {
        val preset = EqSettings.PRESETS.getValue("M2Y").copy(bassDb = 3f, surround = .2f)
        assertFalse(preset.differsFrom(preset))
        assertFalse(preset.copy(enabled = false).differsFrom(preset))
        assertFalse(preset.copy(matchLevel = true).differsFrom(preset))
        assertTrue(preset.copy(bassDb = 3.5f).differsFrom(preset))
        assertTrue(preset.copy(loudnessDb = .5f).differsFrom(preset))
        assertTrue(preset.copy(surround = .21f).differsFrom(preset))
        assertTrue(preset.copy(balance = .05f).differsFrom(preset))
        assertTrue(preset.copy(bassHz = 85f).differsFrom(preset))
        assertTrue(preset.copy(headroom = Headroom.CLEAN).differsFrom(preset))
        assertTrue(preset.copy(intensity = 1.1f).differsFrom(preset))
        assertTrue(preset.withBand(0, 3.5f).differsFrom(preset))
    }

    @Test fun withBandClamps() {
        assertEquals(12f, EqSettings.FLAT.withBand(0, 40f).gains[0], 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongBandCountRejected() {
        EqSettings(listOf(0f))
    }
}
