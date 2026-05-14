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

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.appcompat.widget.AppCompatImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.reilandeubank.unprocess.R
import com.reilandeubank.unprocess.utils.GenericListAdapter
import com.reilandeubank.unprocess.utils.decodeExifOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max


class ImageViewerFragment : Fragment() {

    /** AndroidX navigation arguments */
    private val args: ImageViewerFragmentArgs by navArgs()

    /** Default Bitmap decoding options */
    private val bitmapOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        // Keep Bitmaps at less than 1 MP
        if (max(outHeight, outWidth) > DOWNSAMPLE_SIZE) {
            val scaleFactorX = outWidth / DOWNSAMPLE_SIZE + 1
            val scaleFactorY = outHeight / DOWNSAMPLE_SIZE + 1
            inSampleSize = max(scaleFactorX, scaleFactorY)
        }
    }

    /** Bitmap transformation derived from passed arguments */
    private val bitmapTransformation: Matrix by lazy { decodeExifOrientation(args.orientation) }

    /** Flag indicating that there is depth data available for this image */
    private val isDepth: Boolean by lazy { args.depth }

    /** Data backing our Bitmap viewpager */
    private val bitmapList: MutableList<Bitmap> = mutableListOf()

    /** Reference to the ViewPager2 from the inflated layout */
    private lateinit var viewPager: ViewPager2

    /** Info bar views */
    private lateinit var infoBar: LinearLayout
    private lateinit var infoTextPrimary: TextView
    private lateinit var infoTextSecondary: TextView

    /**
     * ZoomableImageView — an ImageView that supports pinch-to-zoom via ScaleGestureDetector.
     * Scale range is 1.0x to 5.0x. Uses MATRIX scaleType to apply zoom.
     */
    inner class ZoomableImageView(context: Context) : AppCompatImageView(context) {

        private val matrix = Matrix()
        private var currentScale = 1f
        private var isScaling = false

        private val scaleGestureDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    // Disallow ViewPager2 from intercepting touch events while scaling
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    val newScale = (currentScale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                    val actualFactor = newScale / currentScale
                    currentScale = newScale

                    // Scale around the gesture focal point
                    matrix.postScale(actualFactor, actualFactor, detector.focusX, detector.focusY)
                    imageMatrix = matrix
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                    // Re-allow ViewPager2 to intercept when zoom is back to 1x
                    if (currentScale <= MIN_SCALE + 0.01f) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        )

        init {
            scaleType = ScaleType.MATRIX
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleGestureDetector.onTouchEvent(event)
            // Allow parent to handle single-finger swipes only when not scaling
            if (!isScaling && currentScale <= MIN_SCALE + 0.01f) {
                parent?.requestDisallowInterceptTouchEvent(false)
            } else {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return true
        }

        /** Reset zoom level back to 1.0x */
        fun resetZoom() {
            currentScale = MIN_SCALE
            matrix.reset()
            imageMatrix = matrix
        }
    }

    private fun imageViewFactory() = ZoomableImageView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_image_viewer, container, false)

        viewPager = root.findViewById(R.id.view_pager)
        viewPager.offscreenPageLimit = 2
        viewPager.adapter = GenericListAdapter(
            bitmapList,
            itemViewFactory = { imageViewFactory() }
        ) { view, item, _ ->
            view as ZoomableImageView
            // Reset zoom when the adapter binds a new item
            view.resetZoom()
            Glide.with(view).load(item).into(view)
        }

        // Wire up info bar views
        infoBar = root.findViewById(R.id.info_bar)
        infoTextPrimary = root.findViewById(R.id.info_text_primary)
        infoTextSecondary = root.findViewById(R.id.info_text_secondary)

        // Reset zoom on the previously displayed page when the user swipes; also hide info bar
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Reset all visible pages
                val recyclerView = viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
                recyclerView?.let { rv ->
                    for (i in 0 until rv.childCount) {
                        (rv.getChildAt(i) as? ZoomableImageView)?.resetZoom()
                    }
                }
                // Hide metadata overlay when switching pages
                infoBar.visibility = View.GONE
            }
        })

        // Gesture detector for single-tap to toggle info bar
        val gestureDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (infoBar.visibility == View.VISIBLE) {
                        infoBar.visibility = View.GONE
                    } else {
                        showInfoBar()
                    }
                    return true
                }
            }
        )

        viewPager.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        val fabShare = root.findViewById<FloatingActionButton>(R.id.fab_share)
        fabShare.setOnClickListener {
            val currentItem = viewPager.currentItem
            if (currentItem < bitmapList.size) {
                shareBitmap(bitmapList[currentItem])
            }
        }

        return root
    }

    /** Populate and show the metadata info bar for the current file */
    private fun showInfoBar() {
        val file = File(args.filePath)
        val fileName = file.name.ifEmpty { "Photo" }
        infoTextPrimary.text = fileName

        if (file.exists()) {
            val sizeMb = file.length().toDouble() / (1024.0 * 1024.0)
            val sizeStr = String.format(Locale.US, "%.1f MB", sizeMb)
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(Date(file.lastModified()))
            infoTextSecondary.text = "$sizeStr  ·  $dateStr"
        } else {
            infoTextSecondary.text = ""
        }

        infoBar.visibility = View.VISIBLE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {

            // Load input image file
            val inputBuffer = loadInputBuffer()

            // Load the main JPEG image
            addItemToViewPager(viewPager, decodeBitmap(inputBuffer, 0, inputBuffer.size))

            // If we have depth data attached, attempt to load it
            if (isDepth) {
                try {
                    val depthStart = findNextJpegEndMarker(inputBuffer, 2)
                    addItemToViewPager(
                        viewPager, decodeBitmap(
                            inputBuffer, depthStart, inputBuffer.size - depthStart
                        )
                    )

                    val confidenceStart = findNextJpegEndMarker(inputBuffer, depthStart)
                    addItemToViewPager(
                        viewPager, decodeBitmap(
                            inputBuffer, confidenceStart, inputBuffer.size - confidenceStart
                        )
                    )

                } catch (exc: RuntimeException) {
                    Log.e(TAG, "Invalid start marker for depth or confidence data")
                }
            }
        }
    }

    /** Save bitmap to a temporary file in cache and share it via ACTION_SEND */
    private fun shareBitmap(bmp: Bitmap) {
        try {
            val tempFile = saveBitmapTemp(bmp)
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                tempFile
            )
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share photo"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share image", e)
        }
    }

    /** Write the bitmap to cacheDir/share/temp_share.jpg and return the File */
    private fun saveBitmapTemp(bmp: Bitmap): File {
        val shareDir = File(requireContext().cacheDir, "share")
        shareDir.mkdirs()
        val tempFile = File(shareDir, "temp_share.jpg")
        FileOutputStream(tempFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return tempFile
    }

    /** Utility function used to read input file into a byte array */
    private fun loadInputBuffer(): ByteArray {
        val inputFile = File(args.filePath)
        return BufferedInputStream(inputFile.inputStream()).let { stream ->
            ByteArray(stream.available()).also {
                stream.read(it)
                stream.close()
            }
        }
    }

    /** Utility function used to add an item to the viewpager and notify it, in the main thread */
    private fun addItemToViewPager(view: ViewPager2, item: Bitmap) = view.post {
        bitmapList.add(item)
        view.adapter!!.notifyDataSetChanged()
    }

    /** Utility function used to decode a [Bitmap] from a byte array */
    private fun decodeBitmap(buffer: ByteArray, start: Int, length: Int): Bitmap {

        // Load bitmap from given buffer
        val bitmap = BitmapFactory.decodeByteArray(buffer, start, length, bitmapOptions)

        // Transform bitmap orientation using provided metadata
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, bitmapTransformation, true
        )
    }

    companion object {
        private val TAG = ImageViewerFragment::class.java.simpleName

        /** Maximum size of [Bitmap] decoded */
        private const val DOWNSAMPLE_SIZE: Int = 1024  // 1MP

        /** Zoom scale bounds */
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f

        /** These are the magic numbers used to separate the different JPG data chunks */
        private val JPEG_DELIMITER_BYTES = arrayOf(-1, -39)

        /**
         * Utility function used to find the markers indicating separation between JPEG data chunks
         */
        private fun findNextJpegEndMarker(jpegBuffer: ByteArray, start: Int): Int {

            // Sanitize input arguments
            assert(start >= 0) { "Invalid start marker: $start" }
            assert(jpegBuffer.size > start) {
                "Buffer size (${jpegBuffer.size}) smaller than start marker ($start)"
            }

            // Perform a linear search until the delimiter is found
            for (i in start until jpegBuffer.size - 1) {
                if (jpegBuffer[i].toInt() == JPEG_DELIMITER_BYTES[0] &&
                    jpegBuffer[i + 1].toInt() == JPEG_DELIMITER_BYTES[1]
                ) {
                    return i + 2
                }
            }

            // If we reach this, it means that no marker was found
            throw RuntimeException("Separator marker not found in buffer (${jpegBuffer.size})")
        }
    }
}
