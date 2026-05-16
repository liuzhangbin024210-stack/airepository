package com.majiang.counter.vision

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * 将 [ImageProxy] 转为 [Bitmap]（**须**配合 [androidx.camera.core.ImageAnalysis] 的 RGBA 输出格式使用）。
 */
internal fun ImageProxy.toBitmapArgb8888(): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer.duplicate()
    buffer.rewind()
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val w = width
    val h = height
    val rowPadding = rowStride - pixelStride * w
    val bitmap = Bitmap.createBitmap(
        w + (rowPadding / pixelStride.coerceAtLeast(1)),
        h,
        Bitmap.Config.ARGB_8888,
    )
    bitmap.copyPixelsFromBuffer(buffer)
    return if (rowPadding == 0) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, w, h).also {
            if (it != bitmap) bitmap.recycle()
        }
    }
}
