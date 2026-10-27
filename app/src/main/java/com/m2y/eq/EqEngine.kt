package com.m2y.eq

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.DynamicsProcessing.EqBand
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Per audio session: one DynamicsProcessing (per-channel input gain for headroom, loudness and balance, bass shelf,
 * FINE-layout EQ; no limiter, by the user's choice: like Flat Equalizer, peaks above full scale clip),
 * plus a Virtualizer only while surround is switched on.
 * No reverb: PresetReverb on session 0 is an auxiliary effect that ordinary players never attach to, so it is inaudible.
 * Session 0 is the global output mix; player sessions are used only if the global one is refused.
 */
class EqEngine {
    /** One audio session: the DP effect, what was last written to it, and the optional Virtualizer. */
    private class Slot(val dp: DynamicsProcessing) {
        var written: EngineState? = null
        var virtualizer: Virtualizer? = null
        var strength: Short? = null // last strength written to [virtualizer]
        var surroundRefused = false // platform has no surround here; not retried until surround is switched off and on

        fun dropVirtualizer() {
            virtualizer?.release()
            virtualizer = null
            strength = null
        }

        fun release() {
            dp.release()
            dropVirtualizer()
        }
    }

    private val slots = mutableMapOf<Int, Slot>()

    /** Creates the effects for [session] if needed and applies [s]. Returns false if the platform refused the EQ. */
    fun attach(session: Int, s: EqSettings): Boolean {
        val existed = session in slots
        return tryAttach(session, s) || (existed && tryAttach(session, s)) // a lost effect is rebuilt once right away
    }

    private fun tryAttach(session: Int, s: EqSettings): Boolean {
        val slot = slots[session] ?: try {
            Slot(DynamicsProcessing(Int.MAX_VALUE, session, config())).also { slots[session] = it }
        } catch (e: RuntimeException) {
            Log.e(TAG, "DynamicsProcessing refused for session $session", e)
            return false
        }
        return applySafely(session, slot, s)
    }

    fun detach(session: Int) {
        slots.remove(session)?.release()
    }

    /** One cheap binder read. False, with the slot dropped, when the effect died with the audio server or does not exist. */
    fun probe(session: Int): Boolean {
        val slot = slots[session] ?: return false
        return try {
            slot.dp.getInputGainByChannelIndex(0)
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "effect for session $session no longer answers", e)
            detach(session)
            false
        }
    }

    fun applyAll(s: EqSettings) {
        slots.toList().forEach { (session, slot) -> applySafely(session, slot, s) }
    }

    /**
     * An effect dies with the audio server (restart, route change) and its next call throws. The broken instance is
     * dropped and false returned, so the next apply builds a fresh one instead of crash-looping the boot-time service.
     */
    private fun applySafely(session: Int, slot: Slot, s: EqSettings): Boolean = try {
        apply(slot, s)
        applyExtras(session, slot, s)
        true
    } catch (e: RuntimeException) {
        Log.e(TAG, "effect lost for session $session, will be recreated", e)
        detach(session)
        false
    }

    fun releaseAll() {
        slots.keys.toList().forEach(::detach)
    }

    /** Writes only what differs from the last write: a slider drag changes one value, not the ~30 parameters. */
    private fun apply(slot: Slot, s: EqSettings) {
        val dp = slot.dp
        val next = s.engineState()
        val prev = slot.written
        if (next.inputLeft != prev?.inputLeft) dp.setInputGainbyChannel(0, next.inputLeft)
        if (next.inputRight != prev?.inputRight) dp.setInputGainbyChannel(1, next.inputRight)
        if (prev == null || next.bassHz != prev.bassHz || next.bassDb != prev.bassDb) {
            dp.setPreEqBandAllChannelsTo(0, EqBand(true, next.bassHz, next.bassDb))
        }
        if (prev == null) dp.setPreEqBandAllChannelsTo(1, EqBand(true, EqSettings.TOP_CUTOFF_HZ, 0f))
        EqSettings.changedBands(prev?.bands, next.bands).forEach { dp.setPostEqBandAllChannelsTo(it, EqBand(true, CUTOFFS[it], next.bands[it])) }
        if (next.enabled != prev?.enabled) dp.enabled = next.enabled
        slot.written = next
    }

    /**
     * The optional effect exists only while used, so it costs nothing when off. A refusal is logged and the Virtualizer
     * given up for this session; it never takes the EQ down with it.
     */
    private fun applyExtras(session: Int, slot: Slot, s: EqSettings) {
        try {
            if (!(s.enabled && s.surround > 0f)) {
                slot.dropVirtualizer()
                slot.surroundRefused = false
                return
            }
            if (slot.surroundRefused) return
            val v = slot.virtualizer ?: Virtualizer(Int.MAX_VALUE, session).also { slot.virtualizer = it }
            if (!v.strengthSupported) { // most speaker outputs: the effect would do nothing, so it is not kept
                slot.dropVirtualizer()
                slot.surroundRefused = true
                return
            }
            val strength = (s.surround * MAX_STRENGTH).toInt().toShort()
            if (strength == slot.strength) return
            v.setStrength(strength)
            if (slot.strength == null) v.enabled = true
            slot.strength = strength
        } catch (e: RuntimeException) {
            Log.e(TAG, "surround refused for session $session", e)
            slot.dropVirtualizer()
            slot.surroundRefused = true
        }
    }

    private fun config() = DynamicsProcessing.Config.Builder(
        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
        CHANNELS,
        true, PRE_EQ_BANDS,
        false, 0,
        true, CUTOFFS.size,
        false, // no limiter stage
    ).setPreferredFrameDuration(FRAME_MS).build()

    private companion object {
        const val TAG = "M2YEQ"
        // Always the FINE layout; 10-band settings arrive already spread over it (EqSettings.engineGains)
        val CUTOFFS = EqSettings.cutoffs(EqSettings.FINE)
        const val CHANNELS = 2
        const val PRE_EQ_BANDS = 2 // bass shelf + pass-through up to 20 kHz
        // 20 ms → 1024-point FFT at 48 kHz (46,9 Hz bins): the bass bands become separable; ~21 ms latency, same CPU
        const val FRAME_MS = EqSettings.ENGINE_FRAME_MS
        const val MAX_STRENGTH = 1000 // Virtualizer strength range 0..1000
    }
}
