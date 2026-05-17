# 用 GitHub Actions 获取 `tiles-v1.tflite`（方案 A）

在 **GitHub 云端 Linux** 上安装 TensorFlow、训练并导出 `tiles-v1.tflite`，再下载制品到本机 `assets`，适合本机 pip 安装 TensorFlow 过慢或失败的情况。

工作流文件：`.github/workflows/train-tiles-tflite.yml`（名称：**train-tiles-tflite**）。

## 与本仓库对应的 GitHub 远程（示例）

若本地工程要推到 **[liuzhangbin024210-stack/airepository](https://github.com/liuzhangbin024210-stack/airepository)**，可在项目根目录执行（首次推送前请先 `git init` 并 `git add` / `git commit`，且勿把 `local.properties`、密钥等提交上去）：

```bash
git remote add origin https://github.com/liuzhangbin024210-stack/airepository.git
git branch -M main
git push -u origin main
```

- 打开 **Actions** 的直达链接：  
  [https://github.com/liuzhangbin024210-stack/airepository/actions](https://github.com/liuzhangbin024210-stack/airepository/actions)  
- 若远程已存在且需改地址：`git remote set-url origin https://github.com/liuzhangbin024210-stack/airepository.git`

> 注意：GitHub 上该仓库当前文件树若与本地 `majiang` 不一致，请以 **本地为准** 将含 `.github/workflows/train-tiles-tflite.yml` 的完整工程推上去，或先在本机合并后再推送，否则 Actions 里看不到牌面训练工作流。

## 前置条件

1. 本工程已作为 **Git 仓库** 推送到 **GitHub**（任意私有/公有仓库均可）。
2. 仓库 **已包含** 上述 workflow 文件（与 `tools/ml/train_tile_classifier_v1.py` 等一起提交）。
3. 仓库 **Settings → Actions → General**：Workflow permissions 保持默认即可（能跑 `actions/checkout` 与 `upload-artifact`）。

> 若当前文件夹还没有 `.git`，可在项目根目录执行：  
> `git init` → `git add .` → `git commit -m "init"` → 在 GitHub 新建空仓库 → `git remote add origin <你的仓库 URL>` → `git push -u origin main`（分支名以你实际为准）。

## 训练数据集（真实牌面）

工作流会检查 **`datasets/tile_crops_v1`** 下是否存在 **`00` 或 `0`** 子目录：

- **有**：用真实图训练（手动运行默认 **30** epoch；因 **push** 触发的 CI 用 **12** epoch 做冒烟）。
- **无**：退回 **合成纹理** 占位模型（与真桌分布不一致，仅联调用）。

### 准备数据（本机一次即可）

1. 将牌面小图放在任意目录，文件名须为 **`WAN1`…`WAN9`、`TONG1`…`TONG9`、`TIAO1`…`TIAO9`**（扩展名 png/jpg 等），**标签以文件名为准**（与 `Tile.allTypes()`：万→筒→条 一致）。
2. 在项目根执行，生成标准布局 **`datasets/tile_crops_v1/00`…`26`**：

   ```bash
   python tools/ml/organize_tile_crops_by_filename.py --src tools/png --dst datasets/tile_crops_v1
   ```

   说明见 **`datasets/tile_crops_v1/README.md`**。
3. 将 `datasets/tile_crops_v1` **提交并推送** 到 GitHub（每类至少一张；样本多了可考虑 Git LFS）。

### 手动运行参数（`Run workflow`）

| 输入项 | 含义 | 默认 |
|--------|------|------|
| **data_dir** | 数据集根目录（其下须含 `00`…`26` 或 `0`…`26`） | `datasets/tile_crops_v1` |
| **epochs** | 训练轮数 | `30` |

## 操作步骤

1. 打开浏览器进入 GitHub 上的本仓库（例如 [airepository](https://github.com/liuzhangbin024210-stack/airepository)）。
2. 点击顶部 **Actions**。
3. 左侧选择工作流 **train-tiles-tflite**。
4. 右侧点击 **Run workflow**（若有分支下拉框，选 `main` / `master` 等默认分支）→ 可选填写 **data_dir** / **epochs** → **Run workflow**。
5. 等待黄色圆点变绿（约 3～10 分钟，视排队与网络而定）。
6. 点进该次运行记录，页面底部 **Artifacts** 区域会出现 **`tiles-v1-tflite`**，点击下载 ZIP。
7. 解压 ZIP，得到 **`tiles-v1.tflite`**（文件名须与 `model_manifest.json` 里 `tileClassifierFile` 一致）。
8. 将该文件复制到本机工程目录（与 manifest 同级）：

   `app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite`

9. 在 Android Studio 中 **Sync / Rebuild** 后安装运行应用。

## 命令行（可选）

若已安装 [GitHub CLI](https://cli.github.com/) 且已 `gh auth login`，在仓库根目录可执行：

```bash
gh workflow run train-tiles-tflite.yml -f data_dir=datasets/tile_crops_v1 -f epochs=30
gh run watch --workflow=train-tiles-tflite.yml
gh run download -n tiles-v1-tflite -D ./tile-artifact
```

再把 `tile-artifact/tiles-v1.tflite` 拷入上述 `assets` 路径即可。

## 说明

- 仓库 **未包含** `datasets/tile_crops_v1/00`（或 `0`）时，云端仍会用 **合成纹理** 训练，产物为 **占位模型**，真桌识别不可靠；按上文准备好数据集并推送后，同一工作流即会改用真实图。
- 工作流也会在 **推送** 改动 `tools/ml/train_tile_classifier_v1.py`、`tools/ml/tile_schema_v1.py` 或本 workflow 文件时自动运行；若不想自动跑，可只保留 `workflow_dispatch` 触发方式（编辑 YAML 的 `on:` 段）。
