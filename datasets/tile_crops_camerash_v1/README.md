# Camerash 牌面 27 类训练集（`tile_crops_camerash_v1`）

由脚本从上游 **[Camerash/mahjong-dataset](https://github.com/camerash/mahjong-dataset)** 的 `train.zip`（**MIT**）生成，目录 `00`–`26` 与 [`tools/ml/tile_schema_v1.py`](../../tools/ml/tile_schema_v1.py) / Kotlin `Tile.allTypes()` 一致。

## 克隆本仓库后

若使用子模块（推荐）：

```bash
git submodule update --init --recursive
```

## 生成 `00`–`26`（本机）

在项目根执行（默认读取 `third_party/mahjong-dataset/train.zip`；无子模块时加 `--download`）：

```bash
python tools/ml/prepare_camerash_tile_crops.py --clear
```

输出到本目录；**`.jpg` 等图像默认被 `.gitignore` 忽略**，可重复生成，无需提交到 Git。

## 训练

```bash
python tools/ml/train_tile_classifier_v1.py --data-dir datasets/tile_crops_camerash_v1 --epochs 30
```

## 说明

- 仅使用数据集中 **label 1–27**（万/筒/条数牌）；字牌与花牌（28–42）已跳过。
- 与目标血战 **App 内 ROI 截图** 仍可能存在域差异；自动写河依赖端侧置信度门禁，必要时需用本游戏裁切图微调。
