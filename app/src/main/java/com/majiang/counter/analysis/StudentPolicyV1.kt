package com.majiang.counter.analysis

/**
 * 学生策略网络单次前向结果（与 `policy-v1.tflite` 输出布局一致）。
 *
 * @property hu27 听牌/进张头，长度 27，与 `Tile.allTypes()` 顺序一致；已为概率（softmax）。
 * @property ron27 打出侧 RonAny 头：按牌型索引的「∃ 至少一家能和」估计，\[0,1\]；为 null 表示当前模型无打出头（仅 27 维输出）。
 * @property kong27 打出侧点杠存在性估计，\[0,1\]；为 null 同上。
 */
class StudentPolicyV1(
    val hu27: FloatArray,
    val ron27: FloatArray?,
    val kong27: FloatArray?,
)
