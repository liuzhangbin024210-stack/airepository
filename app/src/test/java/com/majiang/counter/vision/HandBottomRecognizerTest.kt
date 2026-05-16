package com.majiang.counter.vision

import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HandBottomRecognizer] 切格与门禁逻辑单测（纯 JVM，不依赖 [android.graphics.Bitmap]）。
 */
class HandBottomRecognizerTest {

    @Test
    fun slotBounds_coverFullWidth_for13And14() {
        val w = 390
        for (n in intArrayOf(13, 14)) {
            val first = HandBottomRecognizer.slotBounds(w, n, 0)
            val last = HandBottomRecognizer.slotBounds(w, n, n - 1)
            assertEquals(0, first.first)
            assertEquals(w, last.second)
        }
    }

    @Test
    fun recognizeBySlotCount_failsWhenModelMissing() = runBlocking {
        val recognizer = HandBottomRecognizer(FakeClassifier(confidence = 0.95f, modelAvailable = false))
        val result = recognizer.recognizeBySlotCountForTest(400, 60, 0.85f) {
            Tile(Suit.WAN, 1) to 0.95f
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("牌面识别") == true)
    }

    @Test
    fun recognizeBySlotCount_succeedsWhenConfidenceOk() = runBlocking {
        val classifier = FakeClassifier(confidence = 0.95f)
        val recognizer = HandBottomRecognizer(classifier)
        val result = recognizer.recognizeBySlotCountForTest(400, 60, 0.85f) {
            classifier.next()
        }
        assertTrue(result.isSuccess)
        val hand = result.getOrThrow()
        assertTrue(hand.size == 13 || hand.size == 14)
        assertTrue(classifier.callCount >= 13)
    }

    private class FakeClassifier(
        private val confidence: Float,
        private val modelAvailable: Boolean = true,
    ) : TileClassifier {
        var callCount = 0

        override fun isModelAvailable(): Boolean = modelAvailable

        override suspend fun classify(crop: ByteArray?): Pair<Tile, Float> = next()

        fun next(): Pair<Tile, Float> {
            callCount++
            return Tile(Suit.WAN, (callCount % 9) + 1) to confidence
        }
    }
}
