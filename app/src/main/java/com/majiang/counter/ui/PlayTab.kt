package com.majiang.counter.ui

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majiang.counter.domain.Seat
import com.majiang.counter.ui.util.tileShortLabel
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/** 相机画面上 HUD 用高亮绿色，在复杂背景下仍易辨认。 */
private val CameraHudGreen = Color(0xFF00FF88)

/** 相机预览区高度：略增大以便同时看清画面与底部叠加层。 */
private val CameraPreviewAreaHeight = 340.dp

/**
 * 叠加在实时预览上的分析 HUD 样式：等宽粗体 + 阴影，提高对比度。
 *
 * @param fontSize - 字号。
 */
private fun cameraHudTextStyle(fontSize: androidx.compose.ui.unit.TextUnit = 13.sp) = TextStyle(
    color = CameraHudGreen,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = fontSize,
    shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 4f),
)

/**
 * 对局页：底部导航控制相机与分析；此处展示预览、记牌与打法建议。
 *
 * @param hasCameraPermission - 是否已授予相机权限（由 [MainScreen] 统一申请与同步）。
 */
@Composable
fun PlayTab(
    vm: GameViewModel,
    hasCameraPermission: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.gameState.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val busy by vm.analysisBusy.collectAsStateWithLifecycle()
    val cameraActive by vm.cameraActive.collectAsStateWithLifecycle()
    val visionBlocked by vm.visionAutoRiverBlocked.collectAsStateWithLifecycle()
    val analysisMessage by vm.analysisMessage.collectAsStateWithLifecycle()
    val expectedWall by vm.reconciledExpectedWall.collectAsStateWithLifecycle()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(lifecycleOwner, hasCameraPermission, cameraActive, previewView, vm) {
        val pv = previewView
        if (!hasCameraPermission || !cameraActive || pv == null) {
            return@DisposableEffect onDispose { }
        }
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(analysisExecutor) { image ->
            runBlocking {
                vm.processCameraFrame(image)
            }
        }
        val run = Runnable {
            val provider = future.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(pv.surfaceProvider)
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }
        future.addListener(run, mainExecutor)
        onDispose {
            analysis.clearAnalyzer()
            analysisExecutor.shutdown()
            if (future.isDone) {
                runCatching { future.get().unbindAll() }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            PlayerStrings.FLOW_HINT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        if (busy) {
            Text(PlayerStrings.ANALYZING, style = MaterialTheme.typography.bodySmall)
        }
        analysisMessage?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (visionBlocked) {
            Text(
                PlayerStrings.VISION_RIVER_PAUSED,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = { vm.resumeVisionAutoRiver() }) {
                Text(PlayerStrings.RESUME_RIVER)
            }
        }

        if (cameraActive && hasCameraPermission) {
            // TextureView（COMPATIBLE）与 Compose 同层叠放正常；SurfaceView 常导致「只有相机」盖住分析。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CameraPreviewAreaHeight),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }.also { previewView = it }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                CameraAnalysisHudOverlay(
                    state = state,
                    expectedWall = expectedWall,
                    insights = insights,
                    busy = busy,
                    analysisMessage = analysisMessage,
                    visionBlocked = visionBlocked,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        } else if (!hasCameraPermission) {
            Text(
                PlayerStrings.CAMERA_PERMISSION_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GameSummarySection(state, expectedWall)
        AdviceSection(insights)
    }
}

/**
 * 叠在相机预览之上的精简分析条：半透明底 + 高对比绿色字，便于「边看画面边看结论」。
 */
@Composable
private fun CameraAnalysisHudOverlay(
    state: com.majiang.counter.domain.GameState,
    expectedWall: Int?,
    insights: Pair<com.majiang.counter.analysis.TenpaiInsight, com.majiang.counter.analysis.DiscardRiskInsight>?,
    busy: Boolean,
    analysisMessage: String?,
    visionBlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val hudScroll = rememberScrollState()
    Column(
        modifier
            .background(Color(0xCC000000))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .heightIn(max = 200.dp)
            .verticalScroll(hudScroll),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (busy) {
            Text(PlayerStrings.ANALYZING, style = cameraHudTextStyle(12.sp))
        }
        analysisMessage?.let { msg ->
            Text(
                msg,
                style = cameraHudTextStyle(12.sp).copy(color = Color(0xFFFF6B6B)),
            )
        }
        if (visionBlocked) {
            Text(
                PlayerStrings.VISION_RIVER_PAUSED,
                style = cameraHudTextStyle(11.sp).copy(color = Color(0xFFFFCC00)),
            )
        }
        val handStr =
            if (state.myHand.isEmpty()) {
                "（等待识别）"
            } else {
                state.myHand.joinToString(" ") { tileShortLabel(it) }
            }
        Text(
            "${PlayerStrings.SECTION_MY_HAND} $handStr",
            style = cameraHudTextStyle(12.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${PlayerStrings.PHASE_LABEL}：${PlayerStrings.phaseLabel(state.phase)} · " +
                "${PlayerStrings.WALL_REMAINING} ${state.hudRemainingTiles ?: PlayerStrings.WALL_UNKNOWN} / " +
                "${expectedWall ?: PlayerStrings.WALL_UNKNOWN}",
            style = cameraHudTextStyle(11.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val pair = insights
        if (pair != null) {
            val (ten, risk) = pair
            val main = risk.recommendedDiscard
            if (main != null) {
                Text(
                    "${PlayerStrings.SUGGEST_DISCARD} ${tileShortLabel(main)}",
                    style = cameraHudTextStyle(18.sp),
                )
            }
            if (ten.waitingTiles.isNotEmpty()) {
                Text(
                    "${PlayerStrings.TENPAI} ${ten.waitingTiles.joinToString { tileShortLabel(it) }}",
                    style = cameraHudTextStyle(13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(PlayerStrings.NO_RESULT, style = cameraHudTextStyle(12.sp))
        }
    }
}

@Composable
private fun GameSummarySection(
    state: com.majiang.counter.domain.GameState,
    expectedWall: Int?,
) {
    Text(PlayerStrings.SECTION_MY_HAND, style = MaterialTheme.typography.labelLarge)
    Text(
        if (state.myHand.isEmpty()) "（等待识别）"
        else state.myHand.joinToString(" ") { tileShortLabel(it) },
        style = MaterialTheme.typography.bodyMedium,
    )

    Text(
        "${PlayerStrings.PHASE_LABEL}：${PlayerStrings.phaseLabel(state.phase)}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "${PlayerStrings.WALL_REMAINING}：画面 ${state.hudRemainingTiles ?: PlayerStrings.WALL_UNKNOWN}；" +
            "推算 ${expectedWall ?: PlayerStrings.WALL_UNKNOWN}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val dq = state.dingQue.filterValues { it != null }
    if (dq.isNotEmpty()) {
        Text(
            "${PlayerStrings.DING_QUE}：" +
                dq.entries.joinToString { "${PlayerStrings.seatShort(it.key)}${PlayerStrings.dingQueLabel(it.value)}" },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    state.dealerSeat?.let {
        Text(
            "${PlayerStrings.DEALER}：${PlayerStrings.seatShort(it)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.wonSeats.isNotEmpty()) {
        Text(
            "${PlayerStrings.WON_SEATS}：${state.wonSeats.joinToString { PlayerStrings.seatShort(it) }}",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Text(PlayerStrings.SECTION_DISCARDS, style = MaterialTheme.typography.labelLarge)
    Seat.entries.forEach { seat ->
        val river = state.discards[seat].orEmpty()
        if (river.isNotEmpty()) {
            Text(
                "${PlayerStrings.seatShort(seat)}：${river.joinToString(" ") { tileShortLabel(it) }}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AdviceSection(insights: Pair<com.majiang.counter.analysis.TenpaiInsight, com.majiang.counter.analysis.DiscardRiskInsight>?) {
    Text(PlayerStrings.SECTION_ADVICE, style = MaterialTheme.typography.titleMedium)
    val pair = insights
    if (pair == null) {
        Text(PlayerStrings.NO_RESULT, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val (ten, risk) = pair
    val main = risk.recommendedDiscard
    if (main != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "${PlayerStrings.SUGGEST_DISCARD}：${tileShortLabel(main)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    if (risk.recommendedDiscardOrder.size > 1) {
        Text(PlayerStrings.DISCARD_PRIORITY, style = MaterialTheme.typography.labelLarge)
        risk.recommendedDiscardOrder.forEachIndexed { i, t ->
            val r = risk.ronAnyByDiscard[t] ?: risk.studentRonAnyByDiscard?.get(t)
            val k = risk.kongAnyByDiscard[t] ?: risk.studentKongAnyByDiscard?.get(t)
            val detail = buildString {
                if (r != null) append("点炮 ${"%.0f".format(r * 100)}%")
                if (k != null) {
                    if (isNotEmpty()) append(" · ")
                    append("点杠 ${"%.0f".format(k * 100)}%")
                }
            }
            Text(
                "${i + 1}. ${tileShortLabel(t)}　$detail",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (ten.waitingTiles.isNotEmpty()) {
        Text(
            "${PlayerStrings.TENPAI}：${ten.waitingTiles.joinToString { tileShortLabel(it) }}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(PlayerStrings.WAITING_TENDENCY, style = MaterialTheme.typography.labelLarge)
        ten.huProbByWaiting.forEach { (t, p) ->
            Text(
                "  ${tileShortLabel(t)}：${"%.1f".format(p * 100)}%",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Text(PlayerStrings.RON_RISK, style = MaterialTheme.typography.labelLarge)
    risk.ronAnyByDiscard.forEach { (t, p) ->
        Text(
            "  打 ${tileShortLabel(t)}：${"%.1f".format(p * 100)}%",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(PlayerStrings.KONG_RISK, style = MaterialTheme.typography.labelLarge)
    risk.kongAnyByDiscard.forEach { (t, p) ->
        Text(
            "  打 ${tileShortLabel(t)}：${"%.1f".format(p * 100)}%",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
