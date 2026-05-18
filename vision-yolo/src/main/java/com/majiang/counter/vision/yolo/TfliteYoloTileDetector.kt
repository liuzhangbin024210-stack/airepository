package com.majiang.counter.vision.yolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 整桌 YOLO 导出 **float32 TFLite**：加载成功且输出形状符合约定时解码 + NMS；否则 [isModelAvailable] 为 false。
 *
 * 张量约定见 **`docs/ml/yolo-tflite-contract.md`**（单输出 `[1,N,31]` 或 `[1,31,N]`，`31 = 4 + 27`）。
 */
class TfliteYoloTileDetector(
    private val context: Context,
    private val modelAssetPath: String?,
    private val inputWidth: Int,
    private val inputHeight: Int,
) : TileDetector {

    private val loadLock = Any()
    private var interpreter: Interpreter? = null
    private var modelBuffer: ByteBuffer? = null
    private var loadAttempted = false
    private var skipInterpreter = false

    override fun isModelAvailable(): Boolean = interpreterOrNull() != null

    override suspend fun detectTiles(frame: Bitmap): List<TileDetection> = withContext(Dispatchers.Default) {
        val interp = interpreterOrNull() ?: return@withContext emptyList()
        val srcW = frame.width
        val srcH = frame.height
        if (srcW < 8 || srcH < 8) return@withContext emptyList()

        val letter = letterboxToRgbFloat(frame, inputWidth, inputHeight)
        try {
            val inTensor = interp.getInputTensor(0)
            val inShape = inTensor.shape()
            val inBuf = buildInputFromLetterboxed(letter.bitmap, inShape)
            val outTensor = interp.getOutputTensor(0)
            val outShape = outTensor.shape()
            val outCount = outShape.fold(1) { a, b -> a * b }
            val outBuf = ByteBuffer.allocateDirect(4 * outCount).order(ByteOrder.nativeOrder())
            interp.run(inBuf, outBuf)
            outBuf.rewind()
            val raw = FloatArray(outCount) { outBuf.float }
            val dets = decodeYolo(raw, outShape) ?: return@withContext emptyList()
            val mapped = dets.mapNotNull { mapDetectionToFullFrame(it, letter.params, srcW, srcH) }
            nms(mapped, iouThreshold = 0.45f, maxPerClass = 8)
        } finally {
            letter.bitmap.recycle()
        }
    }

    private fun interpreterOrNull(): Interpreter? {
        if (skipInterpreter) return null
        interpreter?.let { return it }
        synchronized(loadLock) {
            if (loadAttempted) return interpreter
            loadAttempted = true
            val path = modelAssetPath?.trim().takeUnless { it.isNullOrEmpty() } ?: run {
                skipInterpreter = true
                return null
            }
            val bytes = runCatching {
                context.assets.open(path).use { it.readBytes() }
            }.getOrNull() ?: run {
                skipInterpreter = true
                return null
            }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes)
            buf.rewind()
            modelBuffer = buf
            val interp = runCatching {
                Interpreter(buf, Interpreter.Options().apply { setNumThreads(2) })
            }.getOrNull() ?: run {
                skipInterpreter = true
                return null
            }
            if (!validateOutputShape(interp.getOutputTensor(0).shape())) {
                interp.close()
                skipInterpreter = true
                interpreter = null
                return null
            }
            interpreter = interp
            return interpreter
        }
    }

    private fun validateOutputShape(shape: IntArray): Boolean {
        if (shape.size != 3 || shape[0] != 1) return false
        val d1 = shape[1]
        val d2 = shape[2]
        return (d2 == FEATURES_PER_ANCHOR && d1 > d2) ||
            (d1 == FEATURES_PER_ANCHOR && d2 > d1)
    }

    private data class LetterboxResult(val bitmap: Bitmap, val params: LetterboxParams)

    private data class LetterboxParams(
        val scale: Float,
        val padX: Float,
        val padY: Float,
        val srcW: Int,
        val srcH: Int,
    )

    private fun letterboxToRgbFloat(src: Bitmap, dstW: Int, dstH: Int): LetterboxResult {
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()
        val scale = min(dstW / sw, dstH / sh)
        val nw = sw * scale
        val nh = sh * scale
        val padX = (dstW - nw) / 2f
        val padY = (dstH - nh) / 2f
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(114, 114, 114))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val scaled = Bitmap.createScaledBitmap(
            src,
            nw.toInt().coerceAtLeast(1),
            nh.toInt().coerceAtLeast(1),
            true,
        )
        c.drawBitmap(scaled, padX, padY, paint)
        if (scaled != src) scaled.recycle()
        return LetterboxResult(
            out,
            LetterboxParams(scale, padX, padY, src.width, src.height),
        )
    }

    private fun buildInputFromLetterboxed(bmp: Bitmap, shape: IntArray): ByteBuffer {
        require(shape.size == 4 && shape[0] == 1) { "输入须为 4 维 batch=1" }
        val h = inputHeight
        val w = inputWidth
        val nchw = shape[1] == 3 && shape[2] == h && shape[3] == w
        return if (nchw) {
            fillNchw(bmp, h, w)
        } else {
            require(shape[3] == 3 && shape[1] == h && shape[2] == w) {
                "NHWC 期望 [1,$h,$w,3]，实为 ${shape.contentToString()}"
            }
            fillNhwc(bmp, h, w)
        }
    }

    private fun fillNhwc(bmp: Bitmap, h: Int, w: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(4 * h * w * 3).order(ByteOrder.nativeOrder())
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = bmp.getPixel(x, y)
                buf.putFloat(((p shr 16) and 0xff) / 255f)
                buf.putFloat(((p shr 8) and 0xff) / 255f)
                buf.putFloat((p and 0xff) / 255f)
            }
        }
        buf.rewind()
        return buf
    }

    private fun fillNchw(bmp: Bitmap, h: Int, w: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(4 * 3 * h * w).order(ByteOrder.nativeOrder())
        val fb = buf.asFloatBuffer()
        val r = FloatArray(h * w)
        val g = FloatArray(h * w)
        val bPlane = FloatArray(h * w)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = bmp.getPixel(x, y)
                r[i] = ((p shr 16) and 0xff) / 255f
                g[i] = ((p shr 8) and 0xff) / 255f
                bPlane[i] = (p and 0xff) / 255f
                i++
            }
        }
        fb.put(r)
        fb.put(g)
        fb.put(bPlane)
        buf.rewind()
        return buf
    }

    private data class RawDet(
        val cx: Float,
        val cy: Float,
        val bw: Float,
        val bh: Float,
        val classIndex: Int,
        val score: Float,
    )

    private fun decodeYolo(raw: FloatArray, shape: IntArray): List<RawDet>? {
        if (shape.size != 3 || shape[0] != 1) return null
        val d1 = shape[1]
        val d2 = shape[2]
        val n: Int
        val index: (anchor: Int, feat: Int) -> Int
        when {
            d2 == FEATURES_PER_ANCHOR && d1 > d2 -> {
                n = d1
                index = { a, f -> a * FEATURES_PER_ANCHOR + f }
            }
            d1 == FEATURES_PER_ANCHOR && d2 > d1 -> {
                n = d2
                index = { a, f -> f * n + a }
            }
            else -> return null
        }
        val out = ArrayList<RawDet>(min(n, 8192))
        for (i in 0 until n) {
            val cx = raw[index(i, 0)]
            val cy = raw[index(i, 1)]
            val wBox = raw[index(i, 2)]
            val hBox = raw[index(i, 3)]
            var bestC = 0
            var bestS = -1f
            for (c in 0 until NUM_CLASSES) {
                val s = sigmoid(raw[index(i, 4 + c)])
                if (s > bestS) {
                    bestS = s
                    bestC = c
                }
            }
            if (bestS < SCORE_THRESHOLD) continue
            out.add(RawDet(cx, cy, wBox, hBox, bestC, bestS))
        }
        return out
    }

    private fun mapDetectionToFullFrame(
        det: RawDet,
        lp: LetterboxParams,
        fullW: Int,
        fullH: Int,
    ): TileDetection? {
        fun invLetterbox(mx: Float, my: Float): Pair<Float, Float> {
            val sx = (mx - lp.padX) / lp.scale
            val sy = (my - lp.padY) / lp.scale
            return sx / fullW to sy / fullH
        }
        val x1m = det.cx - det.bw / 2f
        val y1m = det.cy - det.bh / 2f
        val x2m = det.cx + det.bw / 2f
        val y2m = det.cy + det.bh / 2f
        val (nx1, ny1) = invLetterbox(x1m, y1m)
        val (nx2, ny2) = invLetterbox(x2m, y2m)
        val l = min(nx1, nx2).coerceIn(0f, 1f)
        val t = min(ny1, ny2).coerceIn(0f, 1f)
        val r = max(nx1, nx2).coerceIn(0f, 1f)
        val b = max(ny1, ny2).coerceIn(0f, 1f)
        if (r <= l + 1e-4f || b <= t + 1e-4f) return null
        return TileDetection(
            classIndex = det.classIndex,
            confidence = det.score.coerceIn(0f, 1f),
            bboxNorm = NormalizedBBox(l, t, r, b),
        )
    }

    private fun nms(dets: List<TileDetection>, iouThreshold: Float, maxPerClass: Int): List<TileDetection> {
        if (dets.isEmpty()) return dets
        val kept = ArrayList<TileDetection>()
        for ((_, list) in dets.groupBy { it.classIndex }) {
            val sorted = list.sortedByDescending { it.confidence }
            val survive = ArrayList<TileDetection>()
            for (cand in sorted) {
                if (survive.none { iou(it.bboxNorm, cand.bboxNorm) >= iouThreshold }) {
                    survive.add(cand)
                    if (survive.size >= maxPerClass) break
                }
            }
            kept.addAll(survive)
        }
        return kept.sortedByDescending { it.confidence }
    }

    private fun iou(a: NormalizedBBox, b: NormalizedBBox): Float {
        val il = max(a.left, b.left)
        val it = max(a.top, b.top)
        val ir = min(a.right, b.right)
        val ib = min(a.bottom, b.bottom)
        val iw = (ir - il).coerceAtLeast(0f)
        val ih = (ib - it).coerceAtLeast(0f)
        val inter = iw * ih
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val u = areaA + areaB - inter
        return if (u <= 1e-6f) 0f else inter / u
    }

    private companion object {
        const val NUM_CLASSES = 27
        const val FEATURES_PER_ANCHOR = 4 + NUM_CLASSES
        const val SCORE_THRESHOLD = 0.25f

        fun sigmoid(x: Float): Float =
            (1.0 / (1.0 + exp(-x.toDouble().coerceIn(-60.0, 60.0)))).toFloat()
    }
}
