#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
v1 策略蒸馏脚手架：多输出小网络 + 导出 **单张量 81 维** float32 TFLite，
与 `TflitePolicyStudentInterpreter`（输入 81、输出 27 或 81）对齐。

当前使用 **合成数据** 演示训练闭环；接入真实 MC 教师时，将 `synthetic_dataset`
替换为从特征 NPZ / 数据库读取的 `(X, Y_hu, Y_ron, Y_kong)` 即可。

示例：
  pip install -r tools/ml/requirements.txt
  python tools/ml/train_policy_v1.py --out build/policy-v1.tflite --epochs 3
再将生成的 tflite 与更新后的 `model_manifest.json` 拷入 `app/src/main/assets/ml/xuezhan_mahjong_default/`。
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf

_ML_DIR = Path(__file__).resolve().parent
if str(_ML_DIR) not in sys.path:
    sys.path.insert(0, str(_ML_DIR))

from policy_schema import (
    FEATURE_DIM,
    HEAD_HU,
    HEAD_KONG,
    HEAD_RON,
    OUTPUT_FULL,
)


def build_train_model() -> tf.keras.Model:
    """三头可训模型：hu softmax + ron sigmoid + kong sigmoid。"""
    inp = tf.keras.Input(shape=(FEATURE_DIM,), name="features")
    x = tf.keras.layers.Dense(128, activation="relu")(inp)
    hu = tf.keras.layers.Dense(HEAD_HU, activation="softmax", name="hu")(x)
    ron = tf.keras.layers.Dense(HEAD_RON, activation="sigmoid", name="ron")(x)
    kong = tf.keras.layers.Dense(HEAD_KONG, activation="sigmoid", name="kong")(x)
    return tf.keras.Model(inp, [hu, ron, kong], name="policy_train_v1")


def build_export_model(train_model: tf.keras.Model) -> tf.keras.Model:
    """推理用单输出 [batch, 81]，与端侧解析顺序一致。"""
    inp = train_model.input
    hu, ron, kong = train_model(inp, training=False)
    out = tf.keras.layers.Concatenate(axis=-1, name="policy_full")([hu, ron, kong])
    return tf.keras.Model(inp, out, name="policy_export_v1")


def synthetic_dataset(n: int, seed: int = 0):
    """合成教师标签（占位）：真实项目请替换为 MC 统计结果。"""
    rng = np.random.default_rng(seed)
    x = rng.uniform(0.0, 1.0, size=(n, FEATURE_DIM)).astype(np.float32)
    hu_idx = rng.integers(0, HEAD_HU, size=(n,))
    y_hu = tf.keras.utils.to_categorical(hu_idx, HEAD_HU).astype(np.float32)
    y_ron = rng.uniform(0.0, 1.0, size=(n, HEAD_RON)).astype(np.float32)
    y_kong = rng.uniform(0.0, 1.0, size=(n, HEAD_KONG)).astype(np.float32)
    return x, y_hu, y_ron, y_kong


def main() -> None:
    parser = argparse.ArgumentParser(description="训练并导出 v1 策略 TFLite（81 维单输出）")
    parser.add_argument("--out", type=str, default="policy-v1.tflite", help="输出 .tflite 路径")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--samples", type=int, default=512, help="合成样本数")
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()

    if OUTPUT_FULL != 81:
        raise RuntimeError("OUTPUT_FULL 须与 Android POLICY_OUTPUT_FULL_DIM 一致")

    x, y_hu, y_ron, y_kong = synthetic_dataset(args.samples, args.seed)
    train_model = build_train_model()
    train_model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=[
            tf.keras.losses.CategoricalCrossentropy(),
            tf.keras.losses.BinaryCrossentropy(),
            tf.keras.losses.BinaryCrossentropy(),
        ],
        loss_weights=[1.0, 0.5, 0.5],
        metrics={
            "hu": [tf.keras.metrics.CategoricalAccuracy(name="acc_hu")],
        },
    )
    train_model.fit(
        x,
        [y_hu, y_ron, y_kong],
        epochs=args.epochs,
        batch_size=64,
        verbose=1,
    )

    export_model = build_export_model(train_model)
    export_model.summary()

    converter = tf.lite.TFLiteConverter.from_keras_model(export_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_bytes = converter.convert()

    out_path = os.path.abspath(args.out)
    parent = os.path.dirname(out_path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(tflite_bytes)

    print(f"已写入 TFLite（{len(tflite_bytes)} 字节）: {out_path}")
    print(
        "请将文件拷入 app/src/main/assets/ml/xuezhan_mahjong_default/，"
        "并确认 model_manifest.json 中 policyFile、rulesHash 与工程一致。"
    )


if __name__ == "__main__":
    main()
