# vision-yolo 模块

本目录为 **Android Library**：承载「整桌 / 整帧」麻将牌 **检测**（YOLO 导出 TFLite 等）的 **Kotlin 推理与后处理**，与 `app` 内 [`TileClassifier`](../app/src/main/java/com/majiang/counter/vision/VisionInterfaces.kt)（单张小图 27 分类）**并存**。

## 设计说明

- **不**将 `YOLOv11ForMahjong-main` 等 Python 训练仓整树拷入 `app/src`；训练脚本与 `.pt` 留在独立路径或仓库，本模块只接 **已导出** 的端侧模型与解码逻辑。
- 检测输出 [`TileDetection`](src/main/java/com/majiang/counter/vision/yolo/TileDetection.kt) 的 `classIndex` 与 `Tile.allTypes()` / `tools/ml/tile_schema_v1.py` **0..26** 一致（四川血战 108 张，无字牌）。
- [`TfliteYoloTileDetector`](src/main/java/com/majiang/counter/vision/yolo/TfliteYoloTileDetector.kt)：按 `model_manifest.json` 可选加载 `tileDetectorFile`；输出形状不符时自动禁用。解码与 NMS 约定见仓库根目录 [`docs/ml/yolo-tflite-contract.md`](../docs/ml/yolo-tflite-contract.md)。
- `app` 侧 [`AppModule`](../app/src/main/java/com/majiang/counter/di/AppModule.kt) 注入 [`TileDetector`](src/main/java/com/majiang/counter/vision/yolo/TileDetector.kt)；[`RiverDiffTableTracker`](../app/src/main/java/com/majiang/counter/vision/RiverDiffTableTracker.kt) 在 `AppProfile.visionUseWholeTableDetector` 为 true 且检测模型可用时，优先用整桌检测替代河 ROI 小图分类。

## 后续工作（见 `.cursor/plans/yolov11_整桌检测集成.plan.md`）

1. 血战域数据重训 **nc=27** 并导出与本文档契约一致的 TFLite。  
2. ~~在本模块实现 `TfliteYoloTileDetector`~~（骨架已就绪；按实际导出张量微调解码若有差异）。  
3. ~~扩展 `model_manifest.json`~~（已支持可选 `tileDetectorFile` / `tileDetectorInputWidth` / `tileDetectorInputHeight`）。  
4. ~~与 `RiverDiffTableTracker` / 回退~~（已接开关 `visionUseWholeTableDetector`；门禁仍走 `GatedTableTracker`）。

## 许可

训练参考工程若使用 Ultralytics，须在发版前核对其与 APK 分发条款；本模块代码以本仓库根目录许可为准。
