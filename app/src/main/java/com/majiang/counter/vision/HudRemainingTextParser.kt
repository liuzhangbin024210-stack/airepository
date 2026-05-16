package com.majiang.counter.vision

/**
 * 从 ML Kit 合并文本中解析血战 HUD「剩 NN 张」类数字（0..108）。
 *
 * 优先匹配「剩」后数字，避免误采「第 x/y 局」中的小节数字；若无则退化为「数字+张」。
 */
object HudRemainingTextParser {

    private val afterShengRegex = Regex("""剩\s*(\d{1,3})""")
    private val zhangSuffixRegex = Regex("""(\d{1,3})\s*张""")

    /**
     * @param ocrText ML Kit [com.google.mlkit.vision.text.Text.getText] 全量字符串。
     * @return 合法剩张数，无法解析时 null。
     */
    fun parseRemainingTiles(ocrText: String): Int? {
        if (ocrText.isBlank()) return null
        val compact = ocrText.replace("\n", " ").trim()
        afterShengRegex.find(compact)?.groupValues?.get(1)?.toIntOrNull()?.let { n ->
            if (n in 0..108) return n
        }
        zhangSuffixRegex.find(compact)?.groupValues?.get(1)?.toIntOrNull()?.let { n ->
            if (n in 0..108) return n
        }
        return null
    }
}
