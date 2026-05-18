#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将 Camerash/mahjong-dataset 的 train.zip 转为与本仓 ``tile_schema_v1`` 一致的 27 类目录结构。

数据来源（MIT）：https://github.com/camerash/mahjong-dataset
默认从子模块 ``third_party/mahjong-dataset/train.zip`` 读取；若无子模块可使用 ``--download``。

数据集 ``label`` 1–27 为三门数牌；28–42 为字/花，四川 108 张不使用，本脚本跳过。

标签映射（数据集 label → 子目录 ``00``–``26``，与 Kotlin ``Tile.allTypes()`` 一致）：

- **19–27**（characters / 万）→ ``00``–``08``
- **1–9**（dots / 筒）→ ``09``–``17``
- **10–18**（bamboo / 条）→ ``18``–``26``
"""

from __future__ import annotations

import argparse
import csv
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path
from urllib.request import urlretrieve

_REPO_ROOT = Path(__file__).resolve().parents[2]
_ML_DIR = Path(__file__).resolve().parent
if str(_ML_DIR) not in sys.path:
    sys.path.insert(0, str(_ML_DIR))

from tile_schema_v1 import NUM_CLASSES  # noqa: E402

DEFAULT_TRAIN_ROOT = _REPO_ROOT / "third_party" / "mahjong-dataset"
DEFAULT_DST = _REPO_ROOT / "datasets" / "tile_crops_camerash_v1"
DEFAULT_CACHE = _REPO_ROOT / "datasets" / "_cache" / "camerash"
TRAIN_ZIP_NAME = "train.zip"
DOWNLOAD_URL = (
    "https://github.com/camerash/mahjong-dataset/raw/master/train.zip"
)


def _normalize_field(name: str) -> str:
    """CSV 表头归一化：去 BOM、空白，统一小写与连字符。"""
    return name.strip().lstrip("\ufeff").lower().replace("_", "-")


def dataset_label_to_class_index(label: int) -> int | None:
    """
    将 data.csv 中的 ``label``（1–42）映射为本仓 27 类下标；非数牌返回 None。

    :param label: 数据集原始类别编号。
    :returns: ``0..26`` 或 ``None``（字牌/花牌等跳过）。
    """
    if 19 <= label <= 27:
        return label - 19
    if 1 <= label <= 18:
        return label + 8
    return None


def _find_zip(train_root: Path, zip_path: Path | None, download: bool, cache_dir: Path) -> Path:
    """解析最终使用的 ``train.zip`` 本地路径（必要时下载）。"""
    if zip_path is not None:
        p = zip_path.resolve()
        if not p.is_file():
            raise FileNotFoundError(f"指定的 zip 不存在: {p}")
        return p
    cand = (train_root / TRAIN_ZIP_NAME).resolve()
    if cand.is_file():
        return cand
    if not download:
        raise FileNotFoundError(
            f"未找到 {cand}。请先执行: git submodule update --init --recursive\n"
            "或传入 --zip <路径>，或加 --download 从 GitHub 拉取 train.zip。"
        )
    cache_dir.mkdir(parents=True, exist_ok=True)
    out = cache_dir / TRAIN_ZIP_NAME
    print(f"正在下载: {DOWNLOAD_URL}\n保存到: {out}")
    urlretrieve(DOWNLOAD_URL, str(out))
    return out.resolve()


def _extract_zip(zip_file: Path, extract_to: Path) -> None:
    """解压 ``train.zip``（含 ``data.csv`` 与 ``images/``）。"""
    if extract_to.exists():
        shutil.rmtree(extract_to)
    extract_to.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_file, "r") as zf:
        zf.extractall(extract_to)
    csv_path = extract_to / "data.csv"
    if not csv_path.is_file():
        raise FileNotFoundError(f"解压后缺少 data.csv: {csv_path}")
    img_dir = extract_to / "images"
    if not img_dir.is_dir():
        raise FileNotFoundError(f"解压后缺少 images 目录: {img_dir}")


def _prepare_dst_dirs(dst: Path, clear: bool) -> None:
    """创建 ``00``–``26`` 目录；``clear`` 时先删除各类内已有图像。"""
    dst.mkdir(parents=True, exist_ok=True)
    for c in range(NUM_CLASSES):
        sub = dst / f"{c:02d}"
        sub.mkdir(parents=True, exist_ok=True)
        if clear:
            for pat in ("*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"):
                for f in sub.glob(pat):
                    f.unlink()


def _read_rows(csv_path: Path) -> list[dict[str, str]]:
    """读取 CSV，返回字典列表（表头已归一化键）。"""
    with csv_path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames:
            raise ValueError("data.csv 无表头")
        key_map = {_normalize_field(k): k for k in reader.fieldnames}
        # 期望列：image-name, label, label-name
        name_key = key_map.get("image-name") or key_map.get("image name")
        label_key = key_map.get("label")
        if not name_key or not label_key:
            raise ValueError(
                f"data.csv 表头须含 image-name 与 label，实际为: {reader.fieldnames}"
            )
        rows: list[dict[str, str]] = []
        for raw in reader:
            rows.append(
                {
                    "image-name": (raw.get(name_key) or "").strip(),
                    "label": (raw.get(label_key) or "").strip(),
                }
            )
        return rows


def main() -> None:
    parser = argparse.ArgumentParser(
        description="从 Camerash train.zip 生成 tile_crops 目录（00..26）"
    )
    parser.add_argument(
        "--train-root",
        type=str,
        default=str(DEFAULT_TRAIN_ROOT),
        help="放置 train.zip 的目录（默认子模块 third_party/mahjong-dataset）",
    )
    parser.add_argument(
        "--zip",
        type=str,
        default=None,
        dest="zip_path",
        help="直接指定 train.zip 路径（优先于 --train-root）",
    )
    parser.add_argument(
        "--dst",
        type=str,
        default=str(DEFAULT_DST),
        help="输出根目录（默认 datasets/tile_crops_camerash_v1）",
    )
    parser.add_argument(
        "--cache-dir",
        type=str,
        default=str(DEFAULT_CACHE),
        help="--download 时保存 zip / 解压目录的缓存根（默认 datasets/_cache/camerash）",
    )
    parser.add_argument(
        "--download",
        action="store_true",
        help="当子模块内无 train.zip 时从 GitHub 下载到 --cache-dir",
    )
    parser.add_argument(
        "--clear",
        action="store_true",
        help="开始前清空各类目录中已有 jpg/png",
    )
    parser.add_argument(
        "--max-per-class",
        type=int,
        default=0,
        help="每类最多复制张数；0 表示不限制（用于快速试验）",
    )
    args = parser.parse_args()

    train_root = Path(args.train_root).resolve()
    dst = Path(args.dst).resolve()
    cache_dir = Path(args.cache_dir).resolve()
    zip_arg = Path(args.zip_path).resolve() if args.zip_path else None

    zip_file = _find_zip(train_root, zip_arg, args.download, cache_dir)

    # 解压到缓存子目录，避免污染子模块工作树
    extract_parent = cache_dir / "train_unzipped"
    extract_parent.mkdir(parents=True, exist_ok=True)
    extract_to = extract_parent / "content"
    print(f"解压: {zip_file}\n到: {extract_to}")
    _extract_zip(zip_file, extract_to)

    _prepare_dst_dirs(dst, args.clear)

    rows = _read_rows(extract_to / "data.csv")
    images_dir = extract_to / "images"

    per_class_counts = [0] * NUM_CLASSES
    skipped_non_suit = 0
    skipped_missing_file = 0
    skipped_cap = 0

    max_per = max(0, int(args.max_per_class))

    for row in rows:
        name = row["image-name"]
        if not name:
            continue
        try:
            label = int(row["label"])
        except ValueError:
            continue
        class_idx = dataset_label_to_class_index(label)
        if class_idx is None:
            skipped_non_suit += 1
            continue
        if max_per and per_class_counts[class_idx] >= max_per:
            skipped_cap += 1
            continue
        src = images_dir / name
        if not src.is_file():
            skipped_missing_file += 1
            continue
        dest_dir = dst / f"{class_idx:02d}"
        dest = dest_dir / name
        # 同名冲突时加后缀，避免覆盖
        if dest.exists():
            stem = Path(name).stem
            suf = Path(name).suffix or ".jpg"
            n = 1
            while True:
                alt = dest_dir / f"{stem}_dup{n}{suf}"
                if not alt.exists():
                    dest = alt
                    break
                n += 1
        shutil.copy2(src, dest)
        per_class_counts[class_idx] += 1

    empty_classes = [i for i, n in enumerate(per_class_counts) if n == 0]
    if empty_classes:
        raise RuntimeError(
            f"以下类别无图像，train_tile_classifier_v1 将失败: {empty_classes}\n"
            f"每类张数: {per_class_counts}"
        )

    print("完成。各类样本数:", per_class_counts)
    print(f"跳过（非 1–27 数牌）行: {skipped_non_suit}")
    print(f"跳过（磁盘无对应文件）: {skipped_missing_file}")
    if max_per:
        print(f"因 --max-per-class 跳过: {skipped_cap}")
    print(f"输出目录: {dst}")


if __name__ == "__main__":
    main()
