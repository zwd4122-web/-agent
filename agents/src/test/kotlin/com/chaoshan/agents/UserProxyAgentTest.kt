package com.chaoshan.agents

import com.chaoshan.agents.categorizer.CategorizerAgent
import com.chaoshan.agents.core.AgentExecutionException
import com.chaoshan.agents.knowledge.ChaoshanKnowledge
import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.OverallStatus
import com.chaoshan.agents.model.TaskType
import com.chaoshan.agents.notebook.InMemoryNotebookRepository
import com.chaoshan.agents.notebook.NoteRecord
import com.chaoshan.agents.notebook.NotebookRepository
import com.chaoshan.agents.notebook.NotebookRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用户代理：调度与结果整合的真实验证。 */
class UserProxyAgentTest {

    /** 故意让 save 失败的存储实现，用于验证失败不会被伪装成成功。 */
    private class FailingNotebookRepository : NotebookRepository {
        override val storageDescription: String = "测试用：必定失败的存储"
        override fun listNotebooks(): List<NotebookRule> =
            ChaoshanKnowledge.notebookRules + ChaoshanKnowledge.defaultNotebook
        override fun findById(id: String): NotebookRule? = listNotebooks().firstOrNull { it.id == id }
        override fun save(record: NoteRecord): NoteRecord =
            throw AgentExecutionException("模拟存储写入失败")
        override fun listNotes(notebookId: String): List<NoteRecord> = emptyList()
        override fun count(): Int = 0
    }

    private val proxy = UserProxyAgent()

    @Test
    fun `完整流程依次调度三个专家并整合结果`() {
        val text = "明天要去潮洲的牌坊街,记得提前订牛肉火锅店。"
        val response = proxy.process(AgentRequest(text, TaskType.FULL))

        assertEquals(OverallStatus.SUCCESS, response.status)
        assertEquals(text, response.originalText)

        assertEquals(
            "调用顺序应为 校对 -> 归档 -> 行动秘书",
            listOf("ProofreaderAgent", "CategorizerAgent", "ActionSecretaryAgent"),
            response.trace.map { it.agentName }
        )
        assertEquals(3, response.trace.size)
        assertTrue(response.trace.all { it.status == AgentStatus.SUCCESS })

        assertNotNull(response.proofread)
        assertNotNull(response.categorize)
        assertNotNull(response.actions)
    }

    @Test
    fun `下游 Agent 使用的是校对后的文本`() {
        val text = "明天要去潮洲的牌坊街,记得提前订牛肉火锅店。"
        val response = proxy.process(AgentRequest(text, TaskType.FULL))

        val corrected = response.proofread!!.correctedText
        assertEquals("下游文本应等于校对后文本", corrected, response.workingText)
        assertTrue("原文错别字应被修正", response.workingText.contains("潮州"))
        assertTrue("原文错别字不应继续存在", !response.workingText.contains("潮洲"))
        assertTrue("归档依据应是校对后的文本", response.categorize!!.tags.contains("潮州"))
    }

    @Test
    fun `按任务类型只调度对应 Agent`() {
        val request = AgentRequest("潮州的广济楼很壮观。", TaskType.PROOFREAD)
        val response = proxy.process(request)

        assertEquals(OverallStatus.SUCCESS, response.status)
        assertEquals(1, response.trace.size)
        assertEquals("ProofreaderAgent", response.trace[0].agentName)
        assertNotNull(response.proofread)
        assertNull("未请求归档时不应产生归档结果", response.categorize)
        assertNull("未请求摘要时不应产生摘要结果", response.actions)
    }

    @Test
    fun `各 Agent 都能通过用户代理的独立入口调用`() {
        val request = AgentRequest("记得提前预约开元寺的讲解。", TaskType.FULL)

        assertEquals(AgentStatus.SUCCESS, proxy.runProofread(request).status)
        assertEquals(AgentStatus.SUCCESS, proxy.runCategorize(request).status)
        assertEquals(AgentStatus.SUCCESS, proxy.runActions(request).status)
        assertTrue(
            "三个专家返回的应是各自职责的数据",
            proxy.runCategorize(request).notebookId.isNotBlank() &&
                proxy.runActions(request).todos.isNotEmpty() &&
                proxy.runProofread(request).correctedText.isNotBlank()
        )
    }

    @Test
    fun `空输入不调度任何 Agent 且返回失败`() {
        val response = proxy.process(AgentRequest("   ", TaskType.FULL))

        assertEquals(OverallStatus.FAILED, response.status)
        assertTrue(response.trace.isEmpty())
        assertTrue(response.errors.isNotEmpty())
        assertNull(response.proofread)
    }

    @Test
    fun `单个 Agent 失败时返回部分成功并保留错误信息`() {
        val failingProxy = UserProxyAgent(
            categorizer = CategorizerAgent(FailingNotebookRepository()),
        )
        val response = failingProxy.process(AgentRequest("潮州的广济楼很壮观，记得提前订票。", TaskType.FULL))

        assertEquals("部分失败应标记为 PARTIAL_SUCCESS", OverallStatus.PARTIAL_SUCCESS, response.status)
        assertEquals(AgentStatus.FAILED, response.categorize!!.status)
        assertEquals(AgentStatus.SUCCESS, response.proofread!!.status)
        assertEquals(AgentStatus.SUCCESS, response.actions!!.status)
        assertTrue(response.errors.any { it.contains("归档专家执行失败") })
        assertTrue(response.errors.any { it.contains("模拟存储写入失败") })
    }

    @Test
    fun `归档使用的笔记本存储注入后可复用`() {
        val repository = InMemoryNotebookRepository(
            ChaoshanKnowledge.notebookRules + ChaoshanKnowledge.defaultNotebook
        )
        val proxyWithRepo = UserProxyAgent(categorizer = CategorizerAgent(repository))

        val response = proxyWithRepo.process(AgentRequest("潮州古城的夜景很美。", TaskType.FULL))

        assertEquals(OverallStatus.SUCCESS, response.status)
        assertEquals(1, repository.count())
        assertEquals(
            1,
            repository.listNotes(response.categorize!!.notebookId)
                .count { it.id == response.categorize!!.noteId }
        )
    }
}
