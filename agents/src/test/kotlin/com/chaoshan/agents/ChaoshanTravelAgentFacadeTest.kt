package com.chaoshan.agents

import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.OverallStatus
import com.chaoshan.agents.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统一调用入口与各 Agent 独立入口的验证。 */
class ChaoshanTravelAgentFacadeTest {

    private val facade = ChaoshanTravelAgentFacade.create()

    private val sample = "明天要去潮洲的牌坊街,记得提前订牛肉火锅店，还要订汕头的高铁票。"

    @Test
    fun `统一入口返回结构化结果`() {
        val response = facade.execute(sample)

        assertEquals(OverallStatus.SUCCESS, response.status)
        assertTrue(response.requestId.isNotBlank())
        assertEquals(TaskType.FULL, response.taskType)
        assertEquals(sample, response.originalText)

        assertNotNull(response.proofread)
        assertNotNull(response.categorize)
        assertNotNull(response.actions)

        assertTrue(response.categorize!!.tags.isNotEmpty())
        assertTrue(response.categorize!!.notebookName.isNotBlank())
        assertTrue(response.actions!!.summary.isNotBlank())
        assertTrue(response.actions!!.todos.isNotEmpty())
        assertTrue(response.trace.size == 3)
    }

    @Test
    fun `统一入口可按任务类型调用`() {
        val onlyProofread = facade.execute(sample, TaskType.PROOFREAD)
        assertEquals(1, onlyProofread.trace.size)
        assertEquals("ProofreaderAgent", onlyProofread.trace[0].agentName)

        val onlyCategorize = facade.execute(sample, TaskType.CATEGORIZE)
        assertEquals(1, onlyCategorize.trace.size)
        assertEquals("CategorizerAgent", onlyCategorize.trace[0].agentName)

        val onlyActions = facade.execute(sample, TaskType.ACTIONS)
        assertEquals(1, onlyActions.trace.size)
        assertEquals("ActionSecretaryAgent", onlyActions.trace[0].agentName)
    }

    @Test
    fun `校对专家独立入口`() {
        val result = facade.proofread("潮洲的牌坊街很热闹,广济搂很壮观。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("ProofreaderAgent", result.agentName)
        assertTrue(result.correctedText.contains("潮州"))
        assertTrue(result.correctedText.contains("广济楼"))
    }

    @Test
    fun `归档专家独立入口`() {
        val result = facade.categorize("潮州的牛肉火锅和粿条都很好吃。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("CategorizerAgent", result.agentName)
        assertEquals("notebook-food", result.notebookId)
        assertTrue(result.tags.contains("美食"))
    }

    @Test
    fun `行动秘书独立入口`() {
        val result = facade.extractActions("记得提前预约广济楼的门票。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals("ActionSecretaryAgent", result.agentName)
        assertTrue(result.hasTodo)
        assertTrue(result.todos.any { it.content.contains("预约") })
    }

    @Test
    fun `四个 Agent 均已注册且职责说明非空`() {
        val agents = facade.agents()

        assertEquals(4, agents.size)
        assertTrue(agents.all { it.name.isNotBlank() && it.role.isNotBlank() })
        assertEquals(
            setOf("UserProxyAgent", "ProofreaderAgent", "CategorizerAgent", "ActionSecretaryAgent"),
            agents.map { it.name }.toSet()
        )
    }

    @Test
    fun `统一入口在异常输入时返回失败`() {
        val response = facade.execute("")

        assertEquals(OverallStatus.FAILED, response.status)
        assertTrue(response.errors.isNotEmpty())
    }
}
