package com.reilandeubank.unprocess.engine

import android.graphics.Bitmap
import android.util.Log

/**
 * JNI wrapper for the filmr film-simulation engine.
 *
 * The native library (libfilmr.so) must be present in the APK's jniLibs
 * directories.  Build it with:
 *
 *   cd filmr && ./android/build-android.sh
 *
 * If the library is absent the engine gracefully falls back to a no-op, so
 * the app remains functional without film simulation.
 */
object FilmrEngine {

    private const val TAG = "FilmrEngine"
    private var loaded = false

    init {
        loaded = try {
            System.loadLibrary("filmr")
            Log.i(TAG, "filmr native library loaded")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "filmr native library not found — film processing disabled", e)
            false
        }
    }

    val isAvailable: Boolean get() = loaded

    // ------------------------------------------------------------------
    // Native declarations
    // ------------------------------------------------------------------

    @JvmStatic
    private external fun processImage(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        presetKey: String,
        styleKey: String,
        configJson: String
    ): ByteArray?

    @JvmStatic
    private external fun getAvailablePresets(): String

    @JvmStatic
    private external fun getDefaultConfig(): String

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Apply the filmr engine to [bitmap] using [config].
     *
     * The bitmap is not mutated; a new bitmap is returned.
     * Returns [bitmap] unchanged if the native library is unavailable.
     */
    fun process(bitmap: Bitmap, config: FilmrConfig): Bitmap {
        if (!loaded) return bitmap

        val w = bitmap.width
        val h = bitmap.height

        // Extract ARGB_8888 pixel ints and convert to RGBA bytes expected by Rust
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgba = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val argb = pixels[i]
            rgba[i * 4] = ((argb shr 16) and 0xFF).toByte()  // R
            rgba[i * 4 + 1] = ((argb shr 8) and 0xFF).toByte() // G
            rgba[i * 4 + 2] = (argb and 0xFF).toByte()        // B
            rgba[i * 4 + 3] = ((argb shr 24) and 0xFF).toByte() // A (unused by filmr)
        }

        val rgb = try {
            processImage(rgba, w, h, config.preset.key, config.styleKey(), config.toSimConfigJson())
        } catch (e: Exception) {
            Log.e(TAG, "filmr processing failed", e)
            return bitmap
        } ?: return bitmap

        // Convert RGB24 back to ARGB_8888 Bitmap
        val resultPixels = IntArray(w * h)
        for (i in resultPixels.indices) {
            val r = rgb[i * 3].toInt() and 0xFF
            val g = rgb[i * 3 + 1].toInt() and 0xFF
            val b = rgb[i * 3 + 2].toInt() and 0xFF
            resultPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(resultPixels, 0, w, 0, 0, w, h)
        }
    }
}
