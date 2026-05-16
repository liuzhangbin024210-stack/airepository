# 离线蒸馏与 TFLite 产出

MC 教师数据批次命名与落盘约定：**[datasets/README.md](datasets/README.md)**。

## 训练脚手架（策略 v1）

1. 安装依赖：`pip install -r tools/ml/requirements.txt`（建议 Python 3.10+）。
2. 特征与输出维度常量与 Kotlin **必须一致**：见 `tools/ml/policy_schema.py`（与 `PolicyFeatureV1` 同步维护）。
3. 一键合成数据训练 + 导出单张量 81 维 TFLite：
   ```bash
   python tools/ml/train_policy_v1.py --out build/policy-v1.tflite --epochs 3
   ```
   将生成的 `policy-v1.tflite` 拷入 `app/src/main/assets/ml/xuezhan_mahjong_default/`。脚本内为 **多输出训练**（hu softmax + ron/kong sigmoid），导出时 **Concat** 为与端侧一致的 **81 维单输出**。

## 策略张量契约（与 `TflitePolicyStudentInterpreter` 一致）

1. **输入**：float32，元素个数 **81**（`PolicyFeatureV1.build`）。
2. **输出**（二选一）：
   - **27**：仅听牌/进张头（softmax）；打出侧无学生估计，仅 MC。
   - **81**：`[0,27)` 听牌 softmax；`[27,54)` RonAny（sigmoid 或 \[0,1\] 概率）；`[54,81)` 点杠（同上）。
3. 更新同目录 `model_manifest.json`：`modelVersion`、`featureSchemaVersion`、`rulesHash`（与 `RulesConfig.sha256FingerprintHex()` 一致）、`appId`。
4. 无文件或校验失败时端侧回退纯 MC；与 MC 抽检偏差监控等见 `docs/ACCEPTANCE-IMPLEMENTATION.md`。

## 教师批次与 MC↔TFLite 抽检（离线）

- **批次落盘**：见 `datasets/README.md`（`batch_<rulesHash8>_<date>/` + `meta.json`）。
- **Gradle 校验**：`:app:verifyMlManifest`（已挂到 `preBuild`）在 manifest 声明了 `policyFile` / `tileClassifierFile` 时检查文件是否存在于 `assets/ml/{appId}/`；缺失则 **WARN**，不阻断构建（占位 manifest 可保留文件名待拷入模型）。

## 牌面分类（视觉写河）

1. **标签顺序**：与 Kotlin `Tile.allTypes()` 一致，见 `tools/ml/tile_schema_v1.py`（万 1–9 → 筒 1–9 → 条 1–9）。
2. **张量契约**：输入 float32，`[1,H,W,3]` NHWC 或 `[1,3,H,W]` NCHW；输出 **27** 维 softmax（与 `TfliteTileClassifier` 一致）。
3. **一键合成数据训练 + 导出**（占位权重，仅保证端侧可加载；**真实牌桌须换真实数据重训**）：
   ```bash
   pip install -r tools/ml/requirements.txt
   python tools/ml/train_tile_classifier_v1.py
   ```
   默认写出到 `app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite`（可用 `--out` 改路径）。
4. `model_manifest.json` 中 `tileClassifierFile` 须与文件名一致（如 `"tiles-v1.tflite"`）。未设置或加载失败时 `TfliteTileClassifier` 回退为低置信度，门禁不自动写河。
5. **CI / 本机安装慢**：仓库提供 GitHub Actions `train-tiles-tflite`（Linux 上 pip + 训练 + 上传制品）；**逐步操作**见 **[`docs/CI-tiles-v1-github.md`](../docs/CI-tiles-v1-github.md)**。Windows 可用 `tools/ml/run_train_tiles.ps1` 创建 `.venv-tiles` 再训练。
