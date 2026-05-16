#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
牌面 27 分类小型 CNN：训练 + 导出 float32 TFLite（NHWC），与 `TfliteTileClassifier` 契约对齐。

**重要**：默认使用 **按类别可分的合成纹理** 完成训练闭环，便于你本地立刻得到可加载的
`tiles-v1.tflite`。**在真实牌桌画面上准确率无保证**；接入真实牌面小图后，请用同标签顺序
替换 `build_synthetic_dataset` 的数据源并重新训练。

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
    """轻量 CNN，输入 NHWC 与端侧 decode JPEG 后缩放一致。"""
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
    parser.add_argument("--n-per-class", type=int, default=320, help="每类合成样本数")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--batch-size", type=int, default=64)
    args = parser.parse_args()

    print("标签顺序（须与 Tile.allTypes 一致）前 9 个:", TILE_ORDER_27[:9])

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
    print(
        "说明：当前权重仅拟合合成纹理。要识别真实牌桌，请用标注牌面小图替换数据源后重训，"
        "并保持 27 类顺序与 tile_schema_v1.TILE_ORDER_27 一致。"
    )


if __name__ == "__main__":
    main()
