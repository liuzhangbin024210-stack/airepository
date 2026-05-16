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

## 操作步骤

1. 打开浏览器进入 GitHub 上的本仓库（例如 [airepository](https://github.com/liuzhangbin024210-stack/airepository)）。
2. 点击顶部 **Actions**。
3. 左侧选择工作流 **train-tiles-tflite**。
4. 右侧点击 **Run workflow**（若有分支下拉框，选 `main` / `master` 等默认分支）→ **Run workflow**。
5. 等待黄色圆点变绿（约 3～10 分钟，视排队与网络而定）。
6. 点进该次运行记录，页面底部 **Artifacts** 区域会出现 **`tiles-v1-tflite`**，点击下载 ZIP。
7. 解压 ZIP，得到 **`tiles-v1.tflite`**（文件名须与 `model_manifest.json` 里 `tileClassifierFile` 一致）。
8. 将该文件复制到本机工程目录（与 manifest 同级）：

   `app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite`

9. 在 Android Studio 中 **Sync / Rebuild** 后安装运行应用。

## 命令行（可选）

若已安装 [GitHub CLI](https://cli.github.com/) 且已 `gh auth login`，在仓库根目录可执行：

```bash
gh workflow run train-tiles-tflite.yml
gh run watch --workflow=train-tiles-tflite.yml
gh run download -n tiles-v1-tflite -D ./tile-artifact
```

再把 `tile-artifact/tiles-v1.tflite` 拷入上述 `assets` 路径即可。

## 说明

- 当前脚本使用 **合成纹理** 训练，产物为 **占位模型**：端侧可正常加载，**不保证真实牌桌识别准确**；后续可用真实标注牌面图替换数据源后重训并再次跑该工作流。
- 工作流也会在 **推送** 改动 `tools/ml/train_tile_classifier_v1.py`、`tools/ml/tile_schema_v1.py` 或本 workflow 文件时自动运行；若不想自动跑，可只保留 `workflow_dispatch` 触发方式（编辑 YAML 的 `on:` 段）。
