# -*- coding: utf-8 -*-
"""
与 Android `com.majiang.counter.analysis.PolicyFeatureV1` 保持数值一致（修改 Kotlin 后须同步此处与训练脚本）。
"""

FEATURE_DIM = 81
"""离散特征向量维度。"""

HEAD_HU = 27
"""听牌/进张头：与 Tile 全类型顺序一致，训练目标常用 softmax 分布。"""

HEAD_RON = 27
"""打出侧 RonAny（∃ 至少一家能和）按牌型索引，sigmoid / BCE。"""

HEAD_KONG = 27
"""打出侧点杠存在性，sigmoid / BCE。"""

OUTPUT_FULL = HEAD_HU + HEAD_RON + HEAD_KONG
"""导出 TFLite 单输出张量展平长度（Concat 顺序：hu | ron | kong）。"""

FEATURE_SCHEMA_VERSION = 1
