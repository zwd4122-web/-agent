package com.chaoshan.agents.knowledge

import com.chaoshan.agents.notebook.NotebookRule

/**
 * 潮汕旅游领域知识库。
 *
 * 说明：这里是可维护的规则/词典数据，不是模型推理结果。
 * 归档与摘要都基于这些规则做确定性计算，因此同样输入必然得到同样输出，结果可验证。
 */
object ChaoshanKnowledge {

    /**
     * 标签规则：命中 keywords 中任意词即生成对应标签。
     * 标签粒度分三层：地域 / 主题 / 实体，便于其他模块按需筛选。
     */
    data class TagRule(val tag: String, val keywords: List<String>)

    val tagRules: List<TagRule> = listOf(
        // —— 地域 ——
        TagRule("潮州", listOf("潮州", "潮州市", "湘桥", "潮安", "饶平")),
        TagRule("汕头", listOf("汕头", "澄海", "潮阳", "潮南", "南澳", "濠江", "金平", "龙湖")),
        TagRule("揭阳", listOf("揭阳", "普宁", "惠来", "揭西", "揭东")),
        // —— 主题 ——
        TagRule("景点", listOf("景点", "景区", "古城", "游览", "参观", "打卡", "门票", "开放时间")),
        TagRule("美食", listOf("美食", "小吃", "好吃", "吃", "早餐", "夜宵", "排档", "大排档")),
        TagRule("文化", listOf("文化", "民俗", "历史", "传统", "非遗", "祠堂", "侨乡", "老街")),
        TagRule("交通", listOf("交通", "高铁", "动车", "机场", "自驾", "大巴", "轮渡", "船票", "打车", "公交")),
        TagRule("住宿", listOf("住宿", "酒店", "民宿", "客栈", "青旅", "入住", "退房")),
        TagRule("行程", listOf("行程", "路线", "攻略", "安排", "计划", "预算", "第一天", "第二天", "三天")),
        TagRule("购物", listOf("伴手礼", "特产", "手信", "买", "市场")),
        // —— 具体实体 ——
        TagRule("广济楼", listOf("广济楼")),
        TagRule("湘子桥", listOf("湘子桥", "广济桥")),
        TagRule("牌坊街", listOf("牌坊街")),
        TagRule("开元寺", listOf("开元寺")),
        TagRule("韩江", listOf("韩江")),
        TagRule("南澳岛", listOf("南澳岛", "南澳")),
        TagRule("礐石风景区", listOf("礐石", "磐石")),
        TagRule("龙湖古寨", listOf("龙湖古寨")),
        TagRule("陈慈黉故居", listOf("陈慈黉", "陈慈黄")),
        TagRule("道韵楼", listOf("道韵楼")),
        TagRule("凤凰山", listOf("凤凰山", "凤凰单丛", "单丛", "单枞")),
        TagRule("牛肉火锅", listOf("牛肉火锅", "牛肉丸", "牛肉")),
        TagRule("粿条", listOf("粿条", "果条", "棵条")),
        TagRule("蚝烙", listOf("蚝烙", "耗烙", "豪烙")),
        TagRule("卤鹅", listOf("卤鹅", "狮头鹅")),
        TagRule("工夫茶", listOf("工夫茶", "功夫茶")),
        TagRule("英歌舞", listOf("英歌舞", "英歌")),
        TagRule("潮剧", listOf("潮剧")),
    )

    /** 笔记本定义（归档专家的候选分类）。 */
    val notebookRules: List<NotebookRule> = listOf(
        NotebookRule(
            id = "notebook-scenic",
            name = "潮汕景点笔记",
            description = "记录潮汕各地的景点、古城与游览见闻",
            strongKeywords = listOf("景点", "景区", "古城", "游览", "参观", "门票", "开放时间"),
            keywords = listOf("打卡", "广济楼", "湘子桥", "牌坊街", "开元寺", "韩江", "南澳岛", "礐石",
                "龙湖古寨", "陈慈黉", "道韵楼", "凤凰山"),
        ),
        NotebookRule(
            id = "notebook-food",
            name = "潮汕美食笔记",
            description = "记录潮汕美食、小吃与茶馆体验",
            strongKeywords = listOf("美食", "小吃", "好吃", "夜宵", "大排档", "牛肉火锅"),
            keywords = listOf("牛肉丸", "粿条", "蚝烙", "卤鹅", "狮头鹅", "鸭母捻", "砂锅粥", "生腌",
                "鱼饭", "工夫茶", "单丛", "甜汤", "糖葱薄饼"),
        ),
        NotebookRule(
            id = "notebook-plan",
            name = "行程与攻略",
            description = "存放行程安排、路线、交通住宿与预算",
            strongKeywords = listOf("行程", "路线", "攻略", "安排", "计划", "预算", "高铁", "自驾", "住宿", "酒店"),
            keywords = listOf("动车", "机场", "民宿", "第一天", "第二天", "天数"),
        ),
        NotebookRule(
            id = "notebook-todo",
            name = "旅行待办",
            description = "存放出行前需要落实的事项",
            strongKeywords = listOf("待办", "TODO", "记得", "别忘了", "务必", "需要", "提前", "预约", "预订", "确认"),
            keywords = listOf("预定", "出发前"),
        ),
        NotebookRule(
            id = "notebook-diary",
            name = "旅行随笔",
            description = "存放旅行感受、日记与游记",
            strongKeywords = listOf("感受", "日记", "游记", "印象", "值得"),
            keywords = listOf("喜欢", "开心", "回忆", "那天", "记录"),
        ),
    )

    /** 默认笔记本：所有关键词都没有命中时归入此处。 */
    val defaultNotebook: NotebookRule = NotebookRule(
        id = "notebook-inbox",
        name = "潮汕旅游·未分类",
        description = "尚未识别出明确主题的潮汕旅游文本",
        keywords = emptyList(),
    )

    /**
     * 摘要打分用的领域词权重：出现这些词的句子更可能是核心内容。
     */
    val summaryKeywords: Map<String, Int> = buildMap {
        put("潮州", 3); put("汕头", 3); put("揭阳", 3); put("南澳岛", 3)
        put("广济楼", 3); put("湘子桥", 3); put("牌坊街", 3); put("开元寺", 2); put("韩江", 2)
        put("牛肉火锅", 2); put("粿条", 2); put("蚝烙", 2); put("卤鹅", 2); put("工夫茶", 2)
        put("行程", 3); put("路线", 2); put("攻略", 2); put("预算", 2); put("门票", 1); put("高铁", 2)
        put("住宿", 1); put("待办", 3); put("记得", 2); put("需要", 2)
    }
}
