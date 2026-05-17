package com.majiang.counter.vision

import android.graphics.Bitmap
import com.majiang.counter.domain.Tile
import com.majiang.counter.profile.NormRect
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max

/**
 * 本家手牌横条识别：裁切 [handRect] 后按 13/14 等分竖格，逐张调用 [TileClassifier]。
 *
 * 14 张时视为含最右「摸进待打」一张；听牌分析取前 13 张（由 [GameViewModel] 处理）。
 */
class HandBottomRecognizer @Inject constructor(
    private val classifier: TileClassifier,
) {

    /**
     * @param minConfidence 单张置信度下限，任一张低于则整手失败。
     * @return 成功时为左→右牌序（可能 13 或 14 张）。
     */
    suspend fun recognize(
        full: Bitmap,
        handRect: NormRect,
        minConfidence: Float,
    ): Result<List<Tile>> {
        if (!classifier.isModelAvailable()) {
            return Result.failure(IllegalStateException(MODEL_MISSING_MSG))
        }
        val strip = full.cropNormRect(handRect)
            ?: return Result.failure(
                IllegalStateException("手牌区域过小或无效，请到设置调整「本家手牌区」"),
            )
        return try {
            recognizeStrip(strip, minConfidence)
        } finally {
            strip.recycle()
        }
    }


    /**
     * 按张数尝试识别（不依赖 [Bitmap]），供 JVM 单测。
     */
    internal suspend fun recognizeBySlotCountForTest(
        stripWidth: Int,
        stripHeight: Int,
        minConfidence: Float,
        classify: suspend () -> Pair<Tile, Float>,
    ): Result<List<Tile>> {
        if (!classifier.isModelAvailable()) {
            return Result.failure(IllegalStateException(MODEL_MISSING_MSG))
        }
        return pickBestHand(stripWidth, stripHeight, minConfidence, classify)
    }

    private suspend fun recognizeStrip(strip: Bitmap, minConfidence: Float): Result<List<Tile>> {
        return pickBestHand(strip.width, strip.height, minConfidence) { slotIndex, slotCount ->
            classifySlotFromStrip(strip, slotIndex, slotCount, minConfidence)
        }
    }

    private suspend fun pickBestHand(
        stripWidth: Int,
        stripHeight: Int,
        minConfidence: Float,
        classify: suspend () -> Pair<Tile, Float>,
    ): Result<List<Tile>> = pickBestHand(stripWidth, stripHeight, minConfidence) { _, _ -> classify() }

    private suspend fun pickBestHand(
        stripWidth: Int,
        stripHeight: Int,
        minConfidence: Float,
        classifySlot: suspend (slotIndex: Int, slotCount: Int) -> Pair<Tile, Float>?,
    ): Result<List<Tile>> {
        var best: List<Tile>? = null
        var bestScore = -1f
        for (slotCount in intArrayOf(14, 13)) {
            val attempt = classifyAllSlots(stripWidth, stripHeight, slotCount, minConfidence, classifySlot)
                ?: continue
            val avg = attempt.second
            if (avg > bestScore) {
                bestScore = avg
                best = attempt.first
            }
        }
        val hand = best
            ?: return Result.failure(
                IllegalStateException(HAND_INCOMPLETE_MSG),
            )
        return Result.success(hand)
    }

    private suspend fun classifyAllSlots(
        stripWidth: Int,
        stripHeight: Int,
        slotCount: Int,
        minConfidence: Float,
        classifySlot: suspend (slotIndex: Int, slotCount: Int) -> Pair<Tile, Float>?,
    ): Pair<List<Tile>, Float>? {
        if (stripWidth < slotCount * 4 || stripHeight < 4) return null
        val tiles = mutableListOf<Tile>()
        var confSum = 0f
        for (i in 0 until slotCount) {
            val (l, r) = slotCropHorizontalBounds(stripWidth, slotCount, i)
            if (r - l < 4) return null
            val pair = classifySlot(i, slotCount) ?: return null
            val (tile, conf) = pair
            if (conf < minConfidence) return null
            tiles.add(tile)
            confSum += conf
        }
        return tiles to (confSum / slotCount)
    }

    private suspend fun classifySlotFromStrip(
        strip: Bitmap,
        slotIndex: Int,
        slotCount: Int,
        minConfidence: Float,
    ): Pair<Tile, Float>? {
        val w = strip.width
        val h = strip.height
        val (l, r) = slotCropHorizontalBounds(w, slotCount, slotIndex)
        if (r - l < 4) return null
        val tileBmp = try {
            Bitmap.createBitmap(strip, l, 0, r - l, h)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            // PNG 无损，避免 JPEG 块效应在小牌面裁切上拉低真实模型置信度
            val png = tileBmp.toPngBytes()
            classifier.classify(png)
        } finally {
            tileBmp.recycle()
        }
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    companion object {
        /**
         * 与「画面是否对准牌桌」无关：必须在 assets 中成功加载 .tflite 牌面分类器才能逐张识别；
         * 设置里的画面区域标定只决定裁切哪一块像素，不能代替模型。
         */
        const val MODEL_MISSING_MSG =
            "尚未加载牌面识别模型（TensorFlow Lite .tflite），无法从画面读出每一张手牌。" +
            "请将模型按 assets/ml/<应用>/model_manifest.json 配置放入工程（详见 README）；「设置」中的画面区域标定仅用于框选手牌/河牌等裁切区域，不能代替模型。"

        /** 与 [com.majiang.counter.ui.PlayerStrings.HAND_NOT_13] 文案一致（识别层不依赖 ui 模块）。 */
        const val HAND_INCOMPLETE_MSG =
            "尚未识别齐本家 13 张手牌，请对准手牌区后重试。"

        /** 等分竖格左右像素边界（供单测校验切格覆盖整幅手牌条）。 */
        internal fun slotBounds(stripWidth: Int, slotCount: Int, index: Int): Pair<Int, Int> {
            val l = (stripWidth * index) / slotCount
            val r = (stripWidth * (index + 1)) / slotCount
            return l to r
        }

        /**
         * 在等分格基础上左右各缩进一小段，减轻相邻牌缝被裁进当前格造成的误分。
         */
        internal fun slotCropHorizontalBounds(stripWidth: Int, slotCount: Int, index: Int): Pair<Int, Int> {
            val (l0, r0) = slotBounds(stripWidth, slotCount, index)
            val slotW = r0 - l0
            if (slotW < 8) return l0 to r0
            val inset = max(1, (slotW * 0.025f).toInt()).coerceAtMost((slotW - 4) / 2)
            return (l0 + inset) to (r0 - inset)
        }
    }
}
