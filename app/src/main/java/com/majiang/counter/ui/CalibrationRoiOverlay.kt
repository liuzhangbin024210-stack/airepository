package com.majiang.counter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.majiang.counter.domain.Seat
import com.majiang.counter.profile.NormRect
import com.majiang.counter.profile.RoiCalibrationPack
import com.majiang.counter.profile.VisualRoiKeys
import kotlin.math.max

private val RiverRoiRed = Color(0xFFFF3333)
/** 本家手牌标定辅助：细虚线，与四河实线区分。 */
private val HandRoiGuide = Color(0xFFFFAA66)

/**
 * 绘制 **四家舍牌** 细实线红框，及 **本家手牌区** 细虚线辅助框（不占中央，随标定在画面边缘）。
 *
 * @param analysisFrameSize 最近 [ImageAnalysis] 帧的像素宽高（与 ROI 归一化同一坐标系）；
 * 非空时按「中心裁切铺满」映射到当前预览画布，与 [PreviewView.ScaleType.FILL_CENTER] 常见表现一致；
 * 映射结果再裁切到画布内，避免上下/左右轻微不同步时框线画出可视区域。
 */
@Composable
fun CalibrationRoiOverlay(
    pack: RoiCalibrationPack,
    analysisFrameSize: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val vw = size.width
        val vh = size.height
        if (vw < 8f || vh < 8f) return@Canvas

        val frame = analysisFrameSize?.takeIf { it.first > 0 && it.second > 0 }

        Seat.entries.forEach { seat ->
            val n = pack.rivers[seat] ?: return@forEach
            val rect = normToViewRect(n, frame, vw, vh).clampToCanvas(vw, vh) ?: return@forEach
            drawRiverRoiThin(rect)
        }

        pack.rectForExtra(VisualRoiKeys.HAND_BOTTOM)?.let { n ->
            val rect = normToViewRect(n, frame, vw, vh).clampToCanvas(vw, vh)
            if (rect != null && rect.width >= 4f && rect.height >= 4f) {
                drawHandRoiDashedGuide(rect)
            }
        }
    }
}

private fun normToViewRect(
    n: NormRect,
    frame: Pair<Int, Int>?,
    vw: Float,
    vh: Float,
): Rect {
    val f = frame?.takeIf { it.first > 0 && it.second > 0 }
    return if (f != null) {
        mapNormRectCoverToView(n, f.first, f.second, vw, vh)
    } else {
        Rect(
            n.left * vw,
            n.top * vh,
            n.right * vw,
            n.bottom * vh,
        )
    }
}

/**
 * 将归一化矩形（相对分析帧 0..1）映射到预览视图：等比放大至铺满视窗，多余部分裁掉（COVER）。
 */
private fun mapNormRectCoverToView(
    n: NormRect,
    bufW: Int,
    bufH: Int,
    vw: Float,
    vh: Float,
): Rect {
    val bw = bufW.toFloat().coerceAtLeast(1f)
    val bh = bufH.toFloat().coerceAtLeast(1f)
    val scale = max(vw / bw, vh / bh)
    val dx = (vw - bw * scale) * 0.5f
    val dy = (vh - bh * scale) * 0.5f
    val left = n.left * bw * scale + dx
    val top = n.top * bh * scale + dy
    val right = n.right * bw * scale + dx
    val bottom = n.bottom * bh * scale + dy
    return Rect(left, top, right, bottom)
}

/** 将矩形裁切到 \[0,vw\]×\[0,vh\]；裁切后过薄则视为不可见。 */
private fun Rect.clampToCanvas(vw: Float, vh: Float): Rect? {
    if (vw < 1f || vh < 1f) return null
    val nl = left.coerceIn(0f, vw)
    val nt = top.coerceIn(0f, vh)
    val nr = right.coerceIn(0f, vw)
    val nb = bottom.coerceIn(0f, vh)
    val rw = nr - nl
    val rh = nb - nt
    if (rw < 4f || rh < 4f) return null
    return Rect(nl, nt, nr, nb)
}

private fun DrawScope.drawRiverRoiThin(rect: Rect) {
    val stroke = (1.1f * density).coerceIn(0.8f, 2.2f)
    val tl = Offset(rect.left, rect.top)
    val sz = Size(rect.width, rect.height)
    val rad = CornerRadius(2f * density, 2f * density)
    drawRoundRect(
        color = RiverRoiRed.copy(alpha = 0.88f),
        topLeft = tl,
        size = sz,
        cornerRadius = rad,
        style = Stroke(width = stroke),
    )
}

private fun DrawScope.drawHandRoiDashedGuide(rect: Rect) {
    val stroke = (0.95f * density).coerceIn(0.7f, 1.8f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f * density, 7f * density), 0f)
    val tl = Offset(rect.left, rect.top)
    val sz = Size(rect.width, rect.height)
    val rad = CornerRadius(2f * density, 2f * density)
    drawRoundRect(
        color = HandRoiGuide.copy(alpha = 0.92f),
        topLeft = tl,
        size = sz,
        cornerRadius = rad,
        style = Stroke(width = stroke, pathEffect = dash),
    )
}
