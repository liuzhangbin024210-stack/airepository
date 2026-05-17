package com.majiang.counter.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majiang.counter.analysis.DiscardRiskInsight
import com.majiang.counter.analysis.SituationAnalyzer
import com.majiang.counter.analysis.TenpaiInsight
import com.majiang.counter.data.AppDatabase
import com.majiang.counter.data.RoiConfigEntity
import com.majiang.counter.domain.GamePhase
import com.majiang.counter.domain.GameState
import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.expectedWallRemainingOrNull
import com.majiang.counter.domain.hudMatchesReconciledWall
import com.majiang.counter.domain.Tile
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.profile.NormRect
import com.majiang.counter.profile.RoiCalibrationCodec
import com.majiang.counter.profile.RoiCalibrationPack
import com.majiang.counter.profile.VisualRoiKeys
import com.majiang.counter.vision.HandBottomRecognizer
import com.majiang.counter.vision.HudRemainingOcr
import com.majiang.counter.vision.TableTracker
import com.majiang.counter.vision.cropNormRect
import com.majiang.counter.vision.toBitmapArgb8888
import androidx.camera.core.ImageProxy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GameViewModel"

/** 连续点击「运行 MC」时的防抖间隔（毫秒），减轻低端机发热与重复计算（对应 perf-later 轻量落地）。 */
private const val MC_ANALYSIS_DEBOUNCE_MS = 350L

/**
 * 记牌、ROI 标定与 MC 分析的统一状态；**视觉标定 JSON v2** 持久化到 Room（兼容旧版四河 JSON）。
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val db: AppDatabase,
    private val appProfile: AppProfile,
    private val situationAnalyzer: SituationAnalyzer,
    private val tableTracker: TableTracker,
    private val hudRemainingOcr: HudRemainingOcr,
    private val handBottomRecognizer: HandBottomRecognizer,
) : ViewModel() {

    private val appId: String = appProfile.appId

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _roiPack = MutableStateFlow(RoiCalibrationPack.defaultFromProfile(appProfile))
    val roiPack: StateFlow<RoiCalibrationPack> = _roiPack.asStateFlow()

    /** 四牌河 ROI（由 [roiPack] 派生，供差分与相机页使用）。 */
    val riverRois: StateFlow<Map<Seat, NormRect>> = _roiPack
        .map { it.rivers }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = RoiCalibrationPack.defaultFromProfile(appProfile).rivers,
        )

    private val _insights = MutableStateFlow<Pair<TenpaiInsight, DiscardRiskInsight>?>(null)
    val insights: StateFlow<Pair<TenpaiInsight, DiscardRiskInsight>?> = _insights.asStateFlow()

    private val _analysisBusy = MutableStateFlow(false)
    val analysisBusy: StateFlow<Boolean> = _analysisBusy.asStateFlow()

    private val _cameraActive = MutableStateFlow(false)
    val cameraActive: StateFlow<Boolean> = _cameraActive.asStateFlow()

    private val _analysisMessage = MutableStateFlow<String?>(null)
    val analysisMessage: StateFlow<String?> = _analysisMessage.asStateFlow()

    /**
     * 最近一帧 [ImageAnalysis] 转 Bitmap 后的宽高（与 ROI 归一化坐标同一空间），
     * 供对局页将四河红框按「中心裁切铺满」映射到预览画布，与 [TableTracker] / 手牌识别裁切一致。
     */
    private val _analysisFrameSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val analysisFrameSize: StateFlow<Pair<Int, Int>?> = _analysisFrameSize.asStateFlow()

    private val lastFrameLock = Any()

    @Volatile
    private var lastFrameBitmap: Bitmap? = null

    /** 与 [startAnalysis] 防抖配合。 */
    private var analysisJob: Job? = null

    /**
     * 当 HUD 与记牌推算牌墙连续不符达到阈值时置 true，暂停 [processCameraFrame] 自动写河，直至用户在记牌页确认恢复。
     */
    private val _visionAutoRiverBlocked = MutableStateFlow(false)
    val visionAutoRiverBlocked: StateFlow<Boolean> = _visionAutoRiverBlocked.asStateFlow()

    /** 根据当前手牌、三手数、河、副露推算的牌墙剩张；信息不全时为 null（不对账）。 */
    val reconciledExpectedWall: StateFlow<Int?> = _gameState
        .map { it.expectedWallRemainingOrNull() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _gameState.value.expectedWallRemainingOrNull(),
        )

    private var hudMismatchStreak: Int = 0
    private var lastHudMismatchEventUptimeMs: Long = 0L

    private val _hudOcrEnabled = MutableStateFlow(true)
    val hudOcrEnabled: StateFlow<Boolean> = _hudOcrEnabled.asStateFlow()

    private var lastHudOcrAttemptUptimeMs: Long = 0L
    private var ocrHudCandidate: Int? = null
    private var ocrHudStableCount: Int = 0

    init {
        viewModelScope.launch {
            runCatching {
                val json = db.roiDao().get(appId)?.rectsJson
                _roiPack.value = RoiCalibrationCodec.fromJsonOrLegacy(json, appProfile)
            }.onFailure { e ->
                Log.w(TAG, "加载 ROI 失败，使用默认", e)
                _roiPack.value = RoiCalibrationPack.defaultFromProfile(appProfile)
            }
        }
    }

    fun updateRiverRoi(seat: Seat, rect: NormRect) {
        val safe = rect.sanitized()
        _roiPack.update { p -> p.copy(rivers = p.rivers + (seat to safe)) }
        persistCalibration()
    }

    fun updateExtraRoi(key: String, rect: NormRect) {
        val safe = rect.sanitized()
        _roiPack.update { p -> p.copy(extras = p.extras + (key to safe)) }
        persistCalibration()
    }

    /** 仅将四家牌河恢复为当前皮肤默认，保留已标定的扩展 ROI。 */
    fun resetRiverRoisToDefault() {
        val base = RoiCalibrationPack.defaultFromProfile(appProfile)
        _roiPack.update { p -> p.copy(rivers = base.rivers) }
        persistCalibration()
    }

    /** 将单座牌河恢复为皮肤默认。 */
    fun resetRiverRoiToDefault(seat: Seat) {
        val base = RoiCalibrationPack.defaultFromProfile(appProfile)
        val r = base.rivers[seat] ?: return
        _roiPack.update { p -> p.copy(rivers = p.rivers + (seat to r)) }
        persistCalibration()
    }

    /** 将某一扩展 ROI 恢复为皮肤默认几何。 */
    fun resetExtraRoiToDefault(key: String) {
        val base = RoiCalibrationPack.defaultFromProfile(appProfile)
        val r = base.extras[key] ?: return
        _roiPack.update { p -> p.copy(extras = p.extras + (key to r)) }
        persistCalibration()
    }

    /** 四牌河 + 扩展区域全部恢复为皮肤默认（标定链路「一键归零」）。 */
    fun resetAllCalibrationToProfileDefaults() {
        _roiPack.value = RoiCalibrationPack.defaultFromProfile(appProfile)
        persistCalibration()
    }

    private fun persistCalibration() {
        viewModelScope.launch {
            runCatching {
                val json = RoiCalibrationCodec.toJson(_roiPack.value)
                db.roiDao().upsert(RoiConfigEntity(appId = appId, rectsJson = json))
            }.onFailure { e -> Log.w(TAG, "保存 ROI 失败", e) }
        }
    }

    /**
     * 处理相机 [ImageAnalysis] 一帧：非 [GamePhase.PLAYING] 时跳过；否则可选 HUD OCR，再走 [TableTracker]。
     * 结算类画面与中央/四河主从抑制在 [com.majiang.counter.vision.RiverDiffTableTracker] 内实现。
     */
    fun setCameraActive(active: Boolean) {
        if (!active) {
            // 关闭相机时同步停掉打法推算与 HUD 结论，避免后台仍跑 MC 或界面残留上一轮建议。
            analysisJob?.cancel()
            analysisJob = null
            _analysisBusy.value = false
            _insights.value = null
            _analysisMessage.value = null
            synchronized(lastFrameLock) {
                lastFrameBitmap?.recycle()
                lastFrameBitmap = null
            }
            _analysisFrameSize.value = null
        }
        _cameraActive.value = active
    }

    /**
     * 对局页主入口：识别本家手牌后执行打法推算（须已 [setCameraActive] 为 true）。
     */
    fun startAnalysis(iterations: Int = 200) {
        analysisJob?.cancel()
        _analysisMessage.value = null
        if (!_cameraActive.value) {
            _analysisMessage.value = PlayerStrings.CAMERA_REQUIRED
            return
        }
        if (_gameState.value.phase == GamePhase.EXCHANGE_THREE) {
            _analysisMessage.value = PlayerStrings.EXCHANGE_THREE_BLOCK
            return
        }
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            _analysisBusy.value = true
            val frame = synchronized(lastFrameLock) {
                lastFrameBitmap?.let { src ->
                    src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
                }
            }
            if (frame == null) {
                _analysisMessage.value = PlayerStrings.NO_FRAME
                _analysisBusy.value = false
                return@launch
            }
            try {
                val handRect = _roiPack.value.rectForExtra(VisualRoiKeys.HAND_BOTTOM)
                    ?: RoiCalibrationPack.defaultFromProfile(appProfile)
                        .rectForExtra(VisualRoiKeys.HAND_BOTTOM)
                    ?: run {
                        _analysisMessage.value = PlayerStrings.HAND_NOT_13
                        return@launch
                    }
                val recognized = handBottomRecognizer.recognize(
                    frame,
                    handRect,
                    appProfile.handClassifierMinConfidence,
                )
                recognized.fold(
                    onFailure = { e ->
                        _analysisMessage.value = e.message ?: PlayerStrings.HAND_NOT_13
                    },
                    onSuccess = { tiles ->
                        val forTenpai = when (tiles.size) {
                            14 -> tiles.dropLast(1)
                            13 -> tiles
                            else -> emptyList()
                        }
                        if (forTenpai.size != 13) {
                            _analysisMessage.value = PlayerStrings.HAND_NOT_13
                            return@fold
                        }
                        _gameState.update { it.copy(myHand = forTenpai.sorted()) }
                        runAnalysisInternal(iterations)
                    },
                )
            } finally {
                frame.recycle()
                _analysisBusy.value = false
            }
        }
    }

    private suspend fun captureLastFrameFrom(image: ImageProxy) {
        val full = try {
            image.toBitmapArgb8888()
        } catch (_: Throwable) {
            return
        }
        val copy = full.copy(full.config ?: Bitmap.Config.ARGB_8888, false)
        full.recycle()
        synchronized(lastFrameLock) {
            lastFrameBitmap?.recycle()
            lastFrameBitmap = copy
        }
        _analysisFrameSize.value = copy.width to copy.height
    }

    suspend fun processCameraFrame(image: ImageProxy) {
        try {
            if (!_cameraActive.value) return
            captureLastFrameFrom(image)
            if (_gameState.value.phase != GamePhase.PLAYING) return
            maybeUpdateHudFromOcr(image)
            if (_visionAutoRiverBlocked.value) return
            val evt = tableTracker.processFrame(image, _roiPack.value, appProfile) ?: return
            val gs = _gameState.value
            if (!gs.hudMatchesReconciledWall(gs.hudRemainingTiles, appProfile.hudReconcileToleranceTiles)) {
                recordHudMismatchFailure()
                return
            }
            if (gs.expectedWallRemainingOrNull() != null && gs.hudRemainingTiles != null) {
                hudMismatchStreak = 0
            }
            applyDiscardIfTrusted(evt.seat, evt.tile, evt.confidence)
        } finally {
            image.close()
        }
    }

    /**
     * 按节流间隔裁切 `extras.hud` 区域，ML Kit 识别后双帧稳定再写入 [GameState.hudRemainingTiles]。
     */
    private suspend fun maybeUpdateHudFromOcr(image: ImageProxy) {
        if (!_hudOcrEnabled.value) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHudOcrAttemptUptimeMs < appProfile.hudOcrMinIntervalMs) return
        lastHudOcrAttemptUptimeMs = now
        val hudNorm = _roiPack.value.rectForExtra(VisualRoiKeys.HUD) ?: return
        val full = try {
            image.toBitmapArgb8888()
        } catch (_: Throwable) {
            return
        }
        try {
            val crop = full.cropNormRect(hudNorm) ?: return
            try {
                val n = hudRemainingOcr.recognizeRemainingTiles(crop)
                applyOcrHudStableCandidate(n)
            } finally {
                crop.recycle()
            }
        } finally {
            full.recycle()
        }
    }

    /**
     * OCR 结果需连续 [AppProfile.hudOcrStableReadsRequired] 次相同才落库，抑制单帧抖动。
     */
    private fun applyOcrHudStableCandidate(n: Int?) {
        if (n == null) {
            ocrHudCandidate = null
            ocrHudStableCount = 0
            return
        }
        val required = appProfile.hudOcrStableReadsRequired
        if (n == ocrHudCandidate) {
            ocrHudStableCount++
        } else {
            ocrHudCandidate = n
            ocrHudStableCount = 1
        }
        if (ocrHudStableCount < required) return
        val current = _gameState.value.hudRemainingTiles
        if (n != current) {
            _gameState.update { it.copy(hudRemainingTiles = n) }
            Log.d(TAG, "HUD OCR 写入剩张: $n")
        }
    }

    /** 是否从相机流自动识别牌墙剩张（对局页打开相机时默认开启，可在设置关闭）。 */
    fun setHudOcrEnabled(enabled: Boolean) {
        _hudOcrEnabled.value = enabled
        if (!enabled) {
            ocrHudCandidate = null
            ocrHudStableCount = 0
        }
    }

    /**
     * 对账失败计数（防抖）：避免分析线程高帧率下 streak 瞬间打满。
     */
    private fun recordHudMismatchFailure() {
        val now = SystemClock.uptimeMillis()
        if (now - lastHudMismatchEventUptimeMs < 400L) return
        lastHudMismatchEventUptimeMs = now
        hudMismatchStreak++
        if (hudMismatchStreak >= appProfile.hudReconcileMaxFailuresBeforeBlock) {
            _visionAutoRiverBlocked.value = true
            Log.w(TAG, "HUD 与记牌推算牌墙连续不符，已暂停视觉自动写河")
        }
    }

    /** 用户确认记牌/HUD 已校正后恢复视觉写河，并清零对账失败计数。 */
    fun resumeVisionAutoRiver() {
        hudMismatchStreak = 0
        lastHudMismatchEventUptimeMs = 0L
        _visionAutoRiverBlocked.value = false
    }

    internal fun addDiscard(seat: Seat, tile: Tile) {
        _gameState.update { st ->
            val cur = st.discards[seat].orEmpty()
            st.copy(discards = st.discards + (seat to (cur + tile)))
        }
    }

    /**
     * 视觉门禁：置信度不足时不写入正式状态（与修订计划一致）。
     */
    fun applyDiscardIfTrusted(
        seat: Seat,
        tile: Tile,
        confidence: Float,
        confidenceThreshold: Float = appProfile.visionMinClassifierConfidence,
    ) {
        if (confidence < confidenceThreshold) {
            Log.d(TAG, "弃牌置信度 $confidence < $confidenceThreshold，跳过写入")
            return
        }
        addDiscard(seat, tile)
    }

    private suspend fun runAnalysisInternal(iterations: Int) {
        try {
            delay(MC_ANALYSIS_DEBOUNCE_MS)
        } catch (e: CancellationException) {
            throw e
        }
        runCatching {
            val pair = situationAnalyzer.analyze(_gameState.value, iterations = iterations)
            _insights.value = pair
            _analysisMessage.value = null
        }.onFailure { e ->
            Log.e(TAG, "打法推算失败", e)
            _insights.value = null
            _analysisMessage.value = "打法推算失败，请稍后重试。"
        }
    }

    override fun onCleared() {
        synchronized(lastFrameLock) {
            lastFrameBitmap?.recycle()
            lastFrameBitmap = null
        }
        super.onCleared()
    }

    companion object {
        /** 不依赖注入的默认四河 ROI（与血战默认皮肤一致）。 */
        fun defaultRiverRois(): Map<Seat, NormRect> =
            RoiCalibrationPack.defaultFromProfile(AppProfile.xuezhanDefault()).rivers
    }
}
