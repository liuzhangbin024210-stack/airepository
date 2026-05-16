package com.majiang.counter.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.majiang.counter.domain.Tile
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.profile.RoiCalibrationPack
import com.majiang.counter.domain.Suit
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 录屏 / 截图金帧回归：使用仓库 [picture] 目录 PNG（通过 androidTest assets 合并打包），
 * 对 [RiverDiffTableTracker] 的结算门禁与「静止对局帧」无差分行为做仪器断言。
 */
@RunWith(AndroidJUnit4::class)
class GoldenFrameRiverDiffAndroidTest {

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private fun decodePngAsset(name: String): Bitmap? =
        runCatching {
            appContext.assets.open(name).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

    /** 恒返回固定牌与置信度，避免依赖真实 TFLite。 */
    private class FixedTileClassifier(
        private val tile: Tile = Tile(Suit.WAN, 1),
        private val conf: Float = 1f,
    ) : TileClassifier {
        override suspend fun classify(crop: ByteArray?): Pair<Tile, Float> = tile to conf

        override fun isModelAvailable(): Boolean = true
    }

    @Test
    fun settlementFrame_5png_neverEmitsDiscard() = runBlocking {
        assumeTrue("需存在 picture/5.png", assetExists("5.png"))
        val bmp = decodePngAsset("5.png")
        assertThat(bmp).isNotNull()
        val bitmap = requireNotNull(bmp)
        val tracker = RiverDiffTableTracker(FixedTileClassifier())
        val roi = RoiCalibrationPack.defaultFromProfile(AppProfile.xuezhanDefault())
        val profile = AppProfile.xuezhanDefault()
        try {
            repeat(4) {
                val evt = tracker.processFullFrameBitmap(bitmap, roi, profile)
                assertThat(evt).isNull()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun playingFrame_2png_twice_noDiscardEvent() = runBlocking {
        assumeTrue("需存在 picture/2.png", assetExists("2.png"))
        val bmp = decodePngAsset("2.png")
        assertThat(bmp).isNotNull()
        val bitmap = requireNotNull(bmp)
        val tracker = RiverDiffTableTracker(FixedTileClassifier())
        val roi = RoiCalibrationPack.defaultFromProfile(AppProfile.xuezhanDefault())
        val profile = AppProfile.xuezhanDefault()
        try {
            assertThat(tracker.processFullFrameBitmap(bitmap, roi, profile)).isNull()
            assertThat(tracker.processFullFrameBitmap(bitmap, roi, profile)).isNull()
        } finally {
            bitmap.recycle()
        }
    }

    private fun assetExists(name: String): Boolean =
        runCatching { appContext.assets.open(name).use { true } }.getOrDefault(false)
}
