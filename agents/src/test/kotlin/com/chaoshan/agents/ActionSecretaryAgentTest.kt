package com.chaoshan.agents

import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.TaskType
import com.chaoshan.agents.secretary.ActionSecretaryAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 行动秘书：核心摘要与待办事项提取的真实验证。 */
class ActionSecretaryAgentTest {

    private val agent = ActionSecretaryAgent()

    private fun run(text: String) = agent.process(AgentRequest(text, TaskType.ACTIONS))

    @Test
    fun `从计划文本中识别出待办事项`() {
        val text = "明天要去南澳岛，记得提前订船票，还要查一下返程的高铁时间。"
        val result = run(text)

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue("应识别出待办，实际=${result.todos.map { it.content }}", result.hasTodo)
        assertEquals(3, result.todos.size)
        assertTrue(result.todos.any { it.content.contains("订船票") })
        assertTrue(result.todos.any { it.content.contains("查一下返程的高铁时间") })
        assertTrue("每条待办都要能追溯到原文", result.todos.all { text.contains(it.source) })
        assertTrue("每条待办都要给出触发线索", result.todos.all { it.cue.isNotBlank() })
    }

    @Test
    fun `识别显式清单形式的待办`() {
        val result = run("待办：订南澳岛船票\n待办：确认酒店入住时间")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertEquals(2, result.todos.size)
        assertTrue(result.todos.all { it.cue == "清单标记" })
    }

    @Test
    fun `纯描述文本不编造待办`() {
        val result = run("潮州古城很有味道，牌坊街上人很多，广济楼的夜景很漂亮。")

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue("描述性文本不应产生待办，实际=${result.todos.map { it.content }}", result.todos.isEmpty())
        assertEquals(false, result.hasTodo)
        assertTrue("结论应明确说明无待办", result.conclusion.contains("未出现明确的待办事项"))
    }

    @Test
    fun `摘要来自原文关键句且不编造`() {
        val text = "这次潮汕之行一共三天。第一天到了潮州，先去牌坊街逛了一圈，又看了广济楼的夜景。" +
            "第二天去了南澳岛，海很蓝。最后一天在汕头吃了牛肉火锅，然后坐高铁返程。"
        val result = run(text)

        assertEquals(AgentStatus.SUCCESS, result.status)
        assertTrue(result.summary.isNotBlank())
        assertTrue("关键句必须出自原文，实际=${result.keySentences}", result.keySentences.all { text.contains(it) })
        assertTrue(
            "摘要必须完全由原文关键句拼成，实际=${result.summary}",
            result.keySentences.all { result.summary.contains(it) }
        )
        assertTrue(
            "摘要不得引入原文以外的句子",
            result.summary.replace("……", "").all { ch -> text.contains(ch) }
        )
    }

    @Test
    fun `空输入返回可识别的失败`() {
        val result = run("   ")

        assertEquals(AgentStatus.FAILED, result.status)
        assertTrue(result.errorMessage!!.contains("不能为空"))
        assertEquals("", result.summary)
        assertTrue(result.todos.isEmpty())
    }
}
