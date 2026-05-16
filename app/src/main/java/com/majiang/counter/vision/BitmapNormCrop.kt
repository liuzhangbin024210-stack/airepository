package com.majiang.counter.vision

import android.graphics.Bitmap
import com.majiang.counter.profile.NormRect
import kotlin.math.max

/**
 * 按归一化矩形（相对整图 0..1）裁切 [Bitmap]；尺寸过小时返回 null。
 */
fun Bitmap.cropNormRect(n: NormRect): Bitmap? {
    val sw = width
    val sh = height
    val l = (n.left * sw).toInt().coerceIn(0, max(0, sw - 2))
    val t = (n.top * sh).toInt().coerceIn(0, max(0, sh - 2))
    val r = (n.right * sw).toInt().coerceIn(l + 1, sw)
    val b = (n.bottom * sh).toInt().coerceIn(t + 1, sh)
    val w = r - l
    val h = b - t
    if (w < 8 || h < 8) return null
    return try {
        Bitmap.createBitmap(this, l, t, w, h)
    } catch (_: IllegalArgumentException) {
        null
    }
}
