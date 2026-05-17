# 离线蒸馏与 TFLite 产出

MC 教师数据批次命名与落盘约定：**[datasets/README.md](datasets/README.md)**。

## 训练脚手架（策略 v1）

1. 安装依赖：`pip install -r tools/ml/requirements.txt`（建议 Python 3.10+）。
2. 特征与输出维度常量与 Kotlin **必须一致**：见 `tools/ml/policy_schema.py`（与 `PolicyFeatureV1` 同步维护）。
3. 一键合成数据训练 + 导出单张量 81 维 TFLite：
   ```bash
   python tools/ml/train_policy_v1.py --epochs 3
   ```
   默认写出到 `app/src/main/assets/ml/xuezhan_mahjong_default/policy-v1.tflite`（可用 `--out` 改路径）。脚本为 **多输出训练**（hu softmax + ron/kong sigmoid），导出时 **Concat** 为与端侧一致的 **81 维单输出**（float32，无整型量化）。
4. **GitHub Actions**（本机 pip 慢/失败时）：工作流 **`train-policy-tflite`**（`.github/workflows/train-policy-tflite.yml`）在 Linux 上训练并上传制品 **`policy-v1-tflite`**。步骤与牌面类似，见 **[`docs/CI-policy-v1-github.md`](../docs/CI-policy-v1-github.md)**。Windows 可用 **`tools/ml/run_train_policy.ps1`**（创建 `.venv-policy`）。

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

### 可行性说明

- **小图 27 分类 + TFLite 端侧推理** 本身是成熟方案；当前仓库若仍「认不出牌」，首要原因是 **训练数据**：默认脚本只在 **合成纹理** 上收敛，与真实麻将牌面 **分布不同**，权重不能迁移到真机画面。
- **要做成可用**：采集（或渲染）与 App 裁切 ROI 相近的牌面图块，按类放入子目录 **`00` … `26`**（与 `tile_schema_v1.TILE_ORDER_27` 下标一致：0=万1 … 26=条9），用 `--data-dir` 训练后再拷入 `assets/ml/{appId}/`。

### 训练步骤

1. **标签顺序**：与 Kotlin `Tile.allTypes()` 一致，见 `tools/ml/tile_schema_v1.py`（万 1–9 → 筒 1–9 → 条 1–9）。
2. **张量契约**：输入 float32，`[1,H,W,3]` NHWC 或 `[1,3,H,W]` NCHW；输出 **27** 维 softmax（与 `TfliteTileClassifier` 一致）。
3. **合成占位（仅工程联调，不能用于真桌认牌）**：
   ```bash
   pip install -r tools/ml/requirements.txt
   python tools/ml/train_tile_classifier_v1.py
   ```
4. **真实牌面数据（产品路径）**：  
   ```bash
   python tools/ml/train_tile_classifier_v1.py --data-dir path/to/tile_crops_root --epochs 30
   ```  
   目录结构：`tile_crops_root/00/*.png` … `tile_crops_root/26/*.png`（每类尽量多张、含光照与轻微角度变化）。若源图按 `WAN1.png` 等命名但父目录编号不可靠，可先执行  
   `python tools/ml/organize_tile_crops_by_filename.py --src tools/png --dst datasets/tile_crops_v1`  
   再 `--data-dir datasets/tile_crops_v1`（与 GitHub Actions 默认路径一致，见 `datasets/tile_crops_v1/README.md`）。
5. 默认写出到 `app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite`（可用 `--out` 改路径）。
6. `model_manifest.json` 中 `tileClassifierFile` 须与文件名一致（如 `"tiles-v1.tflite"`）。未设置或加载失败时 `TfliteTileClassifier` 回退为低置信度，门禁不自动写河。
7. **CI / 本机安装慢**：仓库提供 GitHub Actions `train-tiles-tflite`（Linux 上 pip + 训练 + 上传制品）；**逐步操作**见 **[`docs/CI-tiles-v1-github.md`](../docs/CI-tiles-v1-github.md)**。Windows 推荐 **`tools/ml/run_train_tiles.ps1`**（创建 `.venv-tiles`、用 `--no-cache-dir` 装 CPU 版 TF）；训练参数可追加在脚本后，例如  
   `.\tools\ml\run_train_tiles.ps1 --data-dir F:\AI\majiang\datasets\tile_crops_v1 --epochs 30`（请先用 `organize_tile_crops_by_filename.py` 从 `tools/png` 生成该目录，见上条）。

### Windows：`ModuleNotFoundError: tensorflow` 或 `Wheel ... is invalid`

1. **推荐**：在项目根执行 **`install_tiles_venv.ps1`**（创建 `.venv-tiles`、升级 pip、`--no-cache-dir` 安装 `tensorflow-cpu`；指定 `-Mirror` 时会自动加 `--trusted-host`）：
   ```powershell
   cd F:\AI\majiang
   .\tools\ml\install_tiles_venv.ps1
   .\tools\ml\install_tiles_venv.ps1 -Mirror https://pypi.tuna.tsinghua.edu.cn/simple
   .\tools\ml\install_tiles_venv.ps1 -Mirror https://mirrors.aliyun.com/pypi/simple/
   ```
   成功后训练：
   ```powershell
   .\.venv-tiles\Scripts\python.exe tools/ml/train_tile_classifier_v1.py --data-dir F:\AI\majiang\tools\png --epochs 30
   ```
2. **`invalid wheel` / `IncompleteRead`**：大包（约 300MB+）易断线，请 **多执行几次** 安装脚本或 `pip install`。手动安装时务必加 **`--trusted-host`**（与 `-i` 镜像主机一致，并加上 `files.pythonhosted.org`），例如：  
   `.\.venv-tiles\Scripts\pip.exe install --no-cache-dir --retries 25 --timeout 300 -r tools/ml/requirements-tiles-cpu.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com --trusted-host files.pythonhosted.org`
3. **Python 版本**：Windows 上 TF 2.14+ 官方轮常见为 **64 位 CPython 3.10–3.12**；若为 3.13 或 32 位解释器，请换 64 位 3.10–3.12。
