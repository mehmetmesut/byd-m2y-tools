package com.m2y.eq

import android.content.Context
import android.content.SharedPreferences

/** Persists the active settings and the user's saved "M2Y" preset (every adjustable value) in SharedPreferences. */
class EqStore(context: Context) {
    private val prefs = context.getSharedPreferences("eq", Context.MODE_PRIVATE)

    fun load(): EqSettings {
        val fallback = userPreset() ?: EqSettings.PRESETS.getValue(EqSettings.DEFAULT_PRESET)
        val gains = prefs.getString(KEY_GAINS, null)?.let(::parseGains) ?: return fallback
        return read(gains, "").copy(enabled = prefs.getBoolean(KEY_ENABLED, true), matchLevel = prefs.getBoolean(KEY_MATCH_LEVEL, false))
    }

    fun save(s: EqSettings) = prefs.edit()
        .putString(KEY_GAINS, s.gains.joinToString(","))
        .putBoolean(KEY_ENABLED, s.enabled)
        .putBoolean(KEY_MATCH_LEVEL, s.matchLevel)
        .also { write(it, s, "") }
        .apply()

    /** The editable "M2Y" preset; null until the user saves one (built-in values are used then). Presets saved before 2.1 hold only bands. */
    fun userPreset(): EqSettings? = prefs.getString(KEY_USER_PRESET, null)?.let(::parseGains)?.let { read(it, PRESET_PREFIX) }

    fun saveUserPreset(s: EqSettings) = prefs.edit()
        .putString(KEY_USER_PRESET, s.gains.joinToString(","))
        .also { write(it, s, PRESET_PREFIX) }
        .apply()

    /** Back to the built-in "M2Y" values: the saved snapshot is removed. */
    fun clearUserPreset() = prefs.edit()
        .remove(KEY_USER_PRESET)
        .also { e -> listOf(KEY_BASS, KEY_LOUDNESS, KEY_BASS_HZ, KEY_BALANCE, KEY_SURROUND, KEY_HEADROOM, KEY_INTENSITY).forEach { e.remove(PRESET_PREFIX + it) } }
        .apply()

    private fun read(gains: List<Float>, prefix: String) = EqSettings(
        gains = gains,
        bassDb = prefs.getFloat(prefix + KEY_BASS, 0f).finiteOr0().coerceIn(0f, EqSettings.BASS_MAX_DB),
        loudnessDb = prefs.getFloat(prefix + KEY_LOUDNESS, 0f).finiteOr0().coerceIn(0f, EqSettings.LOUDNESS_MAX_DB),
        bassHz = prefs.getFloat(prefix + KEY_BASS_HZ, EqSettings.BASS_DEFAULT_HZ).finiteOr0()
            .coerceIn(EqSettings.BASS_MIN_HZ, EqSettings.BASS_MAX_HZ),
        balance = prefs.getFloat(prefix + KEY_BALANCE, 0f).finiteOr0().coerceIn(-1f, 1f),
        surround = prefs.getFloat(prefix + KEY_SURROUND, 0f).finiteOr0().coerceIn(0f, 1f),
        headroom = prefs.getString(prefix + KEY_HEADROOM, null)
            ?.let { name -> Headroom.values().firstOrNull { it.name == name } } ?: Headroom.BALANCED,
        intensity = prefs.getFloat(prefix + KEY_INTENSITY, 1f).takeIf(Float::isFinite)?.coerceIn(0f, EqSettings.INTENSITY_MAX) ?: 1f,
    )

    private fun write(e: SharedPreferences.Editor, s: EqSettings, prefix: String) {
        e.putFloat(prefix + KEY_BASS, s.bassDb)
            .putFloat(prefix + KEY_LOUDNESS, s.loudnessDb)
            .putFloat(prefix + KEY_BASS_HZ, s.bassHz)
            .putFloat(prefix + KEY_BALANCE, s.balance)
            .putFloat(prefix + KEY_SURROUND, s.surround)
            .putString(prefix + KEY_HEADROOM, s.headroom.name)
            .putFloat(prefix + KEY_INTENSITY, s.intensity)
    }

    // Stored values are our own, but a corrupted file must not crash the boot-time service.
    private fun parseGains(raw: String): List<Float>? =
        raw.split(",").mapNotNull { it.toFloatOrNull()?.takeIf(Float::isFinite)?.let(EqSettings::clampBand) }
            .let { if (it.size == LEGACY_31_BANDS) it.drop(1) else it } // 1.2–1.6 stored a 20 Hz band
            .let { if (it.size == EqSettings.LEGACY_THIRD.size) EqSettings.fromLegacyThird(it) else it } // up to 1.25.07.68.0
            .takeIf { it.size in EqSettings.LAYOUTS }

    private fun Float.finiteOr0() = if (isFinite()) this else 0f

    private companion object {
        const val KEY_GAINS = "gains"
        const val KEY_BASS = "bass"
        const val KEY_LOUDNESS = "loudness"
        const val KEY_ENABLED = "enabled"
        const val KEY_USER_PRESET = "preset_mmy"
        const val PRESET_PREFIX = "preset_mmy_"
        const val LEGACY_31_BANDS = 31
        const val KEY_BASS_HZ = "bass_hz"
        const val KEY_BALANCE = "balance"
        const val KEY_SURROUND = "surround"
        const val KEY_HEADROOM = "headroom"
        const val KEY_INTENSITY = "intensity"
        const val KEY_MATCH_LEVEL = "match_level"
    }
}
