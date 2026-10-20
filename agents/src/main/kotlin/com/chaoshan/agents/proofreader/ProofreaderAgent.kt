package com.chaoshan.agents.proofreader

import com.chaoshan.agents.core.Agent
import com.chaoshan.agents.core.AgentExecutionException
import com.chaoshan.agents.model.AgentRequest
import com.chaoshan.agents.model.AgentStatus
import com.chaoshan.agents.model.IssueType
import com.chaoshan.agents.model.ProofreadIssue
import com.chaoshan.agents.model.ProofreadResult
import com.chaoshan.agents.model.requireText
import com.chaoshan.agents.support.TextSplitter

/**
 * 校对专家（Proofreader Agent）。
 *
 * 职责：检查错别字、语病、标点问题，返回校对后的文本与问题清单。
 *
 * 实现说明：
 * - 三类检查（[checkTypos] / [checkGrammar] / [checkPunctuation]）都会真实执行，
 *   并把执行过的规则名写入 [ProofreadResult.checksPerformed]，
 *   因此不会出现「未检查却声称校对完成」的情况。
 * - 每条问题都带 [ProofreadIssue.applied]，标明是否真的改写进了 correctedText。
 * - 没有发现问题时返回 SUCCESS + 空的 issues + 明确的 conclusion。
 */
class ProofreaderAgent : Agent<AgentRequest, ProofreadResult> {

    override val name: String = "ProofreaderAgent"

    override val role: String = "校对专家：检查错别字、语病与标点问题，并给出修改说明"

    override fun process(input: AgentRequest): ProofreadResult {
        val started = System.nanoTime()
        return try {
            val original = input.text.requireText("待校对文本")
            var working = original
            val issues = mutableListOf<ProofreadIssue>()
            val checks = mutableListOf<String>()

            working = checkTypos(working, issues, checks)
            working = checkGrammar(working, issues, checks)
            working = checkPunctuation(working, issues, checks)

            val appliedCount = issues.count { it.applied }
            val conclusion = if (issues.isEmpty()) {
                "已执行 ${checks.size} 组检查（${checks.joinToString("、")}），未发现错别字、语病与标点问题，文本保持原样"
            } else {
                "已执行 ${checks.size} 组检查，共发现 ${issues.size} 处问题，其中 $appliedCount 处已自动修正、" +
                    "${issues.size - appliedCount} 处需人工确认"
            }

            ProofreadResult(
                agentName = name,
                status = AgentStatus.SUCCESS,
                correctedText = working,
                issues = issues.toList(),
                checksPerformed = checks.toList(),
                issueCountByType = issues.groupingBy { it.type }.eachCount(),
                conclusion = conclusion,
                elapsedMillis = elapsedMillis(started),
            )
        } catch (e: AgentExecutionException) {
            failed(started, e.message ?: "校对失败")
        } catch (e: Exception) {
            failed(started, "校对过程中发生异常：${e.javaClass.simpleName} - ${e.message}")
        }
    }

    // ---------------------------------------------------------------- 错别字

    private fun checkTypos(
        text: String,
        issues: MutableList<ProofreadIssue>,
        checks: MutableList<String>,
    ): String {
        var working = text
        var hit = 0
        for (entry in ProofreadRules.typos) {
            var index = working.indexOf(entry.wrong)
            while (index >= 0) {
                issues += ProofreadIssue(
                    type = IssueType.TYPO,
                    original = entry.wrong,
                    suggestion = entry.correct,
                    message = "错别字：${entry.wrong} -> ${entry.correct}（${entry.note}）",
                    applied = true,
                )
                hit++
                working = working.replaceRange(index, index + entry.wrong.length, entry.correct)
                index = working.indexOf(entry.wrong, index + entry.correct.length)
            }
        }
        checks += "错别字词典检查（${ProofreadRules.typos.size} 条词条，命中 $hit 处）"
        return working
    }

    // ------------------------------------------------------------------ 语病

    private fun checkGrammar(
        text: String,
        issues: MutableList<ProofreadIssue>,
        checks: MutableList<String>,
    ): String {
        var working = text
        var hit = 0

        // 规则 1：虚词重复
        val repeated = ProofreadRules.repeatedParticle
        repeated.findAll(working).toList().forEach { m ->
            issues += ProofreadIssue(
                type = IssueType.GRAMMAR,
                original = m.value,
                suggestion = m.value[0].toString(),
                message = "语病：虚词重复「${m.value}」，应简化为「${m.value[0]}」",
                applied = true,
            )
            hit++
        }
        working = repeated.replace(working) { it.groupValues[1] }

        // 规则 2：语义重复「目的是为了」
        var index = working.indexOf(ProofreadRules.redundantPurpose)
        while (index >= 0) {
            issues += ProofreadIssue(
                type = IssueType.GRAMMAR,
                original = ProofreadRules.redundantPurpose,
                suggestion = "目的是",
                message = "语病：「目的是为了」语义重复，可改为「目的是」或「是为了」",
                applied = true,
            )
            hit++
            working = working.replaceRange(index, index + ProofreadRules.redundantPurpose.length, "目的是")
            index = working.indexOf(ProofreadRules.redundantPurpose, index)
        }

        // 规则 3：「大约/大概/约……左右/上下」语义重复
        val approximate = ProofreadRules.approximateRedundancy
        approximate.findAll(working).toList().forEach { m ->
            issues += ProofreadIssue(
                type = IssueType.GRAMMAR,
                original = m.value,
                suggestion = m.value.removePrefix(m.groupValues[1]),
                message = "语病：「${m.groupValues[1]}」与「${m.groupValues[3]}」语义重复，保留其一即可",
                applied = true,
            )
            hit++
        }
        working = approximate.replace(working) { m ->
            m.value.removePrefix(m.groupValues[1])
        }

        // 规则 4：转折连词缺少呼应（只提示，不自动改写）
        TextSplitter.sentences(working).forEach { sentence ->
            val hasConcession = ProofreadRules.concessionConjunctions.any { sentence.contains(it) }
            val hasResponse = ProofreadRules.concessionResponses.any { sentence.contains(it) }
            if (hasConcession && !hasResponse) {
                val conj = ProofreadRules.concessionConjunctions.first { sentence.contains(it) }
                issues += ProofreadIssue(
                    type = IssueType.GRAMMAR,
                    original = sentence,
                    suggestion = "补充「但是 / 却」等呼应连词",
                    message = "语病：出现转折连词「$conj」但句中缺少「但是/却」等呼应词，句子关系不完整",
                    applied = false,
                )
                hit++
            }
            // 规则 5：超长句无停顿（只提示，不自动改写）
            if (sentence.length > ProofreadRules.longSentenceThreshold &&
                !sentence.contains("，") && !sentence.contains("、")
            ) {
                issues += ProofreadIssue(
                    type = IssueType.GRAMMAR,
                    original = sentence,
                    suggestion = "在合适位置断句，拆分为两到三个短句",
                    message = "语病：句子长度 ${sentence.length} 字且中间没有任何停顿，建议断句以提升可读性",
                    applied = false,
                )
                hit++
            }
        }

        checks += "语病规则检查（5 条规则：虚词重复/语义重复×2/连词呼应/长句断句，命中 $hit 处）"
        return working
    }

    // ------------------------------------------------------------------ 标点

    private fun checkPunctuation(
        text: String,
        issues: MutableList<ProofreadIssue>,
        checks: MutableList<String>,
    ): String {
        var working = text
        var hit = 0

        // 规则 1：中文语境中使用半角逗号
        val commaMatches = ProofreadRules.halfWidthComma.findAll(working).count()
        if (commaMatches > 0) {
            issues += ProofreadIssue(
                type = IssueType.PUNCTUATION,
                original = ",",
                suggestion = "，",
                message = "标点：中文文本中检测到 $commaMatches 处半角逗号「,」，应改为全角「，」",
                applied = true,
            )
            hit += commaMatches
            working = ProofreadRules.halfWidthComma.replace(working, "，")
        }

        // 规则 2：半角句号
        val periodMatches = ProofreadRules.halfWidthPeriod.findAll(working).count()
        if (periodMatches > 0) {
            issues += ProofreadIssue(
                type = IssueType.PUNCTUATION,
                original = ".",
                suggestion = "。",
                message = "标点：中文文本中检测到 $periodMatches 处半角句号「.」，应改为全角「。」",
                applied = true,
            )
            hit += periodMatches
            working = ProofreadRules.halfWidthPeriod.replace(working, "。")
        }

        // 规则 3：半角问号与感叹号
        val questionMatches = ProofreadRules.halfWidthQuestion.findAll(working).count()
        if (questionMatches > 0) {
            issues += ProofreadIssue(
                type = IssueType.PUNCTUATION,
                original = "?",
                suggestion = "？",
                message = "标点：中文文本中检测到 $questionMatches 处半角问号「?」，应改为全角「？」",
                applied = true,
            )
            hit += questionMatches
            working = ProofreadRules.halfWidthQuestion.replace(working, "？")
        }
        val exclamationMatches = ProofreadRules.halfWidthExclamation.findAll(working).count()
        if (exclamationMatches > 0) {
            issues += ProofreadIssue(
                type = IssueType.PUNCTUATION,
                original = "!",
                suggestion = "！",
                message = "标点：中文文本中检测到 $exclamationMatches 处半角感叹号「!」，应改为全角「！」",
                applied = true,
            )
            hit += exclamationMatches
            working = ProofreadRules.halfWidthExclamation.replace(working, "！")
        }

        // 规则 4：连续重复标点
        ProofreadRules.duplicatePunctuation.findAll(working).toList().forEach { m ->
            issues += ProofreadIssue(
                type = IssueType.PUNCTUATION,
                original = m.value,
                suggestion = m.value[0].toString(),
                message = "标点：出现连续重复标点「${m.value}」，保留一个「${m.value[0]}」即可",
                applied = true,
            )
            hit++
        }
        working = ProofreadRules.duplicatePunctuation.replace(working) { it.groupValues[1] }

        // 规则 5：中文标点前多余空格
        val spaceMatches = ProofreadRules.spaceBeforePunctuation.findAll(working).count()
        if (spaceMatches > 0) {
            issues += ProofreadIssue(
                type = IssueType.PUNCTUATION,
                original = "空格 + 标点",
                suggestion = "删除标点前的空格",
                message = "标点：检测到 $spaceMatches 处中文标点前有多余空格，已删除",
                applied = true,
            )
            hit += spaceMatches
            working = ProofreadRules.spaceBeforePunctuation.replace(working) { it.groupValues[1] }
        }

        // 规则 6：引号成对检查（只提示，不自动改写）
        val pairIssues = checkQuotePairs(working)
        issues += pairIssues
        hit += pairIssues.size

        // 规则 7：句末缺少标点（自动补全）
        val last = working.trim().lastOrNull()
        if (last != null && last !in ProofreadRules.terminalPunctuation) {
            issues += ProofreadIssue(
                type = IssueType.PUNCTUATION,
                original = last.toString(),
                suggestion = "$last。",
                message = "标点：句末缺少标点，已补充「。」",
                applied = true,
            )
            hit++
            working = working.trimEnd() + "。"
        }

        checks += "标点规则检查（7 条规则：半角符号/重复标点/空格/引号配对/句末标点，命中 $hit 处）"
        return working
    }

    private fun checkQuotePairs(text: String): List<ProofreadIssue> {
        val result = mutableListOf<ProofreadIssue>()
        listOf("「" to "」", "“" to "”").forEach { (open, close) ->
            val openCount = text.count { it == open.single() }
            val closeCount = text.count { it == close.single() }
            if (openCount != closeCount) {
                result += ProofreadIssue(
                    type = IssueType.PUNCTUATION,
                    original = "$open…$close",
                    suggestion = "补齐缺失的引号",
                    message = "标点：引号未成对，左引号 $openCount 个、右引号 $closeCount 个",
                    applied = false,
                )
            }
        }
        return result
    }

    // ------------------------------------------------------------------ 工具

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private fun failed(started: Long, message: String): ProofreadResult = ProofreadResult(
        agentName = name,
        status = AgentStatus.FAILED,
        correctedText = "",
        issues = emptyList(),
        checksPerformed = emptyList(),
        issueCountByType = emptyMap(),
        conclusion = "校对未完成：$message",
        elapsedMillis = elapsedMillis(started),
        errorMessage = message,
    )
}
