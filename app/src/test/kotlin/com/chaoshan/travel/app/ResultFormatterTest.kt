package com.chaoshan.travel.app

import com.chaoshan.agents.ChaoshanTravelAgentFacade
import com.chaoshan.agents.model.OverallStatus
import com.chaoshan.agents.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 展示层测试：确认界面使用的格式化逻辑能正确呈现结构化结果，
 * 同时证明格式化逻辑不依赖 Android 框架（这里跑的是纯 JVM 单元测试）。
 */
class ResultFormatterTest {

    private val text = "明天要去潮洲的牌坊街,记得提前订牛肉火锅店，还要订汕头的高铁票。"

    @Test
    fun `完整结果的展示包含四类 Agent 的信息`() {
        val response = ChaoshanTravelAgentFacade.create().execute(text)
        val rendered = ResultFormatter.format(response)

        assertEquals(OverallStatus.SUCCESS, response.status)
        assertTrue(rendered.contains("UserProxyAgent").not()) // 结果体本身由各专家产出
        assertTrue(rendered.contains("ProofreaderAgent"))
        assertTrue(rendered.contains("CategorizerAgent"))
        assertTrue(rendered.contains("ActionSecretaryAgent"))
        assertTrue(rendered.contains("调用链"))
        assertTrue(rendered.contains("原始输入"))
    }

    @Test
    fun `失败结果在展示中明确呈现`() {
        val response = ChaoshanTravelAgentFacade.create().execute("", TaskType.FULL)
        val rendered = ResultFormatter.format(response)

        assertEquals(OverallStatus.FAILED, response.status)
        assertTrue(rendered.contains("FAILED"))
        assertTrue(rendered.contains("未调度任何 Agent"))
    }

    @Test
    fun `各 Agent 独立结果的展示`() {
        val facade = ChaoshanTravelAgentFacade.create()

        val proofread = ResultFormatter.formatProofread(facade.proofread(text))
        assertTrue(proofread.contains("校对后文本"))
        assertTrue(proofread.contains("执行的检查"))

        val categorize = ResultFormatter.formatCategorize(facade.categorize(text))
        assertTrue(categorize.contains("笔记本"))
        assertTrue(categorize.contains("标签"))

        val actions = ResultFormatter.formatActions(facade.extractActions(text))
        assertTrue(actions.contains("核心摘要"))
        assertTrue(actions.contains("待办事项"))
    }
}
