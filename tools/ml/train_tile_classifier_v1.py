#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
牌面 27 分类小型 CNN：训练 + 导出 float32 TFLite（NHWC），与 `TfliteTileClassifier` 契约对齐。

**重要（可行性）**：默认使用 **按类别可分的合成纹理** 训练——与真实麻将牌面 **像素分布完全不同**，
导出的权重仅用于验证「端侧能加载、张量形状对」；**不经过真实/渲染牌面数据重训，在真机上几乎不可能认出牌**。
要产品可用：必须采集（或从游戏资源渲染）与 App 裁切一致的牌面小图，按 `TILE_ORDER_27` 的 27 类建文件夹，
使用 ``--data-dir`` 训练（见下方说明）。

用法（在项目根目录）::

    pip install -r tools/ml/requirements.txt
    python tools/ml/train_tile_classifier_v1.py

默认写出到::
    app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf

_ML_DIR = Path(__file__).resolve().parent
if str(_ML_DIR) not in sys.path:
    sys.path.insert(0, str(_ML_DIR))

from tile_schema_v1 import NUM_CLASSES, TILE_ORDER_27

_REPO_ROOT = Path(__file__).resolve().parents[2]


def _class_subdir(data_dir: Path, class_index: int) -> Path:
    """
    返回第 ``class_index`` 类的图像子目录。

    同时兼容 ``00``…``26`` 与 ``0``…``26`` 两种命名，便于与历史目录（如 ``tools/png/9``）对齐。
    """
    padded = data_dir / f"{class_index:02d}"
    if padded.is_dir():
        return padded
    plain = data_dir / str(class_index)
    if plain.is_dir():
        return plain
    raise FileNotFoundError(
        f"缺少类别目录（须为 00..26 或 0..26 之一）: {padded} 或 {plain}"
    )
_DEFAULT_OUT = (
    _REPO_ROOT / "app/src/main/assets/ml/xuezhan_mahjong_default/tiles-v1.tflite"
)

IMG_H = 64
IMG_W = 64


def _synth_one(label: int, rng: np.random.Generator) -> np.ndarray:
    """
    生成单张「可学习但非真实牌面」的 RGB 图，值域约 [0,1]。
    不同 label 在底色与条纹相位上可分离，便于小网络过拟合合成域以验证导出。
    """
    rank = label % 9 + 1
    base = np.zeros((IMG_H, IMG_W, 3), dtype=np.float32)
    # 三色基底随 suit / rank 变化
    hue = (label * 37 % 360) / 360.0
    base[..., 0] = 0.25 + 0.55 * np.sin(hue * 2 * np.pi)
    base[..., 1] = 0.25 + 0.55 * np.sin((hue + 0.33) * 2 * np.pi)
    base[..., 2] = 0.25 + 0.55 * np.sin((hue + 0.66) * 2 * np.pi)
    # 竖条纹：条数与 rank 相关
    for k in range(rank):
        x0 = int((k + 0.5) * IMG_W / 10)
        x1 = min(x0 + 3, IMG_W)
        base[:, x0:x1, :] += 0.12
    # 轻微噪声（每图不同）
    noise = rng.normal(0.0, 0.04, size=base.shape).astype(np.float32)
    img = np.clip(base + noise, 0.0, 1.0)
    return img


def load_real_tile_crops(data_dir: Path, seed: int) -> tuple[np.ndarray, np.ndarray]:
    """
    从目录加载真实牌面小图。根目录下每类一个子目录，与 ``TILE_ORDER_27`` 下标一致
    （0=万1, …, 8=万9, 9=筒1, …, 26=条9）。子目录名可为 ``00``…``26`` 或 ``0``…``26``。
    每目录若干 ``.jpg`` / ``.jpeg`` / ``.png``。
    图像统一缩放至 ``IMG_H``×``IMG_W``，RGB 归一化到 [0,1]。
    """
    rng = np.random.default_rng(seed)
    paths_labels: list[tuple[Path, int]] = []
    for c in range(NUM_CLASSES):
        sub = _class_subdir(data_dir, c)
        files: list[Path] = []
        for pat in ("*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"):
            files.extend(sorted(sub.glob(pat)))
        if not files:
            raise FileNotFoundError(f"类别 {c:02d} 目录下无图像文件: {sub}")
        for p in files:
            paths_labels.append((p, c))

    order = rng.permutation(len(paths_labels))
    paths_labels = [paths_labels[i] for i in order]

    xs_list: list[np.ndarray] = []
    for path, _ in paths_labels:
        raw = tf.io.read_file(str(path))
        img = tf.image.decode_image(raw, channels=3, expand_animations=False)
        img = tf.image.resize(img, [IMG_H, IMG_W], method="bilinear")
        img = tf.clip_by_value(tf.cast(img, tf.float32) / 255.0, 0.0, 1.0)
        xs_list.append(img.numpy())

    x = np.stack(xs_list, axis=0).astype(np.float32)
    y = np.zeros((len(paths_labels), NUM_CLASSES), dtype=np.float32)
    for i, (_, c) in enumerate(paths_labels):
        y[i, c] = 1.0
    return x, y


def build_synthetic_dataset(
    n_per_class: int,
    seed: int,
) -> tuple[np.ndarray, np.ndarray]:
    """合成 (N, H, W, 3) float32 与 one-hot 标签。"""
    rng = np.random.default_rng(seed)
    xs: list[np.ndarray] = []
    ys: list[np.ndarray] = []
    for c in range(NUM_CLASSES):
        for _ in range(n_per_class):
            xs.append(_synth_one(c, rng))
            y = np.zeros(NUM_CLASSES, dtype=np.float32)
            y[c] = 1.0
            ys.append(y)
    x = np.stack(xs, axis=0)
    y = np.stack(ys, axis=0)
    # 打乱
    idx = rng.permutation(len(x))
    return x[idx], y[idx]


def build_model() -> tf.keras.Model:
    """轻量 CNN，输入 NHWC；与端侧 ``TfliteTileClassifier``（解码 PNG/JPEG 后缩放）一致。"""
    inp = tf.keras.Input(shape=(IMG_H, IMG_W, 3), name="image_rgb")
    x = tf.keras.layers.Conv2D(24, 3, padding="same", activation="relu")(inp)
    x = tf.keras.layers.MaxPool2D(2)(x)
    x = tf.keras.layers.Conv2D(48, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.MaxPool2D(2)(x)
    x = tf.keras.layers.Conv2D(64, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    x = tf.keras.layers.Dense(128, activation="relu")(x)
    out = tf.keras.layers.Dense(NUM_CLASSES, activation="softmax", name="probs")(x)
    return tf.keras.Model(inp, out, name="tile_classifier_v1")


def main() -> None:
    parser = argparse.ArgumentParser(description="训练并导出牌面 27 类 TFLite（合成数据占位）")
    parser.add_argument(
        "--out",
        type=str,
        default=str(_DEFAULT_OUT),
        help="输出 .tflite 绝对或相对路径",
    )
    parser.add_argument("--epochs", type=int, default=12)
    parser.add_argument("--n-per-class", type=int, default=320, help="每类合成样本数（仅未指定 --data-dir 时）")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument(
        "--data-dir",
        type=str,
        default=None,
        help="真实牌面数据集根目录（子目录 00..26，每类多张图）；指定后不再使用合成数据",
    )
    args = parser.parse_args()

    print("标签顺序（须与 Tile.allTypes 一致）前 9 个:", TILE_ORDER_27[:9])

    if args.data_dir:
        data_root = Path(args.data_dir).resolve()
        print(f"使用真实牌面数据目录: {data_root}")
        x_train, y_train = load_real_tile_crops(data_root, args.seed)
        print(f"已加载真实样本 N={len(x_train)}，输入形状={x_train.shape}")
    else:
        print(
            "警告：未指定 --data-dir，使用合成纹理训练；该权重在真实牌桌上不可用于识别牌面，仅作工程联调。"
        )
        x_train, y_train = build_synthetic_dataset(args.n_per_class, args.seed)
    # 简单 hold-out：末 10% 作验证
    n = len(x_train)
    split = int(n * 0.9)
    x_val, y_val = x_train[split:], y_train[split:]
    x_train, y_train = x_train[:split], y_train[:split]

    model = build_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=tf.keras.losses.CategoricalCrossentropy(),
        metrics=[tf.keras.metrics.CategoricalAccuracy(name="acc")],
    )
    model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch_size,
        verbose=1,
    )

    # 导出 TFLite：不启用整型量化，避免与端侧 float 缓冲不一致
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = []
    tflite_bytes = converter.convert()

    out_path = Path(args.out).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(tflite_bytes)
    print(f"已写入 TFLite（{len(tflite_bytes)} 字节）: {out_path}")
    if args.data_dir:
        print("说明：已用 --data-dir 真实数据训练；请在验证集上检查准确率后再打包进 assets。")
    else:
        print(
            "说明：当前权重仅拟合合成纹理。要识别真实牌桌，请准备 00..26 子目录图像并传入 --data-dir 重训。"
        )


if __name__ == "__main__":
    main()
