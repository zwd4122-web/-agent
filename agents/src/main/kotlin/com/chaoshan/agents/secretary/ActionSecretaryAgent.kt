package com.chaoshan.agents.secretary

import com.chaoshan.agents.core.Agent
import com.chaoshan.agents.core.AgentExecutionException
import com.chaoshan.agents.knowledge.ChaoshanKnowledge
import com.chaoshan.agents.model.ActionResult
import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.TodoItem
import com.chaoshan.agents.model.requireText
import com.chaoshan.agents.support.TextSplitter

/**
 * 行动秘书（Action Secretary Agent）。
 *
 * 职责：抽取核心摘要，识别文本中的待办事项（TODO）。
 *
 * 实现说明：
 * - 摘要为抽取式：按「领域词权重 + 位置 + 句长」给句子打分，挑取得分最高的 1~2 句，
 *   不会生成原文中不存在的内容。
 * - 待办识别有明确触发条件：必须命中待办线索词（或显式清单标记），且句中存在动作动词。
 *   纯描述性句子（例如景点观感）不会被当成待办，因此不会凭空编造任务。
 * - 每条待办都保留 [TodoItem.cue] 与 [TodoItem.source]，说明「为什么被识别」。
 */
class ActionSecretaryAgent : Agent<AgentRequest, ActionResult> {

    override val name: String = "ActionSecretaryAgent"

    override val role: String = "行动秘书：提取核心摘要并识别待办事项"

    override fun process(input: AgentRequest): ActionResult {
        val started = System.nanoTime()
        return try {
            val text = input.text.requireText("待提取文本")

            val sentences = TextSplitter.sentences(text)
            val indexes = pickKeySentenceIndexes(sentences)
            val keySentences = indexes.map { sentences[it] }
            val summary = buildSummary(text, sentences, indexes)
            val todos = extractTodos(text)

            val conclusion = if (todos.isEmpty()) {
                "已提取核心摘要（${keySentences.size} 个关键句）；文本中未出现明确的待办事项，待办列表为空"
            } else {
                "已提取核心摘要（${keySentences.size} 个关键句）；识别到 ${todos.size} 条待办事项"
            }

            ActionResult(
                agentName = name,
                status = AgentStatus.SUCCESS,
                summary = summary,
                todos = todos,
                hasTodo = todos.isNotEmpty(),
                keySentences = keySentences,
                conclusion = conclusion,
                elapsedMillis = (System.nanoTime() - started) / 1_000_000,
            )
        } catch (e: AgentExecutionException) {
            failed(started, e.message ?: "摘要与待办提取失败")
        } catch (e: Exception) {
            failed(started, "提取过程中发生异常：${e.javaClass.simpleName} - ${e.message}")
        }
    }

    // ------------------------------------------------------------------ 摘要

    /** 按打分挑取得分最高的 1~2 个句子，返回它们在原文中的下标（保持升序）。 */
    private fun pickKeySentenceIndexes(sentences: List<String>): List<Int> {
        if (sentences.isEmpty()) return emptyList()
        val scored = sentences.mapIndexed { index, sentence -> index to scoreSentence(sentence, index) }
        val take = if (sentences.size == 1) 1 else 2
        return scored
            .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })
            .take(take)
            .map { it.first }
            .sorted()
    }

    private fun scoreSentence(sentence: String, index: Int): Double {
        var score = 0.0
        for ((word, weight) in ChaoshanKnowledge.summaryKeywords) {
            if (sentence.contains(word)) score += weight
        }
        score += when (index) { 0 -> 4.0; 1 -> 2.0; else -> 0.0 }
        val length = sentence.length
        score += when {
            length in 12..45 -> 2.0
            length < 6 -> -2.0
            length > 60 -> -1.0
            else -> 0.0
        }
        return score
    }

    /**
     * 拼接关键句生成摘要。
     * 若选中的句子在原文中不相邻，用「……」连接，避免把不连贯的句子硬拼在一起。
     */
    private fun buildSummary(text: String, sentences: List<String>, indexes: List<Int>): String {
        val flat = text.trim()
        if (flat.length <= 40) return flat
        if (indexes.isEmpty()) return flat
        val builder = StringBuilder()
        var previous: Int? = null
        for (index in indexes) {
            if (previous != null && index != previous + 1) builder.append("……")
            builder.append(sentences[index])
            previous = index
        }
        return builder.toString()
    }

    // ------------------------------------------------------------------ 待办

    /** 待办线索词：出现这些词说明句子带有「尚未完成、需要去做」的意味。 */
    private val actionCues = listOf(
        "记得", "别忘了", "别忘记", "不要忘了", "务必", "需要", "得先", "提前", "待办", "TODO",
        "预约", "预订", "预定", "确认", "出发前", "打算", "计划", "准备", "应该", "要", "得",
    )

    /** 动作动词：线索词 + 动作动词同时出现，才判定为待办，用于抑制误报。 */
    private val actionVerbs = listOf(
        "订", "买", "查", "确认", "预约", "预订", "预定", "联系", "准备", "收拾", "打印", "下载",
        "安排", "出发", "提前", "取", "换", "退", "寄存", "打卡", "逛", "吃", "拍", "看", "走",
        "租", "加油", "充电", "排队", "带", "去", "坐", "乘", "爬", "游", "尝", "品", "喝", "报",
    )

    /** 显式清单标记，例如「- [ ] 订船票」「待办：订高铁票」。 */
    private val markerRegex = Regex("^\\s*(?:[-*]\\s*\\[\\s*\\]|TODO[:：]?|待办[:：]?)\\s*(.+)$")

    private fun extractTodos(text: String): List<TodoItem> {
        val result = ArrayList<TodoItem>()
        val seen = HashSet<String>()

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            val marker = markerRegex.find(line)
            if (marker != null) {
                val content = TextSplitter.stripEdges(marker.groupValues[1])
                if (content.isNotBlank() && seen.add(content)) {
                    result += TodoItem(content = content, source = line, cue = "清单标记")
                }
                continue // 该行已按显式清单处理，不再做线索词扫描，避免重复
            }

            for (clause in TextSplitter.clauses(line)) {
                val cue = actionCues.firstOrNull { clause.contains(it) } ?: continue
                if (!actionVerbs.any { clause.contains(it) }) continue
                val content = TextSplitter.stripEdges(clause)
                if (content.length < 4) continue
                if (seen.add(content)) {
                    result += TodoItem(content = content, source = clause, cue = cue)
                }
            }
        }
        return result
    }

    // ------------------------------------------------------------------ 工具

    private fun failed(started: Long, message: String): ActionResult = ActionResult(
        agentName = name,
        status = AgentStatus.FAILED,
        summary = "",
        todos = emptyList(),
        hasTodo = false,
        keySentences = emptyList(),
        conclusion = "提取未完成：$message",
        elapsedMillis = (System.nanoTime() - started) / 1_000_000,
        errorMessage = message,
    )
}
