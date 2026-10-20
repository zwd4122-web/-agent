package com.chaoshan.agents.core

/**
 * Agent 执行过程中抛出的业务异常。
 *
 * 所有 Agent 都会捕获该异常并转化为 status = FAILED 的结构化结果，
 * 保证调用方拿到的是可识别的失败，而不是崩溃或未定义行为。
 */
class AgentExecutionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
