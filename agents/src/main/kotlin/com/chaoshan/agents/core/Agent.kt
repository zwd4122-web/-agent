package com.chaoshan.agents.core

/**
 * 所有 Agent 的统一抽象。
 *
 * 设计要点：
 * - 泛型 [I] / [O] 明确声明每个 Agent 的输入与输出类型，调用方无需依赖界面即可调用。
 * - 每个 Agent 只做自己职责范围内的事，[role] 用一行文字说明职责边界。
 */
interface Agent<in I, out O> {

    /** Agent 名称，用于执行追踪与结果归属。 */
    val name: String

    /** 职责说明。 */
    val role: String

    /**
     * 执行一次处理。实现必须真正执行职责对应的逻辑；
     * 发生异常时返回 status = FAILED 的结果，而不是把失败伪装成成功。
     */
    fun process(input: I): O
}
