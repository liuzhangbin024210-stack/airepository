package com.majiang.counter.profile

/**
 * 归一化矩形（相对预览 0..1）。滑条调节时可能短暂不合法，用 [sanitized] 再写入状态。
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
    }

    fun sanitized(): NormRect {
        var l = left.coerceIn(0f, 0.99f)
        var t = top.coerceIn(0f, 0.99f)
        var r = right.coerceIn(0.01f, 1f)
        var b = bottom.coerceIn(0.01f, 1f)
        if (r <= l + 0.02f) r = (l + 0.05f).coerceAtMost(1f)
        if (b <= t + 0.02f) b = (t + 0.05f).coerceAtMost(1f)
        return NormRect(l, t, r, b)
    }
}

/**
 * 单款麻将 App 的皮肤与几何预设（首款血战默认 + 占位）。
 *
 * @property defaultRiverRects 四家牌河 ROI（顺序与 [Seat] 枚举一致：E,S,W,N）。
 * @property hudRect 「剩 NN 张」等 HUD 区域（可选）。
 * @property defaultExtraRects 扩展 ROI（键与 [VisualRoiKeys] 一致）；为 null 时用 [RoiCalibrationPack] 内建兜底。
 * @property defaultStableFrameMs 稳定帧建议值（毫秒，占位）。
 * @property visionMinClassifierConfidence 视觉分类置信度下限，低于则不写入河牌。
 * @property handClassifierMinConfidence 本家手牌条逐张分类下限（可略低于河牌门禁，避免 13 张中偶发一张略低导致整手失败）。
 * @property visionStableFramesRequired 同一座位、同一牌连续帧数达标后才视为稳定。
 * @property hudReconcileToleranceTiles HUD 与推算牌墙剩张允许误差（张）。
 * @property hudReconcileMaxFailuresBeforeBlock 连续对账失败次数上限，达到后暂停视觉写河。
 * @property hudOcrMinIntervalMs 相机流上两次 HUD OCR 的最小间隔（毫秒）。
 * @property hudOcrStableReadsRequired OCR 结果需连续相同次数才写入 [GameState.hudRemainingTiles]。
 */
data class AppProfile(
    val appId: String,
    val displayName: String,
    val defaultRiverRects: List<NormRect>? = null,
    val hudRect: NormRect? = null,
    val defaultExtraRects: Map<String, NormRect>? = null,
    val defaultStableFrameMs: Long = 280L,
    val visionMinClassifierConfidence: Float = 0.85f,
    val handClassifierMinConfidence: Float = 0.58f,
    val visionStableFramesRequired: Int = 2,
    val hudReconcileToleranceTiles: Int = 2,
    val hudReconcileMaxFailuresBeforeBlock: Int = 3,
    val hudOcrMinIntervalMs: Long = 900L,
    val hudOcrStableReadsRequired: Int = 2,
) {
    companion object {
        /** 占位旧 id（兼容 Room 已有数据）。 */
        fun placeholder(): AppProfile = AppProfile(
            appId = "target_v1",
            displayName = "占位目标 App",
            defaultRiverRects = null,
            defaultExtraRects = null,
            visionMinClassifierConfidence = 0.85f,
            visionStableFramesRequired = 2,
            hudReconcileToleranceTiles = 2,
            hudReconcileMaxFailuresBeforeBlock = 3,
        )

        /**
         * 血战麻将默认皮肤：横屏、本家在下（南）时的 **初值** ROI，仍以用户标定为准。
         * 坐标相对预览 0..1；左/右为侧家竖长条，上/下为对家与本家横条。
         */
        fun xuezhanDefault(): AppProfile {
            // 依据 picture/*.png（2556×1179）全屏读图粗估；相机预览比例若不同请在标定页微调。
            val hud = NormRect(0.28f, 0.28f, 0.72f, 0.54f)
            return AppProfile(
                appId = "xuezhan_mahjong_default",
                displayName = "血战麻将（默认几何）",
                defaultRiverRects = listOf(
                    // 左家弃牌常出现在偏中央，故 EAST 列略加宽
                    NormRect(0.00f, 0.22f, 0.20f, 0.78f), // EAST
                    // 本家河在「手牌上方」横带，与 hand_bottom 留缝
                    NormRect(0.12f, 0.58f, 0.88f, 0.81f), // SOUTH
                    NormRect(0.80f, 0.22f, 1.00f, 0.78f), // WEST
                    NormRect(0.15f, 0.03f, 0.85f, 0.20f), // NORTH
                ),
                hudRect = hud,
                defaultExtraRects = mapOf(
                    VisualRoiKeys.HAND_BOTTOM to NormRect(0.05f, 0.825f, 0.95f, 0.995f),
                    VisualRoiKeys.HUD to hud,
                    VisualRoiKeys.CENTER_LAST_DISCARD to NormRect(0.42f, 0.38f, 0.58f, 0.52f),
                    VisualRoiKeys.HAND_COUNT_EAST to NormRect(0.02f, 0.32f, 0.14f, 0.50f),
                    VisualRoiKeys.HAND_COUNT_NORTH to NormRect(0.40f, 0.02f, 0.60f, 0.12f),
                    VisualRoiKeys.HAND_COUNT_WEST to NormRect(0.86f, 0.32f, 0.98f, 0.50f),
                    // 庄标随局在左上/右上等头像旁移动，此处为左上头像邻域示意框，精细识别宜按座位拆 ROI（后续皮肤包）
                    VisualRoiKeys.DEALER_BADGE to NormRect(0.04f, 0.06f, 0.24f, 0.20f),
                ),
            )
        }
    }
}
