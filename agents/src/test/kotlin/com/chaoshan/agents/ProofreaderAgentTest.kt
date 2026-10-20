package com.chaoshan.agents

import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.IssueType
import com.chaoshan.agents.model.TaskType
import com.chaoshan.agents.proofreader.ProofreaderAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校对专家：错别字、语病、标点三类检查的真实验证。 */
class ProofreaderAgentTest {

    private val agent = ProofreaderAgent()

    private fun run(text: String) = agent.process(AgentRequest(text, TaskType.PROOFREAD))

    @Test
    fun `发现错别字并改写为正确写法`() {
        val result = run("我们去了潮洲的牌坊街")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue("应命中错别字", result.issueCountByType[IssueType.TYPO] ?: 0 > 0)
        assertTrue(result.correctedText.contains("潮州"))
        assertTrue(result.correctedText.contains("牌坊街"))
        assertTrue(
            "每条错别字都要给出修改说明",
            result.issues.filter { it.type == IssueType.TYPO }
                .all { it.original.isNotBlank() && it.suggestion.isNotBlank() && it.message.contains("错别字") }
        )
    }

    @Test
    fun `检查标点问题并修正半角逗号`() {
        val result = run("潮州古城很美,广济楼也很壮观。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue("应命中标点问题", result.issueCountByType[IssueType.PUNCTUATION] ?: 0 > 0)
        assertTrue("半角逗号应改为全角", result.correctedText.contains("，"))
    }

    @Test
    fun `检查语病并修正语义重复`() {
        val result = run("这次去潮汕的目的是为了看英歌舞。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue("应命中语病", result.issueCountByType[IssueType.GRAMMAR] ?: 0 > 0)
        assertTrue("语义重复应被修正", result.correctedText.contains("目的是") &&
            !result.correctedText.contains("目的是为了"))
    }

    @Test
    fun `同时命中错别字语病标点并给出校对后文本`() {
        val result = run("我们去了潮洲的牌坊街,广济搂很壮观。目的是为了拍照留念。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals(
            "我们去了潮州的牌坊街，广济楼很壮观。目的是拍照留念。",
            result.correctedText
        )
        assertEquals(2, result.issueCountByType[IssueType.TYPO])
        assertEquals(1, result.issueCountByType[IssueType.PUNCTUATION])
        assertEquals(1, result.issueCountByType[IssueType.GRAMMAR])
    }

    @Test
    fun `没有问题时返回明确的校对完成结果`() {
        val result = run("潮州古城的夜景很美。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue("无问题时不该编造问题", result.issues.isEmpty())
        assertEquals("潮州古城的夜景很美。", result.correctedText)
        assertTrue("必须说明实际执行过哪些检查", result.checksPerformed.size == 3)
        assertTrue("结论应明确说明未发现问题", result.conclusion.contains("未发现"))
    }

    @Test
    fun `空输入返回可识别的失败而不是伪装成功`() {
        val result = run("   ")

        assertEquals(AgentStatus.FAILED, result.status)
        assertTrue(result.errorMessage!!.contains("不能为空"))
        assertEquals("", result.correctedText)
        assertTrue(result.checksPerformed.isEmpty())
    }

    @Test
    fun `四条 Agent 均非占位实现`() {
        assertTrue(agent.name.isNotBlank())
        assertTrue(agent.role.contains("校对"))
    }
}
