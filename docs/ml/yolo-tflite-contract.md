# 整桌 YOLO → TFLite 与端侧解码约定（四川血战 27 类）

本文件与 [`vision-yolo` 模块](../../vision-yolo/README.md) 中 [`TfliteYoloTileDetector`](../../vision-yolo/src/main/java/com/majiang/counter/vision/yolo/TfliteYoloTileDetector.kt) 对齐，供 **Ultralytics 等工具链导出** 时对照。

## manifest（`assets/ml/{appId}/model_manifest.json`）

可选键（未设置则整桌检测不加载模型，行为为「不可用」并走小图分类）：

| 键 | 类型 | 说明 |
|----|------|------|
| `tileDetectorFile` | string | 与 `tileClassifierFile` 同级文件名，如 `yolo-tiles-v1.tflite` |
| `tileDetectorInputWidth` | int | 默认 `640` |
| `tileDetectorInputHeight` | int | 默认 `640` |

**勿**将现有 `tiles-v1.tflite`（27 类 **小图分类**）填到 `tileDetectorFile`：输出形状不同，加载会失败并跳过整桌路径。

## 输入

- **float32**
- **batch = 1**
- **NHWC**：`[1, H, W, 3]`，RGB，像素值 **归一化到 \[0,1\]**（与 `TfliteTileClassifier` 一致）
- 或 **NCHW**：`[1, 3, H, W]`，同上归一化

其中 `H`、`W` 须与 manifest 中宽高一致。端侧对相机帧做 **letterbox**（灰边 114）后缩放至 `H×W` 再喂入模型。

## 输出（单张量）

- 形状 **`[1, N, 31]`** 或 **`[1, 31, N]`**，`N` 为候选数（与导出锚点数一致）。
- **`31 = 4 + 27`**：前 4 维为框，后 27 维为 **各类 logit**（端侧 `sigmoid` 后取 argmax 作为类别与置信度）。
- 框定义：与模型 **输入像素坐标** 一致（`0..W`、`0..H` 量级），语义为 **中心 cx、cy 与宽 bw、高 bh**（与常见 YOLO 头一致）。若你方导出为 xyxy，须在 **导出脚本** 中改为上述形式，或在 Kotlin 侧另写解码分支（需改代码并加单测）。

## 类别顺序

`classIndex` **0..26** 须与 Kotlin `Tile.allTypes()` / `tools/ml/tile_schema_v1.py` 一致：**万 1–9 → 筒 1–9 → 条 1–9**。

## NMS 与门禁

- 模块内对同类框做 IoU-NMS（默认 IoU 0.45、每类最多 8 框）；再经 `AppProfile.visionWholeTableDetectorMinConfidence` 与四河 ROI 几何过滤后，才可能替代小图分类写入事件。

## 训练侧建议

- 使用 **血战 App 录屏/截图** 标注整桌牌框，`nc=27`。
- 导出后在本仓库跑仪器测试 / 金帧，确认 `verifyMlManifest` 能通过且 `RiverDiffTableTracker` 在开启 `visionUseWholeTableDetector` 时行为符合映射表。
