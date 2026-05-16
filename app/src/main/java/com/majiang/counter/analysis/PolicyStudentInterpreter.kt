package com.majiang.counter.analysis

/**
 * 端侧 TFLite 学生策略：加载失败或张量不兼容时返回 null，分析侧回退纯 MC。
 */
interface PolicyStudentInterpreter {
    /**
     * 单次推理；听牌头必含，打出头视模型输出维度（27 或 81）可选。
     *
     * @return 模型或字节缓冲不可用时 null。
     */
    fun infer(featureVector: FloatArray): StudentPolicyV1?
}
