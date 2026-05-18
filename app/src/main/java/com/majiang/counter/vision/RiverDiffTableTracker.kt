package com.majiang.counter.vision

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.majiang.counter.domain.DiscardEvent
import com.majiang.counter.domain.Seat
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.profile.NormRect
import com.majiang.counter.profile.RoiCalibrationPack
import com.majiang.counter.profile.VisualRoiKeys
import com.majiang.counter.vision.yolo.TileDetector
import com.majiang.counter.vision.yolo.NormalizedBBox
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

/**
 * 四牌河 ROI 的简化差分：灰度下采样后做 L1 变化量，超过阈值再调用 [TileClassifier]；若 [AppProfile.visionUseWholeTableDetector] 且 [com.majiang.counter.vision.yolo.TileDetector] 可用，则优先用整桌检测框落入河 ROI 的结果。
 *
 * **映射表落地**（`docs/画面字段-GameState-映射表.md`）：
 * - **结算类画面**：启发式检测（底部大面积「继续游戏」类暖色按钮、顶部胜利金区等）→ **不产出**弃牌事件，并清空运动历史，避免回到对局后误触发。
 * - **中央 vs 四河**：在 `extras.center_last_discard` 存在时计算中央区差分；若中央变化 **显著强于** 四河且河侧仅弱触发，则 **抑制** 本次事件，避免中央集堆/动画被误归到某一侧河 ROI。
 */
class RiverDiffTableTracker @Inject constructor(
    private val classifier: TileClassifier,
    private val tileDetector: TileDetector,
) : TableTracker {

    private val prevRiverThumb = mutableMapOf<Seat, IntArray>()
    private var prevCenterThumb: IntArray? = null

    override suspend fun processFrame(
        image: ImageProxy,
        roiPack: RoiCalibrationPack,
        profile: AppProfile,
    ): DiscardEvent? {
        val full = try {
            image.toBitmapArgb8888()
        } catch (_: Throwable) {
            return null
        }
        return try {
            processFullFrameBitmap(full, roiPack, profile)
        } finally {
            full.recycle()
        }
    }

    /**
     * 与 [processFrame] 相同逻辑，供金帧/录屏回归在本地解码 [Bitmap] 后直接调用；**不**回收 [full]（由调用方负责）。
     */
    internal suspend fun processFullFrameBitmap(
        full: Bitmap,
        roiPack: RoiCalibrationPack,
        profile: AppProfile,
    ): DiscardEvent? {
        if (isLikelySettlementScreen(full)) {
            clearMotionHistory()
            return null
        }

        val centerRect = roiPack.rectForExtra(VisualRoiKeys.CENTER_LAST_DISCARD)
        val newRiverThumbs = mutableMapOf<Seat, IntArray>()
        val riverDiff = mutableMapOf<Seat, Int>()
        for (seat in Seat.entries) {
            val rect = roiPack.rivers[seat] ?: continue
            val crop = cropNorm(full, rect) ?: continue
            val thumb = downscaleGray(crop, THUMB)
            crop.recycle()
            newRiverThumbs[seat] = thumb
            val prev = prevRiverThumb[seat]
            if (prev != null && prev.size == thumb.size) {
                var d = 0
                for (i in thumb.indices) {
                    d += abs(thumb[i] - prev[i])
                }
                riverDiff[seat] = d
            }
        }

        var centerDiff = 0
        var newCenterThumb: IntArray? = null
        if (centerRect != null) {
            val cCrop = cropNorm(full, centerRect) ?: null
            if (cCrop != null) {
                newCenterThumb = downscaleGray(cCrop, THUMB)
                cCrop.recycle()
                val prev = prevCenterThumb
                if (prev != null && prev.size == newCenterThumb.size) {
                    for (i in newCenterThumb.indices) {
                        centerDiff += abs(newCenterThumb[i] - prev[i])
                    }
                }
            }
        }

        var bestSeat: Seat? = null
        var bestDiff = motionThreshold
        for ((seat, d) in riverDiff) {
            if (d > bestDiff) {
                bestDiff = d
                bestSeat = seat
            }
        }

        if (bestSeat != null && shouldSuppressRiverDueToCenter(centerDiff, bestDiff)) {
            bestSeat = null
        }

        commitMotionHistory(newRiverThumbs, newCenterThumb)

        if (bestSeat == null) return null

        val rect = roiPack.rivers[bestSeat] ?: return null
        val rectSan = rect.sanitized()

        if (profile.visionUseWholeTableDetector && tileDetector.isModelAvailable()) {
            val dets = tileDetector.detectTiles(full)
            val inside = dets
                .filter { it.confidence >= profile.visionWholeTableDetectorMinConfidence }
                .filter { bboxCenterInNormRect(it.bboxNorm, rectSan) }
                .maxByOrNull { it.confidence }
            if (inside != null) {
                return DiscardEvent(
                    seat = bestSeat,
                    tile = inside.toDomainTile(),
                    confidence = inside.confidence,
                )
            }
        }

        val classifyCrop = cropNorm(full, rectSan) ?: return null
        val jpeg = compressJpeg(classifyCrop, 72)
        classifyCrop.recycle()
        val (tile, conf) = classifier.classify(jpeg)
        return DiscardEvent(seat = bestSeat, tile = tile, confidence = conf)
    }

    /**
     * 当中央区差分相对「当前最佳河」过大，且河侧仅略高于阈值时，认为变化主因在桌面中央（集堆/UI），**不作为**单侧新弃牌。
     */
    private fun shouldSuppressRiverDueToCenter(centerDiff: Int, riverDiff: Int): Boolean {
        if (centerDiff <= 0) return false
        val riverWeak = riverDiff <= motionThreshold + riverWeakSlack
        val centerDominates = centerDiff >= (riverDiff * centerDominatesRiverRatio).toInt()
        return riverWeak && centerDominates
    }

    /**
     * 结算/大结算类界面启发式：与对局桌布相比，底部常有 **大块橙色「继续游戏」** 按钮；顶部可有高亮金 UI。
     * 假阳性控制：仅当采样区内暖色像素占比 **同时** 满足较高阈值才判定，避免普通牌面误伤。
     */
    private fun isLikelySettlementScreen(full: Bitmap): Boolean {
        if (warmCtaStripRatio(full, bottomCtaRect) >= settlementOrangeRatio) return true
        if (victoryGoldBandRatio(full, topTitleRect) >= settlementGoldRatio) return true
        return false
    }

    private fun warmCtaStripRatio(src: Bitmap, n: NormRect): Float {
        val bmp = cropNorm(src, n) ?: return 0f
        try {
            var samples = 0
            var warm = 0
            val step = 3
            for (y in 0 until bmp.height step step) {
                for (x in 0 until bmp.width step step) {
                    samples++
                    val c = bmp.getPixel(x, y)
                    val r = c shr 16 and 0xff
                    val g = c shr 8 and 0xff
                    val b = c and 0xff
                    if (r > 198 && g in 65..230 && b < 118 && r > g + 18 && r > b + 28) warm++
                }
            }
            if (samples == 0) return 0f
            return warm.toFloat() / samples
        } finally {
            bmp.recycle()
        }
    }

    private fun victoryGoldBandRatio(src: Bitmap, n: NormRect): Float {
        val bmp = cropNorm(src, n) ?: return 0f
        try {
            var samples = 0
            var gold = 0
            val step = 3
            for (y in 0 until bmp.height step step) {
                for (x in 0 until bmp.width step step) {
                    samples++
                    val c = bmp.getPixel(x, y)
                    val r = c shr 16 and 0xff
                    val g = c shr 8 and 0xff
                    val b = c and 0xff
                    if (r > 185 && g > 150 && b in 40..160 && r > b + 40 && g > b + 20) gold++
                }
            }
            if (samples == 0) return 0f
            return gold.toFloat() / samples
        } finally {
            bmp.recycle()
        }
    }

    private fun commitMotionHistory(
        newRivers: Map<Seat, IntArray>,
        newCenter: IntArray?,
    ) {
        prevRiverThumb.clear()
        prevRiverThumb.putAll(newRivers)
        prevCenterThumb = newCenter
    }

    private fun clearMotionHistory() {
        prevRiverThumb.clear()
        prevCenterThumb = null
    }

    private fun bboxCenterInNormRect(bbox: NormalizedBBox, river: NormRect): Boolean {
        val cx = (bbox.left + bbox.right) / 2f
        val cy = (bbox.top + bbox.bottom) / 2f
        return cx >= river.left && cx <= river.right && cy >= river.top && cy <= river.bottom
    }

    private fun compressJpeg(bmp: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }

    private fun cropNorm(src: Bitmap, n: NormRect): Bitmap? {
        val sw = src.width
        val sh = src.height
        val l = (n.left * sw).toInt().coerceIn(0, max(0, sw - 2))
        val t = (n.top * sh).toInt().coerceIn(0, max(0, sh - 2))
        val r = (n.right * sw).toInt().coerceIn(l + 1, sw)
        val b = (n.bottom * sh).toInt().coerceIn(t + 1, sh)
        val w = r - l
        val h = b - t
        if (w < 4 || h < 4) return null
        return try {
            Bitmap.createBitmap(src, l, t, w, h)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun downscaleGray(src: Bitmap, size: Int): IntArray {
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val out = IntArray(size * size)
        var i = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val c = scaled.getPixel(x, y)
                val r = c shr 16 and 0xff
                val g = c shr 8 and 0xff
                val b = c and 0xff
                out[i++] = (r * 30 + g * 59 + b * 11) / 100
            }
        }
        if (scaled != src) scaled.recycle()
        return out
    }

    companion object {
        private const val THUMB = 8
        /** 全 ROI 8×8 像素灰度差分 L1 阈值，越大越不敏感（可按机型在 [AppProfile] 元数据中后置）。 */
        private const val motionThreshold = 180

        /** 中央差分 ≥ 河侧差分 × 该系数，且河侧为弱触发时，抑制河侧弃牌事件。 */
        private const val centerDominatesRiverRatio = 2.15f

        /** 在 [motionThreshold] 之上再放宽的「弱河」上界，用于与中央主从比较。 */
        private const val riverWeakSlack = 110

        /** 底部「继续游戏」类暖色条带（归一化）。 */
        private val bottomCtaRect = NormRect(0.08f, 0.72f, 0.92f, 0.995f)

        /** 顶部胜利标题可能出现的金 UI 带（归一化）。 */
        private val topTitleRect = NormRect(0.30f, 0.02f, 0.70f, 0.16f)

        /** 底部采样区内暖色像素占比超过该值 → 判为结算/大面板。 */
        private const val settlementOrangeRatio = 0.048f

        /** 顶部金 UI 采样占比阈值（略低于底部，减少误报）。 */
        private const val settlementGoldRatio = 0.028f
    }
}
