# 验收与治理（实现侧摘要）

与修订计划 **§风险与验收标准**、**§蒸馏与模型治理** 对齐的落地要点：

## 视觉

- **管线**：`ImageAnalysis`（RGBA）→ `GameViewModel.processCameraFrame` → 可选 **HUD OCR**（`extras.hud` + ML Kit 中文 + [HudRemainingTextParser]）→ `RiverDiffTableTracker`（四河差分 + **中央 ROI 主从抑制** + **结算画面启发式禁触发** + `TileClassifier`）→ `GatedTableTracker`（置信度与稳定帧见 `AppProfile`）→ `applyDiscardIfTrusted`。
- **门禁写入**：默认 `model_manifest.json` 中 `tileClassifierFile` 为空，`TfliteTileClassifier` 走低置信度回退（约 0.08），**不会**通过门禁写入；打包牌面 `*.tflite` 并填写文件名后才会参与稳定帧门禁。换三张/碰杠窗等请在记牌页将 `GamePhase` 设为非 `PLAYING` 以暂停自动写河。
- **对账降级**：`GameViewModel.processCameraFrame` 在写入河牌前用 `GameState.expectedWallRemainingOrNull()` 与 `hudRemainingTiles` 比对（容差见 `AppProfile.hudReconcileToleranceTiles`）；连续失败达 `hudReconcileMaxFailuresBeforeBlock` 后置 `visionAutoRiverBlocked` 并停止自动写河；记牌页可录入 HUD/三家手数并点「恢复视觉写河」。OCR 接 HUD 后仍走同一对账逻辑。

## 规则与指标

- **RonAny（v1）**：`SituationAnalyzer` 与 `SichuanRulesEngine.ronSeats` 一致，语义为 **「∃ 至少一家能和」** 的 MC 频率。
- **一炮多响**：`RulesConfig.allowMultiRon` 默认 `true`；为 `false` 时引擎仅保留枚举序最小的一家和牌（兼容极少数房规）。
- **已胡离场**：`ronSeats` / `kongSeats` 支持 `inactiveSeats`（默认 `GameState.wonSeats` 传入 MC），已胡家不再参与荣和/明杠判定，与血战口径一致；牌例见 `SichuanRulesEngineTest`。

## 模型

- **manifest**：`assets/ml/xuezhan_mahjong_default/model_manifest.json` 为版本真值；`rulesHash` 须与 `RulesConfig.sha256FingerprintHex()` 一致，否则拒载策略模型。
- **策略 TFLite**：`PolicyFeatureV1`（81 维）+ manifest `rulesHash`；`TflitePolicyStudentInterpreter` 支持输出 **27**（仅听牌）或 **81**（含打出 Ron/杠）；无文件或校验失败时听牌与打出侧均回退 MC。
- **回退**：已实现——无策略文件 / `rulesHash` 或 `featureSchemaVersion` 不符 / 张量形状不符 / 推理异常时 `TflitePolicyStudentInterpreter` 返回 null，`SituationAnalyzer` 仅用 MC。**MC↔TFLite 在线抽检超阈值自动改走 MC** 仍为可选增强：离线脚本标定阈值后再接。

## 金帧与回归（可选自动化）

- **帧清单与场景**：`picture/README.md` 中 `1.png`–`13.png` 与场景摘要（换三张、碰杠窗、对局、结算等）即金帧素材索引；期望 `GameState`/河牌增量可由人工标注后录入独立回归脚本（CI 可选）。
- **仪器测试（已接）**：`GoldenFrameRiverDiffAndroidTest` 使用 `picture/5.png`（结算不得产事件）、`picture/2.png`（静止对局帧连续两次不得产事件）；`RiverDiffTableTracker` 暴露 `processFullFrameBitmap` 供解码位图直测。运行命令见 `docs/golden-frames.md`。
- **门禁与对账常数（血战默认皮肤，来自 `AppProfile.xuezhanDefault()`）**

| 项 | 初值 | 说明 |
|----|------|------|
| `visionMinClassifierConfidence` | 0.85 | 低于则不写入河牌（`applyDiscardIfTrusted`） |
| `visionStableFramesRequired` | 2 | 同座同牌连续帧数（`GatedTableTracker`） |
| `hudReconcileToleranceTiles` | 2 | HUD「剩张」与推算牌墙允许误差 |
| `hudReconcileMaxFailuresBeforeBlock` | 3 | 连续对账失败则 `visionAutoRiverBlocked` |
