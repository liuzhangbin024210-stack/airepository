# 用 GitHub Actions 获取 `policy-v1.tflite`

在 **GitHub 云端 Linux** 上安装 TensorFlow、用合成数据训练并导出 **81 维单输出** `policy-v1.tflite`，再下载制品到本机 `assets`，适合本机 pip 安装 TensorFlow 过慢或失败的情况。

工作流文件：`.github/workflows/train-policy-tflite.yml`（名称：**train-policy-tflite**）。

## 前置条件

1. 仓库已推送至 GitHub，且包含上述 workflow 与 `tools/ml/train_policy_v1.py`、`tools/ml/policy_schema.py`。
2. 仓库 **Settings → Actions → General**：Workflow permissions 保持默认即可。

## 操作步骤

1. 打开仓库 **Actions**。
2. 左侧选择 **train-policy-tflite**。
3. 右侧 **Run workflow** → 选默认分支（如 `main`）→ **Run workflow**。
4. 等待运行成功（通常数分钟）。
5. 进入该次运行，在 **Artifacts** 下载 **`policy-v1-tflite`** ZIP。
6. 解压得到 **`policy-v1.tflite`**，复制到：

   `app/src/main/assets/ml/xuezhan_mahjong_default/policy-v1.tflite`

7. 确认同目录 **`model_manifest.json`** 中：
   - `policyFile` 为 `"policy-v1.tflite"`；
   - `rulesHash` 与当前 `RulesConfig` 默认指纹一致（默认 `true,false,false,false` 的 SHA-256，与工程内 manifest 一致即可）；
   - `featureSchemaVersion` 为 **1**（与 `PolicyFeatureV1.FEATURE_SCHEMA_VERSION` 一致）。

8. Android Studio **Sync / Rebuild** 后安装运行；无文件或校验失败时 `TflitePolicyStudentInterpreter` 回退纯 MC。

> **说明**：当前脚本为 **合成占位** 权重，端侧可加载并与特征维绑定；**真实对局质量**需用 MC 教师数据替换 `synthetic_dataset` 后重训，再跑本工作流。

## 命令行（可选）

已安装 [GitHub CLI](https://cli.github.com/) 且 `gh auth login` 后，在仓库根目录：

```bash
gh workflow run train-policy-tflite.yml
gh run watch --workflow=train-policy-tflite.yml
gh run download -n policy-v1-tflite -D ./policy-artifact
```

将 `policy-artifact/policy-v1.tflite` 拷入上述 `assets` 路径即可。
