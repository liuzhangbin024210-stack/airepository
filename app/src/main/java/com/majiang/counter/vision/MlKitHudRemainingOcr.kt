package com.majiang.counter.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * ML Kit 中文识别 + [HudRemainingTextParser]；在后台线程执行 [await]。
 */
@Singleton
class MlKitHudRemainingOcr @Inject constructor() : HudRemainingOcr {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognizeRemainingTiles(hudCrop: Bitmap): Int? =
        withContext(Dispatchers.Default) {
            runCatching {
                val image = InputImage.fromBitmap(hudCrop, 0)
                val result = recognizer.process(image).await()
                HudRemainingTextParser.parseRemainingTiles(result.text)
            }.getOrElse { null }
        }
}
