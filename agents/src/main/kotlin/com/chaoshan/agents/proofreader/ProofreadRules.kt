package com.chaoshan.agents.proofreader

/**
 * 校对专家使用的规则与词典。
 *
 * 全部为可维护的确定性规则：命中即给出「原文 -> 建议」的说明。
 * 词典以潮汕旅游场景高频错误为主，并包含少量通用高频错别字。
 */
object ProofreadRules {

    /** 错别字条目：错误写法、正确写法、说明。 */
    data class TypoEntry(val wrong: String, val correct: String, val note: String)

    val typos: List<TypoEntry> = listOf(
        // —— 地名 ——
        TypoEntry("潮洲市", "潮州市", "地名正确写法为「潮州」，「洲」为水中陆地，不用于此地"),
        TypoEntry("潮洲", "潮州", "地名正确写法为「潮州」"),
        TypoEntry("揭杨", "揭阳", "地名正确写法为「揭阳」"),
        TypoEntry("揭扬", "揭阳", "地名正确写法为「揭阳」"),
        TypoEntry("汕关", "汕头", "地名正确写法为「汕头」"),
        TypoEntry("广济搂", "广济楼", "「楼」误写为「搂」"),
        TypoEntry("广剂楼", "广济楼", "「济」误写为「剂」"),
        TypoEntry("道韵搂", "道韵楼", "「楼」误写为「搂」"),
        TypoEntry("相子桥", "湘子桥", "「湘」误写为「相」"),
        TypoEntry("湘子挢", "湘子桥", "「桥」误写为「挢」"),
        TypoEntry("牌坊衔", "牌坊街", "「街」误写为「衔」"),
        TypoEntry("排坊街", "牌坊街", "「牌」误写为「排」"),
        TypoEntry("开原寺", "开元寺", "「元」误写为「原」"),
        TypoEntry("寒江", "韩江", "「韩」误写为「寒」"),
        TypoEntry("南奥岛", "南澳岛", "「澳」误写为「奥」"),
        TypoEntry("南澳鸟", "南澳岛", "「岛」误写为「鸟」"),
        TypoEntry("龙湖古塞", "龙湖古寨", "「寨」误写为「塞」"),
        TypoEntry("陈慈黄", "陈慈黉", "故居正确写法为「陈慈黉故居」"),
        TypoEntry("砮石", "礐石", "汕头风景区正确写法为「礐石」"),
        TypoEntry("雀石", "礐石", "汕头风景区正确写法为「礐石」"),
        TypoEntry("韩文公词", "韩文公祠", "「祠」误写为「词」"),
        TypoEntry("已略黄公词", "已略黄公祠", "「祠」误写为「词」"),
        // —— 美食与民俗 ——
        TypoEntry("牛内丸", "牛肉丸", "「肉」误写为「内」"),
        TypoEntry("牛肉园", "牛肉丸", "「丸」误写为「园」"),
        TypoEntry("棵条", "粿条", "正确写法为「粿条」"),
        TypoEntry("果条", "粿条", "正确写法为「粿条」"),
        TypoEntry("耗烙", "蚝烙", "「蚝」误写为「耗」"),
        TypoEntry("豪烙", "蚝烙", "「蚝」误写为「豪」"),
        TypoEntry("鲁鹅", "卤鹅", "「卤」误写为「鲁」"),
        TypoEntry("鸭母念", "鸭母捻", "「捻」误写为「念」"),
        TypoEntry("工夫荼", "工夫茶", "「茶」误写为「荼」"),
        TypoEntry("凤凰单从", "凤凰单丛", "「丛」误写为「从」"),
        TypoEntry("单枞", "单丛", "凤凰单丛茶的正确写法为「单丛」"),
        TypoEntry("英哥舞", "英歌舞", "「歌」误写为「哥」"),
        TypoEntry("潮居", "潮剧", "「剧」误写为「居」"),
        // —— 通用高频 ——
        TypoEntry("座落", "坐落", "表示位置时正确写法为「坐落」"),
        TypoEntry("渡假", "度假", "「度」误写为「渡」"),
        TypoEntry("按排", "安排", "「安」误写为「按」"),
        TypoEntry("兰天", "蓝天", "「蓝」误写为「兰」"),
        TypoEntry("海摊", "海滩", "「滩」误写为「摊」"),
        TypoEntry("沙摊", "沙滩", "「滩」误写为「摊」"),
        TypoEntry("气侯", "气候", "「候」误写为「侯」"),
        TypoEntry("照像", "照相", "「相」误写为「像」"),
        TypoEntry("欢渡", "欢度", "「度」误写为「渡」"),
        TypoEntry("在见", "再见", "「再」误写为「在」"),
    ).sortedByDescending { it.wrong.length } // 长词优先，避免短词先替换导致长词漏检

    /** 语病规则 1：虚词重复，如「的的」「了了」。 */
    val repeatedParticle = Regex("([的了是和在与])\\1+")

    /** 语病规则 2：语义重复「目的是为了」。 */
    const val redundantPurpose = "目的是为了"

    /** 语病规则 3：「大约/大概/约……左右/上下」语义重复。 */
    val approximateRedundancy = Regex("(大约|大概|约)([^。！？；!?\\n]{0,24}?)(左右|上下)")

    /** 语病规则 4：转折连词缺少呼应。 */
    val concessionConjunctions = listOf("虽然", "尽管")
    val concessionResponses = listOf("但是", "但", "却", "然而", "还是")

    /** 语病规则 5：超长句且完全没有停顿。 */
    const val longSentenceThreshold = 60

    // —— 标点规则 ——
    val halfWidthComma = Regex("(?<=[\\u4e00-\\u9fa5]),(?=[\\u4e00-\\u9fa5])")
    val halfWidthPeriod = Regex("(?<=[\\u4e00-\\u9fa5])\\.(?=[\\u4e00-\\u9fa5])")
    val halfWidthQuestion = Regex("(?<=[\\u4e00-\\u9fa5])\\?(?=[\\u4e00-\\u9fa5]|$)")
    val halfWidthExclamation = Regex("(?<=[\\u4e00-\\u9fa5])!(?=[\\u4e00-\\u9fa5]|$)")
    val duplicatePunctuation = Regex("([。，！？；、：])\\1+")
    val spaceBeforePunctuation = Regex("[ \\t]+([，。！？；：、])")

    /** 句末标点集合，用于判断句末是否缺标点。 */
    val terminalPunctuation = setOf('。', '.', '！', '!', '？', '?', '；', ';', '…', '”', '」')
}
