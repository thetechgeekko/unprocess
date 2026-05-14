package com.reilandeubank.unprocess.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class HistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint().apply { color = Color.WHITE }
    private val bgPaint = Paint().apply { color = Color.argb(100, 0, 0, 0) }
    private var bins = FloatArray(32)

    fun updateHistogram(bitmap: android.graphics.Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val newBins = FloatArray(32)
        val step = maxOf(1, (w * h) / 4096) // sample ~4096 pixels
        var idx = 0
        var count = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (idx++ % step == 0) {
                    val px = bitmap.getPixel(x, y)
                    val luma = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)).toInt()
                    newBins[luma * 32 / 256]++
                    count++
                }
            }
        }
        if (count > 0) {
            val max = newBins.max().coerceAtLeast(1f)
            for (i in newBins.indices) newBins[i] /= max
        }
        bins = newBins
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val barW = width.toFloat() / bins.size
        for (i in bins.indices) {
            val barH = bins[i] * height
            canvas.drawRect(i * barW, height - barH, (i + 1) * barW, height.toFloat(), barPaint)
        }
    }
}
