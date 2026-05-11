/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reilandeubank.unprocess.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.reilandeubank.unprocess.utils.computeExifOrientation
import com.reilandeubank.unprocess.utils.getPreviewOutputSize
import com.reilandeubank.unprocess.utils.OrientationLiveData
import com.reilandeubank.unprocess.CameraActivity
import com.reilandeubank.unprocess.R
import com.reilandeubank.unprocess.databinding.FragmentCameraBinding
import com.reilandeubank.unprocess.engine.FilmrConfig
import com.reilandeubank.unprocess.engine.FilmrEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.concurrent.TimeoutException
import java.util.Date
import java.util.Locale
import kotlin.RuntimeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore

class CameraFragment : Fragment() {

    /** Android ViewBinding */
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** AndroidX navigation arguments */
    private val args: CameraFragmentArgs by navArgs()

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /**
     * RAW_SENSOR ImageReader used for DNG archival when the user selects RAW mode.
     * Null when the device/mode doesn't use RAW capture.
     */
    private var rawImageReader: ImageReader? = null

    /**
     * JPEG ImageReader used as the source for filmr processing.
     * Always present; also used as the sole reader when not in RAW mode.
     */
    private lateinit var jpegImageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
        Runnable {
            fragmentCameraBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            fragmentCameraBinding.overlay.postDelayed({
                fragmentCameraBinding.overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentCameraBinding.captureButton.setOnApplyWindowInsetsListener { v, insets ->
            v.translationX = (-insets.systemWindowInsetRight).toFloat()
            v.translationY = (-insets.systemWindowInsetBottom).toFloat()
            insets.consumeSystemWindowInsets()
        }

        // Navigate to settings when settings button is tapped
        fragmentCameraBinding.settingsButton.setOnClickListener {
            navController.navigate(R.id.action_camera_to_settings)
        }

        updateFilmInfoBar()

        fragmentCameraBinding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize = getPreviewOutputSize(
                    fragmentCameraBinding.viewFinder.display,
                    characteristics,
                    SurfaceHolder::class.java
                )
                Log.d(TAG, "View finder size: ${fragmentCameraBinding.viewFinder.width} x ${fragmentCameraBinding.viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                fragmentCameraBinding.viewFinder.setAspectRatio(previewSize.width, previewSize.height)
                view.post { initializeCamera() }
            }
        })

        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
            })
        }
    }

    /**
     * Begin all camera operations in a coroutine in the main thread.
     */
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        camera = openCamera(cameraManager, args.cameraId, cameraHandler)

        val streamConfigMap = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )!!

        // Determine whether the device supports RAW_SENSOR and the user requested it.
        val rawOutputSizes = streamConfigMap.getOutputSizes(ImageFormat.RAW_SENSOR)
        val useRaw = args.pixelFormat == ImageFormat.RAW_SENSOR && rawOutputSizes != null

        // Always set up a JPEG reader for filmr processing.
        val jpegSize = streamConfigMap.getOutputSizes(ImageFormat.JPEG)
            .maxByOrNull { it.height * it.width }!!
        Log.d(TAG, "JPEG reader size: $jpegSize")
        jpegImageReader = ImageReader.newInstance(
            jpegSize.width, jpegSize.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE
        )

        // Optionally set up a RAW reader when the user selected RAW mode and the device supports it.
        if (useRaw) {
            val rawSize = rawOutputSizes!!.maxByOrNull { it.height * it.width }!!
            Log.d(TAG, "RAW reader size: $rawSize")
            rawImageReader = ImageReader.newInstance(
                rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, IMAGE_BUFFER_SIZE
            )
        } else if (args.pixelFormat == ImageFormat.RAW_SENSOR) {
            Log.w(TAG, "RAW_SENSOR requested but not supported by this device — falling back to JPEG only")
        }

        val targets = mutableListOf<Surface>().apply {
            add(fragmentCameraBinding.viewFinder.holder.surface)
            add(jpegImageReader.surface)
            rawImageReader?.let { add(it.surface) }
        }

        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            .apply { addTarget(fragmentCameraBinding.viewFinder.holder.surface) }
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)

        fragmentCameraBinding.captureButton.setOnClickListener {
            it.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                takePhoto().use { result ->
                    Log.d(TAG, "Result received: $result")
                    val output = saveResult(result)
                    Log.d(TAG, "Image saved: ${output.absolutePath}")
                    lifecycleScope.launch(Dispatchers.Main) {
                        navController.navigate(
                            CameraFragmentDirections
                                .actionCameraToJpegViewer(output.absolutePath)
                                .setOrientation(result.orientation)
                                .setDepth(
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                            result.format == ImageFormat.DEPTH_JPEG
                                )
                        )
                    }
                }
                it.post { it.isEnabled = true }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->
        val rawReader = rawImageReader
        val isRawCapture = rawReader != null

        // Drain any stale images from the readers before starting capture.
        @Suppress("ControlFlowWithEmptyBody")
        while (jpegImageReader.acquireNextImage() != null) {}
        if (isRawCapture) {
            @Suppress("ControlFlowWithEmptyBody")
            while (rawReader!!.acquireNextImage() != null) {}
        }

        // Use CompletableDeferred to receive each image independently.
        val jpegDeferred = CompletableDeferred<Image>()
        val rawDeferred = if (isRawCapture) CompletableDeferred<Image>() else null

        // Register listeners BEFORE firing the capture request.
        jpegImageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            if (image != null) {
                Log.d(TAG, "JPEG image available: ${image.timestamp}")
                jpegDeferred.complete(image)
            }
        }, imageReaderHandler)

        if (isRawCapture) {
            rawReader!!.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                if (image != null) {
                    Log.d(TAG, "RAW image available: ${image.timestamp}")
                    rawDeferred!!.complete(image)
                }
            }, imageReaderHandler)
        }

        // Build the capture request targeting both surfaces.
        val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            .apply {
                addTarget(jpegImageReader.surface)
                if (isRawCapture) addTarget(rawReader!!.surface)
            }

        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                fragmentCameraBinding.viewFinder.post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                Log.d(TAG, "Capture completed: ${result.get(CaptureResult.SENSOR_TIMESTAMP)}")

                // Set a timeout — if images don't arrive within the window, fail the coroutine.
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    try {
                        val jpegImage = jpegDeferred.await()
                        val rawImage = rawDeferred?.await()

                        imageReaderHandler.removeCallbacks(timeoutRunnable)

                        // Clear listeners so they don't fire for future captures.
                        jpegImageReader.setOnImageAvailableListener(null, null)
                        rawReader?.setOnImageAvailableListener(null, null)

                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)

                        // format reflects the capture mode for callers (RAW_SENSOR when dual, JPEG otherwise)
                        val format = if (isRawCapture) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
                        cont.resume(
                            CombinedCaptureResult(jpegImage, rawImage, result, exifOrientation, format)
                        )
                    } catch (e: Exception) {
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        jpegImageReader.setOnImageAvailableListener(null, null)
                        rawReader?.setOnImageAvailableListener(null, null)
                        cont.resumeWithException(e)
                    }
                }
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure
            ) {
                super.onCaptureFailed(session, request, failure)
                val exc = IOException("Capture failed: reason=${failure.reason}")
                Log.e(TAG, exc.message, exc)
                jpegImageReader.setOnImageAvailableListener(null, null)
                rawReader?.setOnImageAvailableListener(null, null)
                if (jpegDeferred.isActive) jpegDeferred.completeExceptionally(exc)
                if (rawDeferred?.isActive == true) rawDeferred.completeExceptionally(exc)
            }
        }, cameraHandler)
    }

    private suspend fun saveResult(result: CombinedCaptureResult): File = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.RAW_SENSOR -> {
                // rawImage is guaranteed non-null when format == RAW_SENSOR (see takePhoto).
                val rawImage = result.rawImage!!
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                    // Write DNG exactly once — DngCreator.writeImage may only be called once per instance.
                    dngCreator.setOrientation(result.orientation)
                    val dngStream = java.io.ByteArrayOutputStream()
                    dngCreator.writeImage(dngStream, rawImage)
                    val dngBytes = dngStream.toByteArray()

                    // Persist the original DNG bytes when the user chose "Save as RAW".
                    if (!args.convertToJpeg) {
                        saveDngBytes(dngBytes, "RAW_$timestamp.dng")
                        Log.d(TAG, "Original DNG saved")
                    }

                    // Preferred path: feed DNG bytes to filmr JNI for linear-Bayer simulation.
                    // processFromDng demosaics the RAW data before filmr processes it, which is
                    // physically correct (filmr models film chemistry in linear light).
                    val prefs = requireContext()
                        .getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                    val filmrConfig = FilmrConfig.load(prefs)

                    var bitmap: Bitmap? = FilmrEngine.processFromDng(dngBytes, filmrConfig)
                    val filmrAlreadyApplied = (bitmap != null)

                    if (bitmap == null) {
                        // Fallback: decode JPEG companion when libfilmr is absent or DNG decode fails.
                        Log.d(TAG, "processFromDng unavailable, falling back to JPEG companion")
                        val jpegPlane = result.image.planes[0]
                        val jpegBytes = ByteArray(jpegPlane.buffer.remaining())
                        jpegPlane.buffer.get(jpegBytes)
                        bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    }

                    if (bitmap == null) {
                        cont.resumeWithException(IOException("Failed to decode image from both DNG and JPEG paths"))
                        return@suspendCoroutine
                    }

                    // Apply filmr only on the JPEG fallback — the DNG path already ran it.
                    if (!filmrAlreadyApplied) bitmap = applyFilmrProcessing(bitmap)

                    val savedFile = saveJpeg(bitmap, "IMG_$timestamp.jpg", result.orientation)
                    bitmap.recycle()
                    cont.resume(savedFile)

                } catch (exc: IOException) {
                    Log.e(TAG, "Failed to save image", exc)
                    cont.resumeWithException(exc)
                } finally {
                    dngCreator.close()
                }
            }

            ImageFormat.JPEG -> {
                // Pure JPEG mode (no RAW reader configured).
                try {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val jpegPlane = result.image.planes[0]
                    val jpegBytes = ByteArray(jpegPlane.buffer.remaining())
                    jpegPlane.buffer.get(jpegBytes)
                    var bitmap: Bitmap? = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

                    if (bitmap == null) {
                        cont.resumeWithException(IOException("Failed to decode JPEG image"))
                        return@suspendCoroutine
                    }

                    bitmap = applyFilmrProcessing(bitmap)
                    val savedFile = saveJpeg(bitmap, "IMG_$timestamp.jpg", result.orientation)
                    bitmap.recycle()
                    cont.resume(savedFile)

                } catch (exc: IOException) {
                    Log.e(TAG, "Failed to save image", exc)
                    cont.resumeWithException(exc)
                }
            }

            else -> {
                val exc = RuntimeException("Unknown image format: ${result.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    /** Update the bottom film-info bar with the currently saved preset/style. */
    private fun updateFilmInfoBar() {
        val prefs = requireContext()
            .getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val config = FilmrConfig.load(prefs)
        val info = "${config.preset.manufacturer.uppercase()} · ${config.preset.displayName}"
        fragmentCameraBinding.filmInfoText.text = info
    }

    /** Apply the filmr film simulation engine to [bitmap]. Returns original on any failure. */
    private fun applyFilmrProcessing(bitmap: Bitmap): Bitmap {
        if (!FilmrEngine.isAvailable) return bitmap
        val prefs = requireContext()
            .getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val config = FilmrConfig.load(prefs)
        Log.d(TAG, "Applying filmr: preset=${config.preset.key}, style=${config.styleKey()}")
        val modelFile = java.io.File(requireContext().filesDir, "models/${FilmrEngine.DEPTH_MODEL_FILENAME}")
        return if (FilmrEngine.isDepthEstimationSupported && modelFile.exists() &&
                   (config.dofAmount > 0f || config.objectMotionAmount > 0f)) {
            Log.d(TAG, "Using depth-aware filmr processing")
            FilmrEngine.processWithDepth(bitmap, config, modelFile.absolutePath)
        } else {
            FilmrEngine.process(bitmap, config)
        }
    }

    private fun saveDngBytes(dngBytes: ByteArray, filename: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
            }
            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore DNG entry")
            resolver.openOutputStream(uri)?.use { it.write(dngBytes) }
        } else {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "Camera"
            ).apply { if (!exists()) mkdirs() }
            FileOutputStream(File(folder, filename)).use { it.write(dngBytes) }
        }
    }

    private fun saveJpeg(bitmap: Bitmap, filename: String, orientation: Int): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
            }
            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore entry")

            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                ExifInterface(pfd.fileDescriptor).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
            }

            val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            File(File(dcim, "Camera"), filename)
        } else {
            val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val folder = File(dcim, "Camera").apply { if (!exists()) mkdirs() }
            val file = File(folder, filename)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            file
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case user changed preset in settings
        if (_fragmentCameraBinding != null) updateFilmInfoBar()
    }

    override fun onStop() {
        super.onStop()
        try { camera.close() } catch (exc: Throwable) { Log.e(TAG, "Error closing camera", exc) }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
        try { jpegImageReader.close() } catch (exc: Throwable) { Log.e(TAG, "Error closing JPEG reader", exc) }
        try { rawImageReader?.close() } catch (exc: Throwable) { Log.e(TAG, "Error closing RAW reader", exc) }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /**
         * Holds the result of a still capture.
         *
         * @param image       The JPEG image (always present; used for filmr processing).
         * @param rawImage    The RAW_SENSOR image (non-null only in dual-stream RAW mode).
         * @param metadata    The [CaptureResult] from the camera.
         * @param orientation The computed EXIF orientation.
         * @param format      [ImageFormat.RAW_SENSOR] when dual-stream, [ImageFormat.JPEG] otherwise.
         */
        data class CombinedCaptureResult(
            val image: Image,
            val rawImage: Image?,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() {
                image.close()
                rawImage?.close()
            }
        }
    }
}
