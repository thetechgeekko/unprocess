package com.reilandeubank.unprocess.engine

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for FilmrConfig SharedPreferences round-trip.
 * Requires a real device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class FilmrConfigInstrumentedTest {

    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = context.getSharedPreferences("filmr_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun saveAndLoad_roundTripsAllFields() {
        val original = FilmrConfig(
            preset = FilmPreset.SUPERIA_400,
            filmStyle = FilmStyle.ARTISTIC,
            simulationMode = SimulationMode.FAST,
            exposureTime = 0.7f,
            enableGrain = false,
            outputMode = OutputMode.NEGATIVE,
            whiteBalanceMode = WhiteBalanceMode.GRAY,
            whiteBalanceStrength = 0.6f,
            warmth = 0.3f,
            saturation = 1.8f,
            motionBlurAmount = 0.1f,
            objectMotionAmount = 0.2f,
            dofAmount = 0.4f,
            dofFocus = 0.7f,
            dofSwirl = 0.05f,
            rotationalBlurAmount = 0.15f,
            autoLevels = true,
            lightLeakEnabled = true,
            jpegQuality = 85
        )

        FilmrConfig.save(original, prefs)
        val restored = FilmrConfig.load(prefs)

        assertEquals(original, restored)
    }

    @Test
    fun load_returnsDefaultsWhenPrefsAreEmpty() {
        val defaults = FilmrConfig()
        val loaded = FilmrConfig.load(prefs)
        assertEquals(defaults, loaded)
    }

    @Test
    fun saveAndLoad_roundTripsWithDifferentPresets() {
        FilmPreset.entries.take(5).forEach { preset ->
            prefs.edit().clear().commit()
            val config = FilmrConfig(preset = preset)
            FilmrConfig.save(config, prefs)
            assertEquals(preset, FilmrConfig.load(prefs).preset)
        }
    }

    @Test
    fun saveAndLoad_roundTripsJpegQuality() {
        val config = FilmrConfig(jpegQuality = 70)
        FilmrConfig.save(config, prefs)
        assertEquals(70, FilmrConfig.load(prefs).jpegQuality)
    }
}
