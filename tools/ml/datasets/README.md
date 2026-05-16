# MC 教师数据集目录约定（版本化）

与修订计划 **§蒸馏与模型治理 → 教师数据版本化** 对齐：离线生成的 `(特征, 标签)` 批次**不入主仓大文件**，在本目录或团队共享存储按规则分桶。

## 目录命名

```
datasets/batch_<rulesHash前8位>_<YYYYMMDD>/
  meta.json          # appId、rulesHash 全量、featureSchemaVersion、mcIterations、随机种子
  features.npz       # 或 CSV / TFRecord，与训练脚本一致
  labels.npz         # 建议键：`y_hu` [N,27] softmax 目标；`y_ron`、`y_kong` 各 [N,27] 的 0..1 存在性（与 MC RonAny/点杠频率对齐）
```

- **`rulesHash`**：与运行时 `RulesConfig.sha256FingerprintHex()` 一致；变更后须新开 batch，避免与旧 manifest 混训。
- **`featureSchemaVersion`**：须与 `PolicyFeatureV1.FEATURE_SCHEMA_VERSION` 一致。

## 与 assets 的对应关系

训练完成后将 `policy-*.tflite` 与更新后的 `model_manifest.json` 拷入 `app/src/main/assets/ml/{appId}/`，并记录本 batch 路径于发版说明或 CI 制品元数据。
