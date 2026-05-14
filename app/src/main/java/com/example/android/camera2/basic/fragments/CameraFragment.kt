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
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import com.google.android.material.snackbar.Snackbar
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
import kotlinx.coroutines.withContext
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
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore

class CameraFragment : Fragment() {

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private val args: CameraFragmentArgs by navArgs()

    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    private var rawImageReader: ImageReader? = null
    private lateinit var jpegImageReader: ImageReader

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private val animationTask: Runnable by lazy {
        Runnable {
            fragmentCameraBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            fragmentCameraBinding.overlay.postDelayed({
                fragmentCameraBinding.overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null

    private lateinit var camera: CameraDevice
    private lateinit var session: CameraCaptureSession
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private var currentZoom = 1.0f
    private var zoomCropRect: Rect? = null

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
        fragmentCameraBinding.settingsButton.setOnClickListener {
            navController.navigate(R.id.action_camera_to_settings)
        }
        updateFilmInfoBar()
        fragmentCameraBinding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize = getPreviewOutputSize(
                    fragmentCameraBinding.viewFinder.display, characteristics, SurfaceHolder::class.java
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

    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        camera = openCamera(cameraManager, args.cameraId, cameraHandler)

        val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val rawOutputSizes = streamConfigMap.getOutputSizes(ImageFormat.RAW_SENSOR)
        val useRaw = args.pixelFormat == ImageFormat.RAW_SENSOR && rawOutputSizes != null

        val jpegSize = streamConfigMap.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
        Log.d(TAG, "JPEG reader size: $jpegSize")
        jpegImageReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)

        if (useRaw) {
            val rawSize = rawOutputSizes!!.maxByOrNull { it.height * it.width }!!
            Log.d(TAG, "RAW reader size: $rawSize")
            rawImageReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, IMAGE_BUFFER_SIZE)
        } else if (args.pixelFormat == ImageFormat.RAW_SENSOR) {
            Log.w(TAG, "RAW_SENSOR requested but not supported — falling back to JPEG only")
        }

        val targets = mutableListOf<Surface>().apply {
            add(fragmentCameraBinding.viewFinder.holder.surface)
            add(jpegImageReader.surface)
            rawImageReader?.let { add(it.surface) }
        }

        session = createCaptureSession(camera, targets, cameraHandler)

        previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(fragmentCameraBinding.viewFinder.holder.surface)
        }
        session.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)

        setupTouchInteractions()

        if (!FilmrEngine.isAvailable && !filmrUnavailableWarningShown) {
            filmrUnavailableWarningShown = true
            showSnackbar("Film simulation engine unavailable — photos will be saved without film processing")
        }

        fragmentCameraBinding.captureButton.setOnClickListener {
            it.isEnabled = false
            fragmentCameraBinding.captureOverlay.visibility = View.VISIBLE
            fragmentCameraBinding.captureStatusText.text = "Capturing…"
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val result = takePhoto()
                    withContext(Dispatchers.Main) {
                        fragmentCameraBinding.captureStatusText.text = "Processing…"
                    }
                    result.use { r ->
                        Log.d(TAG, "Result received: $r")
                        val output = saveResult(r)
                        Log.d(TAG, "Image saved: ${output.absolutePath}")
                        withContext(Dispatchers.Main) {
                            fragmentCameraBinding.captureOverlay.visibility = View.GONE
                            navController.navigate(
                                CameraFragmentDirections
                                    .actionCameraToJpegViewer(output.absolutePath)
                                    .setOrientation(r.orientation)
                                    .setDepth(
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                                r.format == ImageFormat.DEPTH_JPEG
                                    )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Capture failed", e)
                    withContext(Dispatchers.Main) {
                        fragmentCameraBinding.captureOverlay.visibility = View.GONE
                        it.isEnabled = true
                    }
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(fragmentCameraBinding.root, message, Snackbar.LENGTH_LONG).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchInteractions() {
        val gestureDetector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    setFocusPoint(e.x, e.y)
                    return true
                }
            })

        val scaleDetector = ScaleGestureDetector(requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val maxZoom = characteristics
                        .get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                    currentZoom = (currentZoom * detector.scaleFactor).coerceIn(1f, maxZoom)
                    updateZoom()
                    return true
                }
            })

        fragmentCameraBinding.viewFinder.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
            true
        }
    }

    private val restoreAfRunnable = Runnable {
        if (!::previewRequestBuilder.isInitialized || !::session.isInitialized) return@Runnable
        previewRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AF_REGIONS, null)
            set(CaptureRequest.CONTROL_AE_REGIONS, null)
            set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        }
        try {
            session.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore AF mode", e)
        }
    }

    private fun setFocusPoint(x: Float, y: Float) {
        val sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val viewW = fragmentCameraBinding.viewFinder.width.toFloat().coerceAtLeast(1f)
        val viewH = fragmentCameraBinding.viewFinder.height.toFloat().coerceAtLeast(1f)
        val sensorX = (x / viewW * sensorRect.width()).toInt().coerceIn(0, sensorRect.width() - 1)
        val sensorY = (y / viewH * sensorRect.height()).toInt().coerceIn(0, sensorRect.height() - 1)
        val halfSize = 150
        val left   = maxOf(0, sensorX - halfSize)
        val top    = maxOf(0, sensorY - halfSize)
        val right  = minOf(sensorRect.width(),  sensorX + halfSize)
        val bottom = minOf(sensorRect.height(), sensorY + halfSize)
        val focusRect = MeteringRectangle(left, top, right - left, bottom - top,
            MeteringRectangle.METERING_WEIGHT_MAX)

        previewRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(focusRect))
            set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(focusRect))
            set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        }
        session.capture(previewRequestBuilder.build(), null, cameraHandler)
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        session.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)

        showFocusRingAt(x, y)

        // Restore continuous AF after 3 seconds
        fragmentCameraBinding.viewFinder.removeCallbacks(restoreAfRunnable)
        fragmentCameraBinding.viewFinder.postDelayed(restoreAfRunnable, 3000)
    }

    private fun updateZoom() {
        val sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val cropW = (sensorRect.width()  / currentZoom).toInt()
        val cropH = (sensorRect.height() / currentZoom).toInt()
        val cropX = (sensorRect.width()  - cropW) / 2
        val cropY = (sensorRect.height() - cropH) / 2
        zoomCropRect = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
        previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomCropRect)
        session.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
    }

    private fun showFocusRingAt(x: Float, y: Float) {
        val ring = fragmentCameraBinding.focusRing
        val ringW = ring.width.takeIf { it > 0 } ?: 128
        val ringH = ring.height.takeIf { it > 0 } ?: 128
        ring.x = x - ringW / 2f
        ring.y = y - ringH / 2f
        ring.alpha = 1f
        ring.visibility = View.VISIBLE
        ring.animate()
            .alpha(0f)
            .setStartDelay(500)
            .setDuration(300)
            .withEndAction { ring.visibility = View.GONE }
            .start()
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager, cameraId: String, handler: Handler? = null
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
        device: CameraDevice, targets: List<Surface>, handler: Handler? = null
    ): CameraCaptureSession = suspendCancellableCoroutine { cont ->
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
        cont.invokeOnCancellation {
            // No resources to release here; the session is not yet created when cancelled
        }
    }

    private suspend fun takePhoto(): CombinedCaptureResult = suspendCancellableCoroutine { cont ->
        // Capture handlers at call time; fail fast if threads have been stopped
        val imgReaderHandler = imageReaderHandler
            ?: throw IllegalStateException("imageReaderThread not running")
        val camHandler = cameraHandler

        val rawReader = rawImageReader
        val isRawCapture = rawReader != null

        @Suppress("ControlFlowWithEmptyBody")
        while (jpegImageReader.acquireNextImage() != null) {}
        if (isRawCapture) {
            @Suppress("ControlFlowWithEmptyBody")
            while (rawReader!!.acquireNextImage() != null) {}
        }

        val jpegDeferred = CompletableDeferred<Image>()
        val rawDeferred = if (isRawCapture) CompletableDeferred<Image>() else null

        cont.invokeOnCancellation {
            jpegImageReader.setOnImageAvailableListener(null, null)
            rawReader?.setOnImageAvailableListener(null, null)
            jpegDeferred.cancel()
            rawDeferred?.cancel()
        }

        jpegImageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            if (image != null) {
                Log.d(TAG, "JPEG image available: ${image.timestamp}")
                jpegDeferred.complete(image)
            }
        }, imgReaderHandler)

        if (isRawCapture) {
            rawReader!!.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                if (image != null) {
                    Log.d(TAG, "RAW image available: ${image.timestamp}")
                    rawDeferred!!.complete(image)
                }
            }, imgReaderHandler)
        }

        val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            .apply {
                addTarget(jpegImageReader.surface)
                if (isRawCapture) addTarget(rawReader!!.surface)
                zoomCropRect?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }
            }

        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession, request: CaptureRequest,
                timestamp: Long, frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                fragmentCameraBinding.viewFinder.post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                Log.d(TAG, "Capture completed: ${result.get(CaptureResult.SENSOR_TIMESTAMP)}")

                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable {
                    jpegImageReader.setOnImageAvailableListener(null, null)
                    rawReader?.setOnImageAvailableListener(null, null)
                    if (cont.isActive) cont.resumeWithException(exc)
                }
                imgReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Consume deferred results directly on the imgReaderHandler thread
                // to avoid launching a new coroutine from the camera callback thread.
                imgReaderHandler.post {
                    try {
                        val jpegImage = jpegDeferred.getCompleted()
                        val rawImage = if (rawDeferred != null && rawDeferred.isCompleted)
                            rawDeferred.getCompleted() else null
                        imgReaderHandler.removeCallbacks(timeoutRunnable)
                        jpegImageReader.setOnImageAvailableListener(null, null)
                        rawReader?.setOnImageAvailableListener(null, null)
                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)
                        val format = if (isRawCapture) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
                        if (cont.isActive) cont.resume(
                            CombinedCaptureResult(jpegImage, rawImage, result, exifOrientation, format)
                        )
                    } catch (e: Exception) {
                        // Images not yet ready — wait for the listener callbacks to complete them.
                        // The timeout will fire if they never arrive.
                    }
                }

                // Also wire up completion of both Deferred values to resume the continuation.
                jpegDeferred.invokeOnCompletion { jpegErr ->
                    if (jpegErr != null) {
                        imgReaderHandler.removeCallbacks(timeoutRunnable)
                        jpegImageReader.setOnImageAvailableListener(null, null)
                        rawReader?.setOnImageAvailableListener(null, null)
                        if (cont.isActive) cont.resumeWithException(jpegErr)
                        return@invokeOnCompletion
                    }
                    val rawReady = rawDeferred == null || rawDeferred.isCompleted
                    if (rawReady) {
                        imgReaderHandler.removeCallbacks(timeoutRunnable)
                        jpegImageReader.setOnImageAvailableListener(null, null)
                        rawReader?.setOnImageAvailableListener(null, null)
                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)
                        val format = if (isRawCapture) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
                        if (cont.isActive) cont.resume(
                            CombinedCaptureResult(
                                jpegDeferred.getCompleted(),
                                rawDeferred?.getCompleted(),
                                result, exifOrientation, format
                            )
                        )
                    }
                }

                rawDeferred?.invokeOnCompletion { rawErr ->
                    if (rawErr != null) {
                        imgReaderHandler.removeCallbacks(timeoutRunnable)
                        jpegImageReader.setOnImageAvailableListener(null, null)
                        rawReader?.setOnImageAvailableListener(null, null)
                        if (cont.isActive) cont.resumeWithException(rawErr)
                        return@invokeOnCompletion
                    }
                    if (jpegDeferred.isCompleted) {
                        imgReaderHandler.removeCallbacks(timeoutRunnable)
                        jpegImageReader.setOnImageAvailableListener(null, null)
                        rawReader?.setOnImageAvailableListener(null, null)
                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)
                        val format = if (isRawCapture) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
                        if (cont.isActive) cont.resume(
                            CombinedCaptureResult(
                                jpegDeferred.getCompleted(),
                                rawDeferred.getCompleted(),
                                result, exifOrientation, format
                            )
                        )
                    }
                }
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure
            ) {
                super.onCaptureFailed(session, request, failure)
                val exc = IOException("Capture failed: reason=${failure.reason}")
                Log.e(TAG, exc.message, exc)
                jpegImageReader.setOnImageAvailableListener(null, null)
                rawReader?.setOnImageAvailableListener(null, null)
                if (jpegDeferred.isActive) jpegDeferred.completeExceptionally(exc)
                if (rawDeferred?.isActive == true) rawDeferred.completeExceptionally(exc)
            }
        }, camHandler)
    }

    private fun depthModelPath(config: FilmrConfig, ctx: Context = requireContext()): String {
        val modelFile = File(ctx.filesDir, "models/${FilmrEngine.DEPTH_MODEL_FILENAME}")
        return if (FilmrEngine.isDepthEstimationSupported && modelFile.exists() &&
                   (config.dofAmount > 0f || config.objectMotionAmount > 0f))
                   modelFile.absolutePath else ""
    }

    private suspend fun saveResult(result: CombinedCaptureResult): File {
        // Capture context on the calling thread before any IO dispatcher switch
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val filmrConfig = FilmrConfig.load(prefs)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        return when (result.format) {
            ImageFormat.RAW_SENSOR -> {
                val rawImage = result.rawImage!!
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    dngCreator.setOrientation(result.orientation)
                    val dngStream = java.io.ByteArrayOutputStream()
                    dngCreator.writeImage(dngStream, rawImage)
                    val dngBytes = dngStream.toByteArray()

                    if (!args.convertToJpeg) {
                        saveDngBytes(dngBytes, "RAW_$timestamp.dng", ctx)
                        Log.d(TAG, "Original DNG saved")
                    }

                    val modelPath = depthModelPath(filmrConfig, ctx)
                    var bitmap: Bitmap? = withContext(Dispatchers.IO) {
                        FilmrEngine.processFromDng(dngBytes, filmrConfig, modelPath)
                    }
                    val decoded = bitmap
                        ?: throw IOException("Film simulation failed — filmr could not process DNG. Check logcat for details.")

                    val finalBitmap = decoded

                    val savedFile = saveJpeg(finalBitmap, "IMG_$timestamp.jpg", result.orientation, filmrConfig.jpegQuality, ctx)
                    finalBitmap.recycle()
                    savedFile
                } finally {
                    dngCreator.close()
                }
            }

            else -> throw RuntimeException("Unsupported image format: ${result.format} — only RAW_SENSOR is supported")
        }
    }

    private fun updateFilmInfoBar() {
        val prefs = requireContext()
            .getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val config = FilmrConfig.load(prefs)
        fragmentCameraBinding.filmInfoText.text =
            "${config.preset.manufacturer.uppercase()} · ${config.preset.displayName}"
    }

    private fun saveDngBytes(dngBytes: ByteArray, filename: String, ctx: Context = requireContext()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore DNG entry")
            resolver.openOutputStream(uri)?.use { it.write(dngBytes) }
        } else {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"
            ).apply { if (!exists()) mkdirs() }
            FileOutputStream(File(folder, filename)).use { it.write(dngBytes) }
        }
    }

    private fun saveJpeg(bitmap: Bitmap, filename: String, orientation: Int, quality: Int = 95, ctx: Context = requireContext()): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore entry")
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
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
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            file
        }
    }

    override fun onResume() {
        super.onResume()
        // Start HandlerThreads when the fragment becomes active
        if (cameraThread == null) {
            cameraThread = HandlerThread("CameraThread").also {
                it.start()
                cameraHandler = Handler(it.looper)
            }
        }
        if (imageReaderThread == null) {
            imageReaderThread = HandlerThread("imageReaderThread").also {
                it.start()
                imageReaderHandler = Handler(it.looper)
            }
        }
        if (_fragmentCameraBinding != null) updateFilmInfoBar()
    }

    override fun onPause() {
        super.onPause()
        // Stop HandlerThreads when the fragment is paused to prevent leaks
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
        imageReaderThread?.quitSafely()
        imageReaderThread = null
        imageReaderHandler = null
    }

    override fun onStop() {
        super.onStop()
        try { camera.close() } catch (exc: Throwable) { Log.e(TAG, "Error closing camera", exc) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Threads already stopped in onPause; close image readers
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
        private var filmrUnavailableWarningShown = false

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
