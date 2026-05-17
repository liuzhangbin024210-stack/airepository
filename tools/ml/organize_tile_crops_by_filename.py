#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将散落的牌面小图按 **文件名** 归类到训练用目录 ``00``…``26``。

**为何需要**：若仅用「父文件夹编号」当标签，容易与 Kotlin ``Tile.allTypes()``（万→筒→条）
不一致；例如 ``TIAO1`` 应对应索引 **18**，而不是文件夹名 ``9``。

本脚本根据文件名前缀 ``WAN`` / ``TONG`` / ``TIAO`` 与点数 ``1``…``9`` 查 ``tile_schema_v1.TILE_ORDER_27``，
复制到 ``dst/00`` … ``dst/26``，供 ``train_tile_classifier_v1.py --data-dir`` 与 GitHub Actions 使用。
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

_ML_DIR = Path(__file__).resolve().parent
if str(_ML_DIR) not in sys.path:
    sys.path.insert(0, str(_ML_DIR))

from tile_schema_v1 import TILE_ORDER_27

_NAME_RE = re.compile(r"^(WAN|TONG|TIAO)(\d)$", re.IGNORECASE)
_IMAGE_SUFFIX = {".png", ".jpg", ".jpeg", ".webp", ".PNG", ".JPG", ".JPEG", ".WEBP"}


def _class_index_from_stem(stem: str) -> int:
    m = _NAME_RE.match(stem.strip())
    if not m:
        raise ValueError(f"无法从文件名解析花色与点数: {stem!r}（须匹配 WAN1…TIAO9）")
    suit, rank_s = m.group(1).upper(), m.group(2)
    rank = int(rank_s)
    key = (suit, rank)
    if key not in TILE_ORDER_27:
        raise ValueError(f"非法牌型: {key}")
    return TILE_ORDER_27.index(key)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="按 WAN/TONG/TIAO+点数 文件名整理牌面图到 00..26 目录"
    )
    parser.add_argument(
        "--src",
        type=str,
        required=True,
        help="源根目录（递归搜索图片；父文件夹编号不参与标签）",
    )
    parser.add_argument(
        "--dst",
        type=str,
        default="datasets/tile_crops_v1",
        help="输出根目录（将创建/覆盖 00..26 子目录）",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="只打印计划，不写盘",
    )
    args = parser.parse_args()

    src_root = Path(args.src).resolve()
    dst_root = Path(args.dst).resolve()
    if not src_root.is_dir():
        raise FileNotFoundError(f"源目录不存在: {src_root}")

    planned: list[tuple[Path, Path, int]] = []
    for path in sorted(src_root.rglob("*")):
        if not path.is_file() or path.suffix not in _IMAGE_SUFFIX:
            continue
        try:
            cls = _class_index_from_stem(path.stem)
        except ValueError as e:
            print(f"跳过（{e}）: {path}")
            continue
        sub = dst_root / f"{cls:02d}"
        dest = sub / path.name
        # 同名则追加序号，避免覆盖
        if any(d == dest for _, d, _ in planned):
            base = path.stem
            suf = path.suffix
            k = 2
            while any(d == (sub / f"{base}_{k}{suf}") for _, d, _ in planned):
                k += 1
            dest = sub / f"{base}_{k}{suf}"
        planned.append((path, dest, cls))

    if not planned:
        raise SystemExit(f"未在 {src_root} 下找到可识别的牌面图（WAN/TONG/TIAO+1..9）")

    print(f"共 {len(planned)} 张 → {dst_root}（按 TILE_ORDER_27 共 27 类）")
    for src, dest, cls in planned:
        print(f"  [{cls:02d}] {src.relative_to(src_root)} -> {dest.relative_to(dst_root)}")

    if args.dry_run:
        print("dry-run：未写入。")
        return

    for src, dest, _ in planned:
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
    print(f"已写入: {dst_root}")


if __name__ == "__main__":
    main()
