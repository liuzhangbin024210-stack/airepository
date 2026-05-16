# 发版评审检查清单（摘自修订计划，执行时逐项勾选）

## A. 真值与视觉关键路径

- [x] `picture/` 或等价素材在仓库内固定路径
- [x] 像素 ROI 与《画面字段 → GameState 映射表》完成（初版：2556×1179；相机可微调）
- [x] 中央最近打出与四边 `river_*` 去重规则已文档化

## B. 门禁、对账与回归

- [x] 自动写河牌：`AppProfile` 稳定帧 + 置信度阈值 + `GatedTableTracker` / `applyDiscardIfTrusted`
- [x] HUD 守恒对账失败 K 次降级：`GameViewModel.processCameraFrame` + `hudReconcileMaxFailuresBeforeBlock`
- [x] 金帧/录屏回归集（可选）：`picture/README.md` 帧清单；自动化脚本可选，见 `docs/ACCEPTANCE-IMPLEMENTATION.md`

## C. 规则与指标

- [x] README / `RulesConfig` / 代码注释对「当前子集」表述一致（发版前再扫一遍）
- [x] 一炮多响与 `ronSeats` 单测覆盖（`SichuanRulesEngineTest`）
- [x] `RonAny` = ∃ 至少一家能和 在引擎、MC、README 一致

## D. MC、蒸馏与 TFLite

- [x] v1 TFLite 输出范围：`tools/ml/README.md`（听牌侧 27 维）；打出侧仍以 MC
- [x] `model_manifest.json` 字段约定：`assets/ml/.../model_manifest.json`；可选 Gradle `verifyMlManifest`
- [x] MC 回退：无文件/校验失败/形状不符见 `TflitePolicyStudentInterpreter`；在线抽检阈值登记于 `docs/ACCEPTANCE-IMPLEMENTATION.md`（离线标定后启用）

## E. 单一事实来源

- [x] 执行主文档为修订版计划；旧版计划已加注「以修订版为准」
- [x] README 与实现一致（发版前再扫一遍）
