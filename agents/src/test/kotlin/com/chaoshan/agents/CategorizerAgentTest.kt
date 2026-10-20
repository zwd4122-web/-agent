package com.chaoshan.agents

import com.chaoshan.agents.categorizer.CategorizerAgent
import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 归档专家：标签生成与笔记本分类的真实验证。 */
class CategorizerAgentTest {

    private fun run(text: String, agent: CategorizerAgent = CategorizerAgent()) =
        agent.process(AgentRequest(text, TaskType.CATEGORIZE))

    @Test
    fun `美食文本生成美食类标签并归入美食笔记本`() {
        val agent = CategorizerAgent()
        val result = run("今天在潮州牌坊街吃了牛肉火锅和粿条，蚝烙也很香。", agent)

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue(
            "应识别出潮州/美食/牛肉火锅/粿条/蚝烙/牌坊街，实际=${result.tags}",
            result.tags.containsAll(listOf("潮州", "美食", "牛肉火锅", "粿条", "蚝烙", "牌坊街"))
        )
        assertEquals("notebook-food", result.notebookId)
        assertEquals("潮汕美食笔记", result.notebookName)
    }

    @Test
    fun `待办文本归入旅行待办笔记本`() {
        val result = run("记得提前预约开元寺的讲解，出发前要确认身份证。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("notebook-todo", result.notebookId)
        assertTrue(result.notebookReason.contains("记得"))
    }

    @Test
    fun `行程文本归入行程与攻略笔记本`() {
        val result = run("三天两夜的潮汕行程，第一天住汕头酒店，第二天坐高铁去潮州。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("notebook-plan", result.notebookId)
        assertTrue(result.tags.contains("行程"))
        assertTrue(result.tags.contains("潮州"))
    }

    @Test
    fun `标签必须有原文依据`() {
        val result = run("南澳岛的海很蓝，环岛路适合骑行。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue(result.tags.contains("南澳岛"))
        val evidence = result.tagEvidence["南澳岛"]
        assertTrue("标签必须能追溯到命中词", !evidence.isNullOrEmpty())
        assertTrue("命中词必须是原文中出现的", evidence!!.all { "南澳岛的海很蓝，环岛路适合骑行。".contains(it) })
    }

    @Test
    fun `分类结果真实写入笔记本存储且可查回`() {
        val agent = CategorizerAgent()
        val result = run("潮州的广济楼值得一看，门票很便宜。", agent)

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue("应标记为已写入", result.persisted)
        assertTrue(result.noteId != null)
        val stored = agent.notebookRepository.listNotes(result.notebookId)
        assertTrue("写入后可以按笔记本查回", stored.any { it.id == result.noteId })
        assertTrue("存储性质必须如实说明", result.storageDescription.contains("内存"))
    }

    @Test
    fun `无领域关键词时归入默认笔记本且标签为空`() {
        val result = run("今天随便写点东西。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("notebook-inbox", result.notebookId)
        assertTrue(result.tags.isEmpty())
        assertTrue(result.conclusion.contains("未识别"))
    }

    @Test
    fun `空输入返回可识别的失败`() {
        val result = run("")

        assertEquals(AgentStatus.FAILED, result.status)
        assertTrue(result.errorMessage!!.contains("不能为空"))
        assertEquals(false, result.persisted)
    }
}
