package com.chaoshan.agents.model

/**
 * 四类 Agent 的结构化输出，以及统一入口的整合结果。
 *
 * 这些数据结构与界面无关，任何 Kotlin/Java 代码都可以直接读取字段使用。
 */

/** 校对问题的类型。 */
enum class IssueType {
    /** 错别字 */
    TYPO,

    /** 语病 */
    GRAMMAR,

    /** 标点问题 */
    PUNCTUATION,
}

/**
 * 一条具体的校对问题。
 *
 * @property applied 该修改是否已被应用到 [ProofreadResult.correctedText]
 */
data class ProofreadIssue(
    val type: IssueType,
    val original: String,
    val suggestion: String,
    val message: String,
    val applied: Boolean,
)

/**
 * 校对专家的输出。
 *
 * @property correctedText 校对后的文本
 * @property issues 发现的问题清单（含修改说明）
 * @property checksPerformed 实际执行过的检查项，用于证明检查真实发生而非直接返回原文
 * @property conclusion 结论性描述；没有问题时也会给出明确说明
 */
data class ProofreadResult(
    val agentName: String,
    val status: AgentStatus,
    val correctedText: String,
    val issues: List<ProofreadIssue>,
    val checksPerformed: List<String>,
    val issueCountByType: Map<IssueType, Int>,
    val conclusion: String,
    val elapsedMillis: Long,
    val errorMessage: String? = null,
) {
    val hasIssue: Boolean get() = issues.isNotEmpty()
}

/**
 * 归档专家的输出。
 *
 * @property tags 依据文本内容生成的标签
 * @property notebookId / [notebookName] 归类到的笔记本
 * @property tagEvidence 每个标签命中的原文关键词，保证分类与输入内容有可追溯的关联
 * @property persisted 是否真的写入了笔记本存储（当前实现为进程内内存存储，见 NotebookRepository）
 */
data class CategorizeResult(
    val agentName: String,
    val status: AgentStatus,
    val tags: List<String>,
    val notebookId: String,
    val notebookName: String,
    val notebookReason: String,
    val tagEvidence: Map<String, List<String>>,
    val score: Double,
    val persisted: Boolean,
    val noteId: String?,
    val storageDescription: String,
    val conclusion: String,
    val elapsedMillis: Long,
    val errorMessage: String? = null,
)

/**
 * 一条待办事项。
 *
 * @property cue 触发提取的线索词。保留线索是为了可追溯：
 *               没有线索的纯描述性句子不会被当成待办，避免凭空编造任务。
 */
data class TodoItem(
    val content: String,
    val source: String,
    val cue: String,
)

/**
 * 行动秘书的输出。
 *
 * @property summary 核心摘要（抽取式：从原文挑选关键句，非生成式编造）
 * @property todos 待办事项列表；文本中无待办时为空列表，[hasTodo] = false
 */
data class ActionResult(
    val agentName: String,
    val status: AgentStatus,
    val summary: String,
    val todos: List<TodoItem>,
    val hasTodo: Boolean,
    val keySentences: List<String>,
    val conclusion: String,
    val elapsedMillis: Long,
    val errorMessage: String? = null,
)

/**
 * 用户代理整合后的统一输出。
 *
 * @property originalText 原始输入，保证结果可追溯
 * @property workingText 传递给下游 Agent 的文本（校对后的文本），体现 Agent 之间的数据传递
 * @property proofread / [categorize] / [actions] 各专家的处理结果
 * @property trace 调用链，清楚记录谁被调用、顺序、耗时与输入输出摘要
 */
data class MultiAgentResponse(
    val requestId: String,
    val status: OverallStatus,
    val taskType: TaskType,
    val originalText: String,
    val workingText: String,
    val proofread: ProofreadResult?,
    val categorize: CategorizeResult?,
    val actions: ActionResult?,
    val trace: List<TraceStep>,
    val errors: List<String>,
    val elapsedMillis: Long,
)
