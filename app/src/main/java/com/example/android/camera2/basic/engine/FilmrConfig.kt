package com.reilandeubank.unprocess.engine

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Film preset catalogue
// ---------------------------------------------------------------------------

enum class FilmPreset(
    val key: String,
    val manufacturer: String,
    val displayName: String,
    val iso: Int
) {
    // Kodak
    KODAK_PORTRA_400("KODAK_PORTRA_400", "Kodak", "Portra 400", 400),
    KODAK_PORTRA_400_ARTISTIC("KODAK_PORTRA_400_ARTISTIC", "Kodak", "Portra 400 (Artistic)", 400),
    KODAK_PORTRA_160("KODAK_PORTRA_160", "Kodak", "Portra 160", 160),
    KODAK_PORTRA_800("KODAK_PORTRA_800", "Kodak", "Portra 800", 800),
    KODAK_TRI_X_400("KODAK_TRI_X_400", "Kodak", "Tri-X 400", 400),
    KODAK_TRI_X_400_ARTISTIC("KODAK_TRI_X_400_ARTISTIC", "Kodak", "Tri-X 400 (Artistic)", 400),
    KODAK_PLUS_X_125("KODAK_PLUS_X_125", "Kodak", "Plus-X 125", 125),
    KODAK_EKTACHROME_100("KODAK_EKTACHROME_100", "Kodak", "Ektachrome 100", 100),
    KODAK_EKTACHROME_100VS("KODAK_EKTACHROME_100VS", "Kodak", "Ektachrome 100VS", 100),
    KODAK_KODACHROME_64("KODAK_KODACHROME_64", "Kodak", "Kodachrome 64", 64),
    KODAK_KODACHROME_25("KODAK_KODACHROME_25", "Kodak", "Kodachrome 25", 25),
    KODAK_GOLD_200("KODAK_GOLD_200", "Kodak", "Gold 200", 200),
    KODAK_EKTAR_100("KODAK_EKTAR_100", "Kodak", "Ektar 100", 100),
    // Fujifilm
    SUPERIA_400("SUPERIA_400", "Fujifilm", "Superia 400", 400),
    SUPERIA_200("SUPERIA_200", "Fujifilm", "Superia 200", 200),
    SUPERIA_100("SUPERIA_100", "Fujifilm", "Superia 100", 100),
    NEOPAN_400("NEOPAN_400", "Fujifilm", "Neopan 400", 400),
    NEOPAN_100("NEOPAN_100", "Fujifilm", "Neopan 100", 100),
    PROVIA_100F("PROVIA_100F", "Fujifilm", "Provia 100F", 100),
    VELVIA_50("VELVIA_50", "Fujifilm", "Velvia 50", 50),
    VELVIA_50_ARTISTIC("VELVIA_50_ARTISTIC", "Fujifilm", "Velvia 50 (Artistic)", 50),
    ASTIA_100F("ASTIA_100F", "Fujifilm", "Astia 100F", 100),
    // Ilford
    HP5_PLUS_400("HP5_PLUS_400", "Ilford", "HP5+", 400),
    HP5_PLUS_400_ARTISTIC("HP5_PLUS_400_ARTISTIC", "Ilford", "HP5+ (Artistic)", 400),
    FP4_PLUS_125("FP4_PLUS_125", "Ilford", "FP4+", 125),
    DELTA_400_PROFESSIONAL("DELTA_400_PROFESSIONAL", "Ilford", "Delta 400", 400),
    DELTA_100_PROFESSIONAL("DELTA_100_PROFESSIONAL", "Ilford", "Delta 100", 100),
    PAN_F_PLUS_50("PAN_F_PLUS_50", "Ilford", "Pan F+", 50),
    XP2_SUPER_400("XP2_SUPER_400", "Ilford", "XP2 Super 400", 400),
    SFX_200("SFX_200", "Ilford", "SFX 200", 200),
    ORTHO_PLUS_80("ORTHO_PLUS_80", "Ilford", "Ortho Plus 80", 80),
    // Agfa
    VISTA_400("VISTA_400", "Agfa", "Vista 400", 400),
    VISTA_200("VISTA_200", "Agfa", "Vista 200", 200),
    VISTA_100("VISTA_100", "Agfa", "Vista 100", 100),
    APX_400("APX_400", "Agfa", "APX 400", 400),
    APX_100("APX_100", "Agfa", "APX 100", 100),
    PRECISA_100("PRECISA_100", "Agfa", "Precisa 100", 100),
    SCALA_200("SCALA_200", "Agfa", "Scala 200", 200),
    OPTIMA_200("OPTIMA_200", "Agfa", "Optima 200", 200),
    // Polaroid
    POLAROID_600_COLOR("POLAROID_600_COLOR", "Polaroid", "600 Color", 640),
    POLAROID_SX70_COLOR("POLAROID_SX70_COLOR", "Polaroid", "SX-70 Color", 160),
    POLAROID_I_TYPE_COLOR("POLAROID_I_TYPE_COLOR", "Polaroid", "i-Type Color", 640),
    POLAROID_BW_667("POLAROID_BW_667", "Polaroid", "667 B&W", 3000),
    POLAROID_SPECTRA_COLOR("POLAROID_SPECTRA_COLOR", "Polaroid", "Spectra Color", 640),
    POLAROID_55_BW("POLAROID_55_BW", "Polaroid", "55 B&W", 50),
    // Other
    CINESTILL_800T("CINESTILL_800T", "CineStill", "800T", 800),
    CINESTILL_50D("CINESTILL_50D", "CineStill", "50D", 50),
    STANDARD_DAYLIGHT("STANDARD_DAYLIGHT", "Other", "Standard Daylight", 100);

    val label: String get() = "$manufacturer $displayName"
}

// ---------------------------------------------------------------------------
// Enums mirroring filmr Rust types
// ---------------------------------------------------------------------------

enum class SimulationMode { FAST, ACCURATE }
enum class OutputMode { NEGATIVE, POSITIVE }
enum class WhiteBalanceMode { AUTO, GRAY, WHITE, OFF }
enum class FilmStyle { ACCURATE, ARTISTIC, VINTAGE, HIGH_CONTRAST, PASTEL }

// ---------------------------------------------------------------------------
// Main config
// ---------------------------------------------------------------------------

data class FilmrConfig(
    val preset: FilmPreset = FilmPreset.KODAK_PORTRA_400,
    val filmStyle: FilmStyle = FilmStyle.ACCURATE,
    val simulationMode: SimulationMode = SimulationMode.ACCURATE,
    val exposureTime: Float = 1.0f,
    val enableGrain: Boolean = true,
    val outputMode: OutputMode = OutputMode.POSITIVE,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val whiteBalanceStrength: Float = 1.0f,
    val warmth: Float = 0.0f,
    val saturation: Float = 1.0f,
    val motionBlurAmount: Float = 0.0f,
    val dofAmount: Float = 0.0f,
    val dofFocus: Float = 0.5f,
    val dofSwirl: Float = 0.0f,
    val rotationalBlurAmount: Float = 0.0f,
    val autoLevels: Boolean = false,
    val lightLeakEnabled: Boolean = false
) {
    /** Serialize to the JSON format expected by Rust SimulationConfig. */
    fun toSimConfigJson(): String {
        val lightLeakJson = if (lightLeakEnabled) {
            """{"enabled":true,"leaks":[{"position":[0.1,0.1],"color":[1.0,0.5,0.2],"radius":0.4,"intensity":0.3,"shape":"Circle","rotation":0.0,"roughness":0.5}]}"""
        } else {
            """{"enabled":false,"leaks":[]}"""
        }
        return JSONObject().run {
            put("simulation_mode", if (simulationMode == SimulationMode.FAST) "Fast" else "Accurate")
            put("exposure_time", exposureTime.toDouble())
            put("enable_grain", enableGrain)
            put("use_gpu", false)
            put("output_mode", if (outputMode == OutputMode.POSITIVE) "Positive" else "Negative")
            put("white_balance_mode", when (whiteBalanceMode) {
                WhiteBalanceMode.AUTO -> "Auto"
                WhiteBalanceMode.GRAY -> "Gray"
                WhiteBalanceMode.WHITE -> "White"
                WhiteBalanceMode.OFF -> "Off"
            })
            put("white_balance_strength", whiteBalanceStrength.toDouble())
            put("warmth", warmth.toDouble())
            put("saturation", saturation.toDouble())
            put("light_leak", JSONObject(lightLeakJson))
            put("motion_blur_amount", motionBlurAmount.toDouble())
            put("motion_blur_seed", 42)
            put("object_motion_amount", 0.0)
            put("auto_levels", autoLevels)
            put("dof_amount", dofAmount.toDouble())
            put("dof_focus", dofFocus.toDouble())
            put("dof_swirl", dofSwirl.toDouble())
            put("rotational_blur_amount", rotationalBlurAmount.toDouble())
            toString()
        }
    }

    /** Style key passed to the Rust JNI layer. */
    fun styleKey(): String = when (filmStyle) {
        FilmStyle.ACCURATE -> "ACCURATE"
        FilmStyle.ARTISTIC -> "ARTISTIC"
        FilmStyle.VINTAGE -> "VINTAGE"
        FilmStyle.HIGH_CONTRAST -> "HIGH_CONTRAST"
        FilmStyle.PASTEL -> "PASTEL"
    }

    companion object {
        private const val PREFS_NAME = "filmr_settings"

        fun load(prefs: SharedPreferences): FilmrConfig = FilmrConfig(
            preset = FilmPreset.entries.getOrElse(
                prefs.getInt("preset_ordinal", FilmPreset.KODAK_PORTRA_400.ordinal)
            ) { FilmPreset.KODAK_PORTRA_400 },
            filmStyle = FilmStyle.entries.getOrElse(
                prefs.getInt("film_style_ordinal", FilmStyle.ACCURATE.ordinal)
            ) { FilmStyle.ACCURATE },
            simulationMode = SimulationMode.entries.getOrElse(
                prefs.getInt("simulation_mode_ordinal", SimulationMode.ACCURATE.ordinal)
            ) { SimulationMode.ACCURATE },
            exposureTime = prefs.getFloat("exposure_time", 1.0f),
            enableGrain = prefs.getBoolean("enable_grain", true),
            outputMode = OutputMode.entries.getOrElse(
                prefs.getInt("output_mode_ordinal", OutputMode.POSITIVE.ordinal)
            ) { OutputMode.POSITIVE },
            whiteBalanceMode = WhiteBalanceMode.entries.getOrElse(
                prefs.getInt("wb_mode_ordinal", WhiteBalanceMode.AUTO.ordinal)
            ) { WhiteBalanceMode.AUTO },
            whiteBalanceStrength = prefs.getFloat("wb_strength", 1.0f),
            warmth = prefs.getFloat("warmth", 0.0f),
            saturation = prefs.getFloat("saturation", 1.0f),
            motionBlurAmount = prefs.getFloat("motion_blur_amount", 0.0f),
            dofAmount = prefs.getFloat("dof_amount", 0.0f),
            dofFocus = prefs.getFloat("dof_focus", 0.5f),
            dofSwirl = prefs.getFloat("dof_swirl", 0.0f),
            rotationalBlurAmount = prefs.getFloat("rotational_blur_amount", 0.0f),
            autoLevels = prefs.getBoolean("auto_levels", false),
            lightLeakEnabled = prefs.getBoolean("light_leak_enabled", false)
        )

        fun save(config: FilmrConfig, prefs: SharedPreferences) {
            prefs.edit().apply {
                putInt("preset_ordinal", config.preset.ordinal)
                putInt("film_style_ordinal", config.filmStyle.ordinal)
                putInt("simulation_mode_ordinal", config.simulationMode.ordinal)
                putFloat("exposure_time", config.exposureTime)
                putBoolean("enable_grain", config.enableGrain)
                putInt("output_mode_ordinal", config.outputMode.ordinal)
                putInt("wb_mode_ordinal", config.whiteBalanceMode.ordinal)
                putFloat("wb_strength", config.whiteBalanceStrength)
                putFloat("warmth", config.warmth)
                putFloat("saturation", config.saturation)
                putFloat("motion_blur_amount", config.motionBlurAmount)
                putFloat("dof_amount", config.dofAmount)
                putFloat("dof_focus", config.dofFocus)
                putFloat("dof_swirl", config.dofSwirl)
                putFloat("rotational_blur_amount", config.rotationalBlurAmount)
                putBoolean("auto_levels", config.autoLevels)
                putBoolean("light_leak_enabled", config.lightLeakEnabled)
            }.apply()
        }

        const val SHARED_PREFS_NAME = "filmr_settings"
    }
}
