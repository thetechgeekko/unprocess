package com.reilandeubank.unprocess.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FilmrConfigTest {

    // -----------------------------------------------------------------------
    // toSimConfigJson
    // -----------------------------------------------------------------------

    @Test
    fun `toSimConfigJson maps simulation_mode FAST`() {
        val json = JSONObject(FilmrConfig(simulationMode = SimulationMode.FAST).toSimConfigJson())
        assertEquals("Fast", json.getString("simulation_mode"))
    }

    @Test
    fun `toSimConfigJson maps simulation_mode ACCURATE`() {
        val json = JSONObject(FilmrConfig(simulationMode = SimulationMode.ACCURATE).toSimConfigJson())
        assertEquals("Accurate", json.getString("simulation_mode"))
    }

    @Test
    fun `toSimConfigJson maps all numeric fields`() {
        val config = FilmrConfig(
            exposureTime = 1.5f,
            warmth = -0.3f,
            saturation = 1.2f,
            dofAmount = 0.8f,
            dofFocus = 0.3f,
            dofSwirl = 0.1f,
            motionBlurAmount = 0.5f,
            objectMotionAmount = 0.4f,
            rotationalBlurAmount = 0.2f,
            whiteBalanceStrength = 0.9f
        )
        val json = JSONObject(config.toSimConfigJson())
        assertEquals(1.5,  json.getDouble("exposure_time"),          0.001)
        assertEquals(-0.3, json.getDouble("warmth"),                  0.001)
        assertEquals(1.2,  json.getDouble("saturation"),              0.001)
        assertEquals(0.8,  json.getDouble("dof_amount"),              0.001)
        assertEquals(0.3,  json.getDouble("dof_focus"),               0.001)
        assertEquals(0.1,  json.getDouble("dof_swirl"),               0.001)
        assertEquals(0.5,  json.getDouble("motion_blur_amount"),      0.001)
        assertEquals(0.4,  json.getDouble("object_motion_amount"),    0.001)
        assertEquals(0.2,  json.getDouble("rotational_blur_amount"),  0.001)
        assertEquals(0.9,  json.getDouble("white_balance_strength"),  0.001)
    }

    @Test
    fun `toSimConfigJson maps output_mode POSITIVE`() {
        val json = JSONObject(FilmrConfig(outputMode = OutputMode.POSITIVE).toSimConfigJson())
        assertEquals("Positive", json.getString("output_mode"))
    }

    @Test
    fun `toSimConfigJson maps output_mode NEGATIVE`() {
        val json = JSONObject(FilmrConfig(outputMode = OutputMode.NEGATIVE).toSimConfigJson())
        assertEquals("Negative", json.getString("output_mode"))
    }

    @Test
    fun `toSimConfigJson maps white_balance_mode for all values`() {
        assertEquals("Auto",  JSONObject(FilmrConfig(whiteBalanceMode = WhiteBalanceMode.AUTO).toSimConfigJson()).getString("white_balance_mode"))
        assertEquals("Gray",  JSONObject(FilmrConfig(whiteBalanceMode = WhiteBalanceMode.GRAY).toSimConfigJson()).getString("white_balance_mode"))
        assertEquals("White", JSONObject(FilmrConfig(whiteBalanceMode = WhiteBalanceMode.WHITE).toSimConfigJson()).getString("white_balance_mode"))
        assertEquals("Off",   JSONObject(FilmrConfig(whiteBalanceMode = WhiteBalanceMode.OFF).toSimConfigJson()).getString("white_balance_mode"))
    }

    @Test
    fun `toSimConfigJson maps enable_grain and auto_levels`() {
        val on = JSONObject(FilmrConfig(enableGrain = true, autoLevels = true).toSimConfigJson())
        assertTrue(on.getBoolean("enable_grain"))
        assertTrue(on.getBoolean("auto_levels"))

        val off = JSONObject(FilmrConfig(enableGrain = false, autoLevels = false).toSimConfigJson())
        assertFalse(off.getBoolean("enable_grain"))
        assertFalse(off.getBoolean("auto_levels"))
    }

    @Test
    fun `toSimConfigJson includes light_leak object`() {
        val noLeak = JSONObject(FilmrConfig(lightLeakEnabled = false).toSimConfigJson())
        assertFalse(noLeak.getJSONObject("light_leak").getBoolean("enabled"))

        val withLeak = JSONObject(FilmrConfig(lightLeakEnabled = true).toSimConfigJson())
        assertTrue(withLeak.getJSONObject("light_leak").getBoolean("enabled"))
    }

    @Test
    fun `toSimConfigJson sets use_gpu to false`() {
        val json = JSONObject(FilmrConfig().toSimConfigJson())
        assertFalse(json.getBoolean("use_gpu"))
    }

    @Test
    fun `toSimConfigJson default config is valid JSON`() {
        val json = JSONObject(FilmrConfig().toSimConfigJson())
        // Verify a selection of keys are present
        assertTrue(json.has("simulation_mode"))
        assertTrue(json.has("exposure_time"))
        assertTrue(json.has("saturation"))
        assertTrue(json.has("dof_amount"))
        assertTrue(json.has("light_leak"))
    }

    // -----------------------------------------------------------------------
    // styleKey
    // -----------------------------------------------------------------------

    @Test
    fun `styleKey returns correct string for all FilmStyle values`() {
        assertEquals("ACCURATE",      FilmrConfig(filmStyle = FilmStyle.ACCURATE).styleKey())
        assertEquals("ARTISTIC",      FilmrConfig(filmStyle = FilmStyle.ARTISTIC).styleKey())
        assertEquals("VINTAGE",       FilmrConfig(filmStyle = FilmStyle.VINTAGE).styleKey())
        assertEquals("HIGH_CONTRAST", FilmrConfig(filmStyle = FilmStyle.HIGH_CONTRAST).styleKey())
        assertEquals("PASTEL",        FilmrConfig(filmStyle = FilmStyle.PASTEL).styleKey())
    }

    // -----------------------------------------------------------------------
    // ARGB → RGBA byte-packing (mirrors FilmrEngine.processChecked logic)
    // -----------------------------------------------------------------------

    @Test
    fun `ARGB to RGBA packing extracts R G B A into correct byte positions`() {
        val r = 100; val g = 150; val b = 200; val a = 255
        val argb = (a shl 24) or (r shl 16) or (g shl 8) or b

        val rgba = ByteArray(4)
        rgba[0] = ((argb shr 16) and 0xFF).toByte()   // R
        rgba[1] = ((argb shr  8) and 0xFF).toByte()   // G
        rgba[2] = (argb          and 0xFF).toByte()   // B
        rgba[3] = ((argb shr 24) and 0xFF).toByte()   // A

        assertEquals(r, rgba[0].toInt() and 0xFF)
        assertEquals(g, rgba[1].toInt() and 0xFF)
        assertEquals(b, rgba[2].toInt() and 0xFF)
        assertEquals(a, rgba[3].toInt() and 0xFF)
    }

    @Test
    fun `ARGB to RGBA packing handles fully transparent black pixel`() {
        val argb = 0x00_00_00_00
        val rgba = ByteArray(4)
        rgba[0] = ((argb shr 16) and 0xFF).toByte()
        rgba[1] = ((argb shr  8) and 0xFF).toByte()
        rgba[2] = (argb          and 0xFF).toByte()
        rgba[3] = ((argb shr 24) and 0xFF).toByte()
        for (i in 0..3) assertEquals(0, rgba[i].toInt() and 0xFF)
    }

    @Test
    fun `ARGB to RGBA packing handles white opaque pixel`() {
        val argb = 0xFF_FF_FF_FF.toInt()
        val rgba = ByteArray(4)
        rgba[0] = ((argb shr 16) and 0xFF).toByte()
        rgba[1] = ((argb shr  8) and 0xFF).toByte()
        rgba[2] = (argb          and 0xFF).toByte()
        rgba[3] = ((argb shr 24) and 0xFF).toByte()
        for (i in 0..3) assertEquals(0xFF, rgba[i].toInt() and 0xFF)
    }
}
