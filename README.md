# 潮汕旅游 Multi-Agent

一个可调用、可复用、输入输出明确的「潮汕旅游 Multi-Agent」工程。
核心不是旅游 App 页面，而是由四个 Agent 协同处理、分析和增强文本的智能工作流。

## 1. 工程结构

```
ChaoshanTravelAgents/
├── agents/                 # 纯 JVM/Kotlin 库模块：Agent 能力本体（无 Android 依赖）
│   └── src/main/kotlin/com/chaoshan/agents/
│       ├── core/           # Agent 接口、执行异常
│       ├── model/          # 输入契约(AgentRequest)与全部结构化输出
│       ├── knowledge/      # 潮汕旅游领域词典与规则数据
│       ├── notebook/       # 笔记本存储抽象 + 进程内内存实现
│       ├── proofreader/    # 校对专家 + 校对规则
│       ├── categorizer/    # 归档专家
│       ├── secretary/      # 行动秘书
│       ├── support/        # 文本切分工具
│       ├── UserProxyAgent.kt            # 用户代理（调度中枢）
│       └── ChaoshanTravelAgentFacade.kt # 统一调用入口
│   └── src/test/           # 33 个单元测试
└── app/                    # Android 应用模块：仅作交互入口
    └── src/main/kotlin/com/chaoshan/travel/app/
        ├── MainActivity.kt        # 收集输入 + 调用 facade + 展示结果
        └── ResultFormatter.kt     # 结构化结果 -> 展示文本（纯 Kotlin，可单测）
    └── src/test/           # 3 个展示层单元测试
```

**分层原则**：`:agents` 不含任何 Android API，`:app` 不含任何业务判断。
调用方可以完全不使用 `MainActivity`，直接依赖 `:agents` 调用能力。

## 2. 四类 Agent

| Agent | 类 | 职责 | 输出 |
|---|---|---|---|
| 用户代理 | `UserProxyAgent` | 接收输入、调度其余三个专家、整合结果 | `MultiAgentResponse` |
| 校对专家 | `ProofreaderAgent` | 检查错别字、语病、标点 | `ProofreadResult` |
| 归档专家 | `CategorizerAgent` | 生成标签、归入笔记本 | `CategorizeResult` |
| 行动秘书 | `ActionSecretaryAgent` | 提取核心摘要、识别待办 | `ActionResult` |

### 校对专家
三类检查都真实执行，并把执行过的规则写入 `checksPerformed`，因此不存在"未检查即声称校对完成"：
- 错别字：潮汕旅游高频错误词典（潮洲→潮州、广济搂→广济楼、耗烙→蚝烙…）+ 通用高频错字
- 语病：虚词重复、"目的是为了"语义重复、"大约…左右"语义重复、转折连词缺呼应、超长句无停顿
- 标点：半角符号、重复标点、标点前多余空格、引号不成对、句末缺标点

每条问题都带 `applied` 字段，标明是否真的改写进了 `correctedText`。
没有问题时返回 `SUCCESS` + 空 `issues` + 明确的 `conclusion`。

### 归档专家
- 标签由领域词表命中产生，结果中的 `tagEvidence` 给出每个标签命中的原文关键词，保证分类与内容有可追溯关联
- 笔记本分类用关键词加权打分：强特征词（意图明确）3 分、普通特征词（实体名）1 分，
  使"意图"压过"实体名"，避免只提到某个景点名就被归进景点笔记
- 分类结果真实写入注入的 `NotebookRepository`，并把存储性质原样返回

### 行动秘书
- 摘要为**抽取式**：按"领域词权重 + 位置 + 句长"给句子打分，挑取得分最高的 1~2 句，
  不相邻时用「……」连接。不会生成原文中不存在的内容
- 待办识别有明确触发条件：必须命中待办线索词（或 `- [ ]` / `待办：` 等显式清单标记），
  且句中存在动作动词。纯描述性句子不会被当成待办，因此不会凭空编造任务
- 每条待办保留 `cue`（触发线索）与 `source`（原文片段），可追溯

## 3. 协同机制

`UserProxyAgent` 统一调度，顺序执行且数据流清晰：

```
用户输入
  │
  ├─ 1. ProofreaderAgent  输入=原文        输出=校对后文本
  │
  ├─ 2. CategorizerAgent  输入=校对后文本  输出=标签 + 笔记本
  │
  └─ 3. ActionSecretary   输入=校对后文本  输出=摘要 + 待办
  │
  └─> 整合为 MultiAgentResponse（含 trace 调用链）
```

- `AgentRequest.taskType` 显式声明本次要做什么（`FULL` / `PROOFREAD` / `CATEGORIZE` / `ACTIONS`），不依赖界面状态
- 下游 Agent 用的是**校对后的文本**（结果中的 `workingText`），这是 Agent 之间的数据传递
- 每步都记录 `TraceStep`（Agent 名 / 任务 / 状态 / 耗时 / 输入输出摘要），调用关系可追踪
- 单个 Agent 失败不中断流程：整体状态为 `PARTIAL_SUCCESS`，全部失败为 `FAILED`，失败信息进 `errors`

## 4. 调用方式

### 统一入口
```kotlin
val facade = ChaoshanTravelAgentFacade.create()

// 完整协同流程
val response: MultiAgentResponse = facade.execute("明天要去潮洲的牌坊街,记得提前订牛肉火锅店。")

// 指定任务类型
val onlyProofread = facade.execute(text, TaskType.PROOFREAD)

// 使用完整请求对象（可自定义 requestId）
val custom = facade.execute(AgentRequest(text, TaskType.FULL, requestId = "req-001"))
```

### 各 Agent 独立入口
```kotlin
val proofread: ProofreadResult  = facade.proofread(text)
val categorize: CategorizeResult = facade.categorize(text)
val actions: ActionResult       = facade.extractActions(text)

// 也可以直接构造 Agent 使用（便于替换依赖、复用）
val proxy = UserProxyAgent(
    categorizer = CategorizerAgent(myRepository)   // 注入自己的笔记本存储
)
proxy.runProofread(AgentRequest(text, TaskType.PROOFREAD))
```

### 统一输出字段（`MultiAgentResponse`）
| 字段 | 说明 |
|---|---|
| `requestId` | 请求标识 |
| `status` | `SUCCESS` / `PARTIAL_SUCCESS` / `FAILED` |
| `taskType` | 本次任务类型 |
| `originalText` | 原始输入，保证可追溯 |
| `workingText` | 传给下游 Agent 的文本（校对后） |
| `proofread` / `categorize` / `actions` | 各专家结果 |
| `trace` | 调用链，含每步 Agent 名、状态、耗时、输入输出摘要 |
| `errors` | 错误信息清单 |
| `elapsedMillis` | 总耗时 |

## 5. 构建与验证

```bash
./gradlew :agents:test :app:test   # 运行全部单元测试（36 个）
./gradlew :app:assembleDebug       # 构建 APK
./gradlew clean build              # 全量构建
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 6. 诚实说明与已知限制

- **没有接入大模型**。四类 Agent 全部是基于规则和领域词典的确定性实现：
  同样输入必然得到同样输出，结果可验证。若项目后续接入真实 AI 服务，
  可替换 `ProofreaderAgent` / `ActionSecretaryAgent` 的内部实现，接口与协同流程不变。
- **笔记本存储是进程内内存实现**（`InMemoryNotebookRepository`）。
  写入后可立即按笔记本查回（有测试覆盖），但不做磁盘/数据库持久化，进程退出即丢失。
  该性质通过 `storageDescription` 字段随结果如实返回，不虚构持久化能力。
- **摘要是抽取式**，从原文选句，不具备改写与生成能力。
- **错别字/语病词典是人工维护的有限词表**，只能覆盖已收录的错误，无法识别词表外的错别字。
- 工程路径含非 ASCII 字符（Windows 用户名），已通过 AGP 官方开关 `android.overridePathCheck=true` 绕过。
