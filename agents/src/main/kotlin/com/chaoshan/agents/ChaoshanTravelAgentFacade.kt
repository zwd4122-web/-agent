package com.chaoshan.agents

import com.chaoshan.agents.core.Agent
import com.chaoshan.agents.model.ActionResult
import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.CategorizeResult
import com.chaoshan.agents.model.MultiAgentResponse
import com.chaoshan.agents.model.ProofreadResult
import com.chaoshan.agents.model.TaskType

/**
 * 潮汕旅游 Multi-Agent 的统一调用入口（Facade）。
 *
 * 调用方（Android 界面、服务端、脚本、测试）只需要依赖这一个类：
 *
 * ```kotlin
 * val facade = ChaoshanTravelAgentFacade.create()
 *
 * // 1) 统一入口：一次拿到四类 Agent 的整合结果
 * val response = facade.execute("明天要去南澳岛，记得提前订船票。", TaskType.FULL)
 *
 * // 2) 各 Agent 独立入口
 * val proofread  = facade.proofread(text)
 * val categorize = facade.categorize(text)
 * val actions    = facade.extractActions(text)
 * ```
 *
 * 所有方法都返回结构化对象，调用方不需要依赖任何界面即可使用。
 */
class ChaoshanTravelAgentFacade(
    val userProxy: UserProxyAgent = UserProxyAgent(),
) {

    /** 统一调用入口：完整协同流程（校对 -> 归档 -> 摘要待办）。 */
    fun execute(text: String): MultiAgentResponse =
        userProxy.process(AgentRequest(text = text, taskType = TaskType.FULL))

    /** 统一调用入口：按指定任务类型执行。 */
    fun execute(text: String, taskType: TaskType): MultiAgentResponse =
        userProxy.process(AgentRequest(text = text, taskType = taskType))

    /** 统一调用入口：使用完整请求对象（可自定义 requestId）。 */
    fun execute(request: AgentRequest): MultiAgentResponse = userProxy.process(request)

    /** 校对专家独立入口。 */
    fun proofread(text: String): ProofreadResult =
        userProxy.runProofread(AgentRequest(text = text, taskType = TaskType.PROOFREAD))

    /** 归档专家独立入口。 */
    fun categorize(text: String): CategorizeResult =
        userProxy.runCategorize(AgentRequest(text = text, taskType = TaskType.CATEGORIZE))

    /** 行动秘书独立入口。 */
    fun extractActions(text: String): ActionResult =
        userProxy.runActions(AgentRequest(text = text, taskType = TaskType.ACTIONS))

    /** 当前参与协同的全部 Agent，便于自检与展示。 */
    fun agents(): List<Agent<*, *>> = listOf(
        userProxy,
        userProxy.proofreader,
        userProxy.categorizer,
        userProxy.secretary,
    )

    companion object {
        @JvmStatic
        fun create(): ChaoshanTravelAgentFacade = ChaoshanTravelAgentFacade()
    }
}
