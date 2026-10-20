package com.chaoshan.agents.categorizer

import com.chaoshan.agents.core.Agent
import com.chaoshan.agents.core.AgentExecutionException
import com.chaoshan.agents.knowledge.ChaoshanKnowledge
import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.CategorizeResult
import com.chaoshan.agents.model.requireText
import com.chaoshan.agents.notebook.InMemoryNotebookRepository
import com.chaoshan.agents.notebook.NoteRecord
import com.chaoshan.agents.notebook.NotebookRepository
import com.chaoshan.agents.notebook.NotebookRule

/**
 * 归档专家（Categorizer Agent）。
 *
 * 职责：分析文本内容，生成标签，并把文本归入合适的笔记本。
 *
 * 实现说明：
 * - 标签由 [ChaoshanKnowledge.tagRules] 命中产生，结果中通过 tagEvidence
 *   给出每个标签命中的原文关键词，保证分类与输入内容有可追溯的关联。
 * - 笔记本分类采用关键词加权打分，取最高分；无命中时归入默认笔记本。
 * - 分类结果会真实写入注入的 [NotebookRepository]，并把存储性质
 *   （[NotebookRepository.storageDescription]）原样返回，不虚构持久化能力。
 */
class CategorizerAgent(
    private val repository: NotebookRepository = InMemoryNotebookRepository(
        notebooks = ChaoshanKnowledge.notebookRules + ChaoshanKnowledge.defaultNotebook,
    ),
) : Agent<AgentRequest, CategorizeResult> {

    /** 对外暴露所使用的笔记本存储，便于调用方查询或替换为自己的实现。 */
    val notebookRepository: NotebookRepository get() = repository

    override val name: String = "CategorizerAgent"

    override val role: String = "归档专家：分析文本内容，生成标签并归入合适的笔记本"

    override fun process(input: AgentRequest): CategorizeResult {
        val started = System.nanoTime()
        return try {
            val text = input.text.requireText("待归档文本")

            // 1) 标签识别
            val evidence = LinkedHashMap<String, List<String>>()
            for (rule in ChaoshanKnowledge.tagRules) {
                val matched = rule.keywords.filter { text.contains(it) }
                if (matched.isNotEmpty()) {
                    evidence[rule.tag] = matched
                }
            }
            val tags = evidence.keys.toList()

            // 2) 笔记本打分归类
            val scored = scoreNotebooks(text)
            val best = scored.maxByOrNull { it.second }
            val notebook: NotebookRule = if (best != null && best.second > 0.0) {
                repository.findById(best.first)
                    ?: throw AgentExecutionException("笔记本 ${best.first} 不存在")
            } else {
                ChaoshanKnowledge.defaultNotebook
            }
            val hitKeywords = (notebook.strongKeywords + notebook.keywords).filter { text.contains(it) }
            val reason = if (best != null && best.second > 0.0) {
                "命中笔记本关键词 ${hitKeywords.joinToString("、")}，得分 ${"%.1f".format(best.second)}"
            } else {
                "未命中任何笔记本关键词，归入默认笔记本"
            }

            // 3) 真实写入笔记本存储
            val record = repository.save(
                NoteRecord(
                    id = "note-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                    notebookId = notebook.id,
                    text = text,
                    tags = tags,
                )
            )

            val conclusion = if (tags.isEmpty()) {
                "未识别出明确标签，已将文本归入笔记本「${notebook.name}」（$reason），已写入${repository.storageDescription}"
            } else {
                "生成 ${tags.size} 个标签（${tags.joinToString("、")}），归入笔记本「${notebook.name}」" +
                    "（$reason），已写入${repository.storageDescription}"
            }

            CategorizeResult(
                agentName = name,
                status = AgentStatus.SUCCESS,
                tags = tags,
                notebookId = notebook.id,
                notebookName = notebook.name,
                notebookReason = reason,
                tagEvidence = evidence,
                score = best?.second ?: 0.0,
                persisted = true,
                noteId = record.id,
                storageDescription = repository.storageDescription,
                conclusion = conclusion,
                elapsedMillis = (System.nanoTime() - started) / 1_000_000,
            )
        } catch (e: AgentExecutionException) {
            failed(started, e.message ?: "归档失败")
        } catch (e: Exception) {
            failed(started, "归档过程中发生异常：${e.javaClass.simpleName} - ${e.message}")
        }
    }

    /**
     * 对候选笔记本打分：强特征词（主题意图明确）命中记 3.0 分，普通特征词记 1.0 分。
     * 打分权重向「意图」倾斜，避免只提到某个景点名就被归进景点笔记。
     */
    private fun scoreNotebooks(text: String): List<Pair<String, Double>> =
        repository.listNotebooks().map { notebook ->
            val strong = notebook.strongKeywords.count { text.contains(it) } * 3.0
            val normal = notebook.keywords.count { text.contains(it) } * 1.0
            notebook.id to (strong + normal)
        }

    private fun failed(started: Long, message: String): CategorizeResult = CategorizeResult(
        agentName = name,
        status = AgentStatus.FAILED,
        tags = emptyList(),
        notebookId = "",
        notebookName = "",
        notebookReason = "归档未完成",
        tagEvidence = emptyMap(),
        score = 0.0,
        persisted = false,
        noteId = null,
        storageDescription = repository.storageDescription,
        conclusion = "归档未完成：$message",
        elapsedMillis = (System.nanoTime() - started) / 1_000_000,
        errorMessage = message,
    )
}
