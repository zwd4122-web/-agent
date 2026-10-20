package com.chaoshan.agents.model

import com.chaoshan.agents.core.AgentExecutionException

/**
 * 本次请求要求执行的任务类型。
 *
 * 调用方通过 [AgentRequest.taskType] 字段显式声明要做什么，
 * 不依赖任何界面状态或隐式约定。
 */
enum class TaskType {
    /** 完整协同流程：校对专家 -> 归档专家 -> 行动秘书 */
    FULL,

    /** 只执行校对 */
    PROOFREAD,

    /** 只执行标签与笔记本归档 */
    CATEGORIZE,

    /** 只执行摘要与待办提取 */
    ACTIONS,
}

/** 单个 Agent 的执行状态。 */
enum class AgentStatus {
    SUCCESS,
    FAILED,
}

/** 统一入口返回的总体状态。 */
enum class OverallStatus {
    SUCCESS,

    /** 部分 Agent 成功、部分失败 */
    PARTIAL_SUCCESS,

    FAILED,
}

/**
 * 统一输入。
 *
 * @property text 待处理的原始文本（潮汕旅游相关：景点介绍、旅行记录、游玩计划、备忘等）
 * @property taskType 本次请求要执行的任务，默认为完整流程
 * @property requestId 请求标识，用于结果追溯
 */
data class AgentRequest(
    val text: String,
    val taskType: TaskType = TaskType.FULL,
    val requestId: String = "req-" + java.util.UUID.randomUUID().toString().substring(0, 8),
)

/**
 * 执行追踪步骤。用于证明 Agent 确实被调用过，以及调用顺序与数据流向。
 */
data class TraceStep(
    val agentName: String,
    val task: TaskType,
    val status: AgentStatus,
    val elapsedMillis: Long,
    /** 该 Agent 实际收到的输入摘要 */
    val inputDigest: String,
    /** 该 Agent 产出结果的摘要 */
    val outputDigest: String,
) {
    override fun toString(): String =
        "$agentName[$task] ${status.name} ${elapsedMillis}ms in=($inputDigest) out=($outputDigest)"
}

/** 文本校验：空白输入视为非法，直接抛出可识别的失败原因。 */
fun String.requireText(field: String = "输入文本"): String {
    if (isBlank()) {
        throw AgentExecutionException("$field 不能为空，请提供需要处理的潮汕旅游文本")
    }
    return this
}

/** 生成文本摘要（用于追踪与日志，不影响业务结果）。 */
fun String.digest(maxLength: Int = 24): String {
    val flat = replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= maxLength) "$flat(len=${flat.length})"
    else flat.substring(0, maxLength) + "…(len=${flat.length})"
}
