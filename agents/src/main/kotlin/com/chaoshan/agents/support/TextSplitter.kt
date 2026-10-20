package com.chaoshan.agents.support

/**
 * 中文文本切分工具，供校对专家与行动秘书共用。
 *
 * 这里只做确定性的规则切分，不做任何语义理解。
 */
object TextSplitter {

    private val sentencePattern = Regex("[^。！？；!?;\\n]+[。！？；!?;]?")

    /** 句子切分：按句末标点与换行切分，保留标点。 */
    fun sentences(text: String): List<String> =
        sentencePattern.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()

    /** 分句切分：在句子基础上再按逗号、顿号、分号、冒号切成更小的片段。 */
    fun clauses(text: String): List<String> =
        sentences(text)
            .flatMap { s -> s.split(Regex("[，,、；;：:]")).asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    /** 去掉片段首尾的标点与空白，用于生成干净的待办文案。 */
    fun stripEdges(raw: String): String =
        raw.trim().trim('，', ',', '、', '；', ';', '：', ':', '。', '.', '！', '!', '？', '?')
}
