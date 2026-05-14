package com.reilandeubank.unprocess.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.reilandeubank.unprocess.FilmPreset
import com.reilandeubank.unprocess.FilmStyle
import com.reilandeubank.unprocess.FilmrConfig
import com.reilandeubank.unprocess.OutputMode
import com.reilandeubank.unprocess.SimulationMode
import com.reilandeubank.unprocess.WhiteBalanceMode

class SettingsBridge(private val context: Context) {

    private val prefs
        get() = context.getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun loadConfig(): FilmrConfig {
        val json = prefs.getString(FilmrConfig.SHARED_PREFS_NAME, null)
        return if (json != null) gson.fromJson(json, FilmrConfig::class.java) else FilmrConfig()
    }

    private fun saveConfig(config: FilmrConfig) {
        prefs.edit().putString(FilmrConfig.SHARED_PREFS_NAME, gson.toJson(config)).apply()
    }

    private fun configToJsonString(config: FilmrConfig): String {
        return buildString {
            append("{") 
            append("\"preset\":\"${config.preset.name}\",")
            append("\"filmStyle\":\"${config.filmStyle.name}\",")
            append("\"simulationMode\":\"${config.simulationMode.name}\",")
            append("\"outputMode\":\"${config.outputMode.name}\",")
            append("\"whiteBalanceMode\":\"${config.whiteBalanceMode.name}\",")
            append("\"exposureTime\":${config.exposureTime},")
            append("\"whiteBalanceStrength\":${config.whiteBalanceStrength},")
            append("\"warmth\":${config.warmth},")
            append("\"saturation\":${config.saturation},")
            append("\"objectMotionAmount\":${config.objectMotionAmount},")
            append("\"motionBlurAmount\":${config.motionBlurAmount},")
            append("\"dofAmount\":${config.dofAmount},")
            append("\"dofFocus\":${config.dofFocus},")
            append("\"dofSwirl\":${config.dofSwirl},")
            append("\"rotationalBlurAmount\":${config.rotationalBlurAmount},")
            append("\"chromaticAberrationStrength\":${config.chromaticAberrationStrength},")
            append("\"grainMultiplier\":${config.grainMultiplier},")
            append("\"vignetteMultiplier\":${config.vignetteMultiplier},")
            append("\"highlightHueShift\":${config.highlightHueShift},")
            append("\"shadowHueShift\":${config.shadowHueShift},")
            append("\"jpegQuality\":${config.jpegQuality},")
            append("\"enableGrain\":${config.enableGrain},")
            append("\"autoLevels\":${config.autoLevels},")
            append("\"lightLeakEnabled\":${config.lightLeakEnabled}")
            append("}")
        }
    }

    // ---------------------------------------------------------------------------
    // Push config to JS
    // ---------------------------------------------------------------------------

    @SuppressLint("DefaultLocale")
    fun pushConfigToJs(webView: WebView) {
        val json = configToJsonString(loadConfig())
        val escaped = json.replace("\\", "\\\\").replace("'", "\\'")
        webView.post {
            webView.evaluateJavascript("window.loadConfig('$escaped')", null)
        }
    }

    // ---------------------------------------------------------------------------
    // JavascriptInterface setters
    // ---------------------------------------------------------------------------

    @JavascriptInterface
    fun setPreset(value: String) {
        val config = loadConfig()
        runCatching { FilmPreset.valueOf(value) }.getOrNull()?.let {
            saveConfig(config.copy(preset = it))
        }
    }

    @JavascriptInterface
    fun setStyle(value: String) {
        val config = loadConfig()
        runCatching { FilmStyle.valueOf(value) }.getOrNull()?.let {
            saveConfig(config.copy(filmStyle = it))
        }
    }

    @JavascriptInterface
    fun setSimulationMode(value: String) {
        val config = loadConfig()
        runCatching { SimulationMode.valueOf(value) }.getOrNull()?.let {
            saveConfig(config.copy(simulationMode = it))
        }
    }

    @JavascriptInterface
    fun setOutputMode(value: String) {
        val config = loadConfig()
        runCatching { OutputMode.valueOf(value) }.getOrNull()?.let {
            saveConfig(config.copy(outputMode = it))
        }
    }

    @JavascriptInterface
    fun setWhiteBalance(value: String) {
        val config = loadConfig()
        runCatching { WhiteBalanceMode.valueOf(value) }.getOrNull()?.let {
            saveConfig(config.copy(whiteBalanceMode = it))
        }
    }

    @JavascriptInterface
    fun setExposureTime(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(exposureTime = value.coerceIn(0.1f, 4.0f)))
    }

    @JavascriptInterface
    fun setWbStrength(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(whiteBalanceStrength = value.coerceIn(0.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setWarmth(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(warmth = value.coerceIn(-1.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setSaturation(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(saturation = value.coerceIn(0.0f, 2.0f)))
    }

    @JavascriptInterface
    fun setObjectMotion(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(objectMotionAmount = value.coerceIn(0.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setMotionBlur(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(motionBlurAmount = value.coerceIn(0.0f, 2.0f)))
    }

    @JavascriptInterface
    fun setDofAmount(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(dofAmount = value.coerceIn(0.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setDofFocus(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(dofFocus = value.coerceIn(0.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setDofSwirl(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(dofSwirl = value.coerceIn(0.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setRotBlur(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(rotationalBlurAmount = value.coerceIn(0.0f, 2.0f)))
    }

    @JavascriptInterface
    fun setChromaticAberration(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(chromaticAberrationStrength = value.coerceIn(0.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setGrainMultiplier(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(grainMultiplier = value.coerceIn(0.0f, 2.0f)))
    }

    @JavascriptInterface
    fun setVignetteMultiplier(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(vignetteMultiplier = value.coerceIn(0.0f, 2.0f)))
    }

    @JavascriptInterface
    fun setHighlightHueShift(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(highlightHueShift = value.coerceIn(-1.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setShadowHueShift(value: Float) {
        val config = loadConfig()
        saveConfig(config.copy(shadowHueShift = value.coerceIn(-1.0f, 1.0f)))
    }

    @JavascriptInterface
    fun setJpegQuality(value: Int) {
        val config = loadConfig()
        saveConfig(config.copy(jpegQuality = value.coerceIn(60, 100)))
    }

    @JavascriptInterface
    fun setEnableGrain(value: Boolean) {
        val config = loadConfig()
        saveConfig(config.copy(enableGrain = value))
    }

    @JavascriptInterface
    fun setAutoLevels(value: Boolean) {
        val config = loadConfig()
        saveConfig(config.copy(autoLevels = value))
    }

    @JavascriptInterface
    fun setLightLeak(value: Boolean) {
        val config = loadConfig()
        saveConfig(config.copy(lightLeakEnabled = value))
    }

    @JavascriptInterface
    fun resetDefaults() {
        saveConfig(FilmrConfig())
        // pushConfigToJs cannot be called directly here because we don't hold a WebView reference;
        // the WebView will call Android.resetDefaults() and then request updated config via
        // Android.getConfig() or by reloading. WebViewSettingsFragment handles the push.
    }

    @JavascriptInterface
    fun navigateBack() {
        // Back navigation is handled by Android's back stack; this is a no-op.
    }

    @JavascriptInterface
    fun getConfig(): String = configToJsonString(loadConfig())
}
