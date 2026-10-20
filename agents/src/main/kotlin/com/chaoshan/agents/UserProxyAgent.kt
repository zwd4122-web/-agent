package com.chaoshan.agents

import com.chaoshan.agents.categorizer.CategorizerAgent
import com.chaoshan.agents.core.Agent
import com.chaoshan.agents.model.ActionResult
import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.CategorizeResult
import com.chaoshan.agents.model.MultiAgentResponse
import com.chaoshan.agents.model.OverallStatus
import com.chaoshan.agents.model.ProofreadResult
import com.chaoshan.agents.model.TaskType
import com.chaoshan.agents.model.TraceStep
import com.chaoshan.agents.model.digest
import com.chaoshan.agents.proofreader.ProofreaderAgent
import com.chaoshan.agents.secretary.ActionSecretaryAgent

/**
 * 用户代理（User Proxy Agent）。
 *
 * 职责：作为用户与其他 Agent 之间的统一交互入口，接收用户输入，
 * 调度 [ProofreaderAgent]、[CategorizerAgent]、[ActionSecretaryAgent]，
 * 收集并整合各 Agent 的结果后返回统一输出。
 *
 * 协同方式（顺序调度，数据流清晰）：
 *  1. 先交给校对专家，得到校对后的文本；
 *  2. 把「校对后的文本」作为输入交给归档专家与行动秘书，
 *     这样下游 Agent 处理的是已经修正过的内容（结果中的 workingText 即为此值）；
 *  3. 汇总三个结果 + 调用链 trace + 错误清单，返回 [MultiAgentResponse]。
 *
 * 单个 Agent 失败不会中断整体流程：失败的 Agent 返回 FAILED 结果，
 * 整体状态标记为 PARTIAL_SUCCESS（全部失败则为 FAILED），不做任何「失败伪装成成功」的处理。
 */
class UserProxyAgent(
    val proofreader: ProofreaderAgent = ProofreaderAgent(),
    val categorizer: CategorizerAgent = CategorizerAgent(),
    val secretary: ActionSecretaryAgent = ActionSecretaryAgent(),
) : Agent<AgentRequest, MultiAgentResponse> {

    override val name: String = "UserProxyAgent"

    override val role: String = "用户代理：接收用户输入，调度各专家 Agent 并整合最终结果"

    /** 本次调度实际会执行的任务顺序。 */
    fun plan(taskType: TaskType): List<TaskType> = when (taskType) {
        TaskType.FULL -> listOf(TaskType.PROOFREAD, TaskType.CATEGORIZE, TaskType.ACTIONS)
        else -> listOf(taskType)
    }

    // ------------------------------------------------- 各专家的独立调度入口

    /** 调度校对专家。 */
    fun runProofread(request: AgentRequest): ProofreadResult =
        proofreader.process(request.copy(taskType = TaskType.PROOFREAD))

    /** 调度归档专家。 */
    fun runCategorize(request: AgentRequest): CategorizeResult =
        categorizer.process(request.copy(taskType = TaskType.CATEGORIZE))

    /** 调度行动秘书。 */
    fun runActions(request: AgentRequest): ActionResult =
        secretary.process(request.copy(taskType = TaskType.ACTIONS))

    // ------------------------------------------------------------ 统一调度

    override fun process(input: AgentRequest): MultiAgentResponse {
        val started = System.nanoTime()
        val trace = ArrayList<TraceStep>()
        val errors = ArrayList<String>()

        if (input.text.isBlank()) {
            return MultiAgentResponse(
                requestId = input.requestId,
                status = OverallStatus.FAILED,
                taskType = input.taskType,
                originalText = input.text,
                workingText = input.text,
                proofread = null,
                categorize = null,
                actions = null,
                trace = emptyList(),
                errors = listOf("输入文本不能为空，未调度任何 Agent"),
                elapsedMillis = (System.nanoTime() - started) / 1_000_000,
            )
        }

        var workingText = input.text
        var proofread: ProofreadResult? = null
        var categorize: CategorizeResult? = null
        var actions: ActionResult? = null

        for (task in plan(input.taskType)) {
            when (task) {
                TaskType.PROOFREAD -> {
                    val step = measure(task, workingText) {
                        runProofread(
                            AgentRequest(
                                text = workingText,
                                taskType = TaskType.PROOFREAD,
                                requestId = "${input.requestId}-proofread",
                            )
                        )
                    }
                    trace += step.first
                    proofread = step.second
                    if (step.second.status == AgentStatus.SUCCESS) {
                        // 数据传递：下游 Agent 使用校对后的文本
                        workingText = step.second.correctedText
                    } else {
                        errors += "校对专家执行失败：${step.second.errorMessage}"
                    }
                }

                TaskType.CATEGORIZE -> {
                    val step = measure(task, workingText) {
                        runCategorize(
                            AgentRequest(
                                text = workingText,
                                taskType = TaskType.CATEGORIZE,
                                requestId = "${input.requestId}-categorize",
                            )
                        )
                    }
                    trace += step.first
                    categorize = step.second
                    if (step.second.status != AgentStatus.SUCCESS) {
                        errors += "归档专家执行失败：${step.second.errorMessage}"
                    }
                }

                TaskType.ACTIONS -> {
                    val step = measure(task, workingText) {
                        runActions(
                            AgentRequest(
                                text = workingText,
                                taskType = TaskType.ACTIONS,
                                requestId = "${input.requestId}-actions",
                            )
                        )
                    }
                    trace += step.first
                    actions = step.second
                    if (step.second.status != AgentStatus.SUCCESS) {
                        errors += "行动秘书执行失败：${step.second.errorMessage}"
                    }
                }

                TaskType.FULL -> error("FULL 已在 plan() 中展开，不应作为单步任务执行")
            }
        }

        val status = when {
            errors.isEmpty() -> OverallStatus.SUCCESS
            errors.size == trace.size -> OverallStatus.FAILED
            else -> OverallStatus.PARTIAL_SUCCESS
        }

        return MultiAgentResponse(
            requestId = input.requestId,
            status = status,
            taskType = input.taskType,
            originalText = input.text,
            workingText = workingText,
            proofread = proofread,
            categorize = categorize,
            actions = actions,
            trace = trace.toList(),
            errors = errors.toList(),
            elapsedMillis = (System.nanoTime() - started) / 1_000_000,
        )
    }

    /**
     * 执行单个 Agent 并计时，同时把异常转成可识别的 FAILED 结果，
     * 保证调用链上每一步都有明确的成功/失败记录。
     */
    private inline fun <reified T> measure(
        task: TaskType,
        inputText: String,
        block: () -> T,
    ): Pair<TraceStep, T> {
        val started = System.nanoTime()
        val result = try {
            block()
        } catch (e: Exception) {
            val failed = buildFailedResult<T>(e)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            return TraceStep(
                agentName = agentNameOf(task),
                task = task,
                status = AgentStatus.FAILED,
                elapsedMillis = elapsed,
                inputDigest = inputText.digest(),
                outputDigest = "失败：${e.javaClass.simpleName} - ${e.message}",
            ) to failed
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val status = readStatus(result)
        return TraceStep(
            agentName = agentNameOf(task),
            task = task,
            status = status,
            elapsedMillis = elapsed,
            inputDigest = inputText.digest(),
            outputDigest = readDigest(result),
        ) to result
    }

    private fun agentNameOf(task: TaskType): String = when (task) {
        TaskType.PROOFREAD -> proofreader.name
        TaskType.CATEGORIZE -> categorizer.name
        TaskType.ACTIONS -> secretary.name
        TaskType.FULL -> name
    }

    /** 读取各结果对象中的执行状态。 */
    private fun readStatus(result: Any?): AgentStatus = when (result) {
        is ProofreadResult -> result.status
        is CategorizeResult -> result.status
        is ActionResult -> result.status
        else -> AgentStatus.FAILED
    }

    private fun readDigest(result: Any?): String = when (result) {
        is ProofreadResult -> "问题 ${result.issues.size} 处，校对后文本 ${result.correctedText.digest(16)}"
        is CategorizeResult -> "标签 ${result.tags.size} 个，笔记本 ${result.notebookName}"
        is ActionResult -> "待办 ${result.todos.size} 条，摘要 ${result.summary.digest(16)}"
        else -> result.toString().digest()
    }

    private inline fun <reified T> buildFailedResult(e: Exception): T {
        val message = "${e.javaClass.simpleName} - ${e.message}"
        @Suppress("UNCHECKED_CAST")
        return when (T::class) {
            ProofreadResult::class -> ProofreadResult(
                agentName = proofreader.name,
                status = AgentStatus.FAILED,
                correctedText = "",
                issues = emptyList(),
                checksPerformed = emptyList(),
                issueCountByType = emptyMap(),
                conclusion = "校对未完成：$message",
                elapsedMillis = 0,
                errorMessage = message,
            ) as T

            CategorizeResult::class -> CategorizeResult(
                agentName = categorizer.name,
                status = AgentStatus.FAILED,
                tags = emptyList(),
                notebookId = "",
                notebookName = "",
                notebookReason = "归档未完成",
                tagEvidence = emptyMap(),
                score = 0.0,
                persisted = false,
                noteId = null,
                storageDescription = categorizer.notebookRepository.storageDescription,
                conclusion = "归档未完成：$message",
                elapsedMillis = 0,
                errorMessage = message,
            ) as T

            else -> ActionResult(
                agentName = secretary.name,
                status = AgentStatus.FAILED,
                summary = "",
                todos = emptyList(),
                hasTodo = false,
                keySentences = emptyList(),
                conclusion = "提取未完成：$message",
                elapsedMillis = 0,
                errorMessage = message,
            ) as T
        }
    }
}
