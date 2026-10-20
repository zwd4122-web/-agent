package com.chaoshan.travel.app

import com.chaoshan.agents.model.ActionResult
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.CategorizeResult
import com.chaoshan.agents.model.IssueType
import com.chaoshan.agents.model.MultiAgentResponse
import com.chaoshan.agents.model.ProofreadResult

/**
 * 把 Agent 返回的结构化结果渲染成可阅读的文本。
 *
 * 这个类是纯 Kotlin 实现（不依赖任何 Android API），因此可以被单元测试覆盖，
 * 也说明「Agent 的处理逻辑」与「界面展示逻辑」是分离的：
 * 界面只是把结构化对象交给这里转换成文字，不参与任何决策。
 */
object ResultFormatter {

    /** 统一入口结果的展示文本。 */
    fun format(response: MultiAgentResponse): String = buildString {
        appendLine("===== 统一调用入口结果 =====")
        appendLine("请求 ID：${response.requestId}")
        appendLine("执行状态：${response.status}    任务类型：${response.taskType}    耗时：${response.elapsedMillis}ms")
        appendLine("原始输入：${response.originalText}")
        appendLine("传给下游的文本（校对后）：${response.workingText}")
        appendLine()
        response.proofread?.let { appendLine(formatProofread(it)) }
        response.categorize?.let { appendLine(formatCategorize(it)) }
        response.actions?.let { appendLine(formatActions(it)) }

        appendLine("----- 调用链（谁被调用、顺序与耗时）-----")
        if (response.trace.isEmpty()) {
            appendLine("（未调度任何 Agent）")
        } else {
            response.trace.forEachIndexed { index, step ->
                appendLine("${index + 1}. ${step.agentName}｜任务=${step.task}｜状态=${step.status}｜耗时=${step.elapsedMillis}ms")
                appendLine("     输入：${step.inputDigest}")
                appendLine("     输出：${step.outputDigest}")
            }
        }
        appendLine()
        appendLine("错误：${if (response.errors.isEmpty()) "无" else response.errors.joinToString("；")}")
    }

    fun formatProofread(result: ProofreadResult): String = buildString {
        appendLine("【校对专家 ${result.agentName}】状态=${result.status} 耗时=${result.elapsedMillis}ms")
        if (result.status == AgentStatus.FAILED) {
            appendLine("失败原因：${result.errorMessage}")
            return@buildString
        }
        appendLine("校对后文本：${result.correctedText}")
        appendLine("结论：${result.conclusion}")
        appendLine("执行的检查：")
        result.checksPerformed.forEach { appendLine("   · $it") }
        appendLine("发现的问题（${result.issues.size}）：")
        if (result.issues.isEmpty()) {
            appendLine("   无")
        } else {
            result.issues.forEach { issue ->
                val flag = if (issue.applied) "已修正" else "待人工确认"
                appendLine("   · [${typeName(issue.type)}][$flag] ${issue.original} → ${issue.suggestion}")
                appendLine("     ${issue.message}")
            }
        }
    }

    fun formatCategorize(result: CategorizeResult): String = buildString {
        appendLine("【归档专家 ${result.agentName}】状态=${result.status} 耗时=${result.elapsedMillis}ms")
        if (result.status == AgentStatus.FAILED) {
            appendLine("失败原因：${result.errorMessage}")
            return@buildString
        }
        appendLine("标签（${result.tags.size}）：${if (result.tags.isEmpty()) "无" else result.tags.joinToString("、")}")
        appendLine("笔记本：${result.notebookName}（${result.notebookId}）")
        appendLine("归类依据：${result.notebookReason}")
        if (result.tagEvidence.isNotEmpty()) {
            appendLine("标签依据：")
            result.tagEvidence.forEach { (tag, hits) -> appendLine("   · $tag ← ${hits.joinToString("、")}") }
        }
        appendLine("是否已写入笔记本存储：${result.persisted}（笔记 ID=${result.noteId}）")
        appendLine("存储说明：${result.storageDescription}")
        appendLine("结论：${result.conclusion}")
    }

    fun formatActions(result: ActionResult): String = buildString {
        appendLine("【行动秘书 ${result.agentName}】状态=${result.status} 耗时=${result.elapsedMillis}ms")
        if (result.status == AgentStatus.FAILED) {
            appendLine("失败原因：${result.errorMessage}")
            return@buildString
        }
        appendLine("核心摘要：${result.summary}")
        appendLine("待办事项（${result.todos.size}）：")
        if (result.todos.isEmpty()) {
            appendLine("   无（文本中没有明确的待办事项）")
        } else {
            result.todos.forEachIndexed { index, todo ->
                appendLine("   ${index + 1}. ${todo.content}（线索：${todo.cue}）")
            }
        }
        appendLine("结论：${result.conclusion}")
    }

    private fun typeName(type: IssueType): String = when (type) {
        IssueType.TYPO -> "错别字"
        IssueType.GRAMMAR -> "语病"
        IssueType.PUNCTUATION -> "标点"
    }
}
