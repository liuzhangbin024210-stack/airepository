# 牌面 27 类训练集（`tile_crops_v1`）

与 `tools/ml/train_tile_classifier_v1.py` 的 **`--data-dir`** 及 GitHub Actions **`train-tiles-tflite`** 约定一致。

## 目录结构

根目录下须含子目录 **`00` … `26`**（也支持 **`0` … `26`**，训练脚本会自动识别），与 `Tile.allTypes()` / `tile_schema_v1.TILE_ORDER_27` 下标一致：

| 索引 | 含义 |
|------|------|
| 00–08 | 万子 1–9（WAN1…WAN9） |
| 09–17 | 筒子 1–9（TONG1…TONG9） |
| 18–26 | 条子 1–9（TIAO1…TIAO9） |

每类目录内多张 **`.png` / `.jpg` / `.jpeg`**；图像会被缩放到 64×64 后训练。

## 从 `tools/png` 一键整理（推荐）

若你当前把图放在 `tools/png/`，且文件名为 `WAN1.png`、`TONG2.png` 等（**标签以文件名为准**，不要依赖父文件夹编号），在项目根执行：

```bash
python tools/ml/organize_tile_crops_by_filename.py --src tools/png --dst datasets/tile_crops_v1
```

将生成本目录下的 `00`…`26`，检查无误后 **提交并推送** 到 GitHub，再在 Actions 里运行 **train-tiles-tflite**。

## 在 GitHub 上训练

1. 确保仓库含 `datasets/tile_crops_v1/00`…`26`（每类至少一张图）。
2. **Actions** → **train-tiles-tflite** → **Run workflow**。
3. 可选填写 **epochs**（默认 30）、**data_dir**（默认 `datasets/tile_crops_v1`）。
4. 完成后在运行页下载制品 **`tiles-v1-tflite`**，解压得到 `tiles-v1.tflite`，拷入  
   `app/src/main/assets/ml/xuezhan_mahjong_default/`。

详细步骤见根目录 **`docs/CI-tiles-v1-github.md`**。

## 数据量与仓库体积

样本少时可直接 Git 提交；每类成百上千张时建议使用 **Git LFS** 或私有对象存储，在 workflow 里增加下载步骤（当前默认假设数据已随仓库检出）。
