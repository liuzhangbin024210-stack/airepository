# 性能专项（后置，不阻塞 v1 闭环）

## 已做（轻量）

- **MC 分析防抖**：`GameViewModel.runMcAnalysis` 在 `MC_ANALYSIS_DEBOUNCE_MS`（350ms）内合并连点，减少重复 MC 与 UI 抖动。

## 仍待专项

- 相机管线 `debounce`、分析帧 stride、MC 并行化、`INT8` TFLite、相机分析 FPS 与分辨率档位。
- 低端机 MC 耗时摸底与「仅听牌 / 仅打出」分模式降载。
