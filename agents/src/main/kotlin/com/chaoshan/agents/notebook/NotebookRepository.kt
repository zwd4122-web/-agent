package com.chaoshan.agents.notebook

/**
 * 笔记本定义（归档候选）。
 *
 * @param keywords 普通特征词，命中一次记 1.0 分
 * @param strongKeywords 强特征词（主题意图明确），命中一次记 3.0 分。
 *                       区分强弱是为了让「意图」压过「实体名」，避免只提到某个景点名就被归进景点笔记。
 */
data class NotebookRule(
    val id: String,
    val name: String,
    val description: String,
    val keywords: List<String>,
    val strongKeywords: List<String> = emptyList(),
)

/** 存入笔记本的一条笔记。 */
data class NoteRecord(
    val id: String,
    val notebookId: String,
    val text: String,
    val tags: List<String>,
)

/**
 * 笔记本存储抽象。
 *
 * 任务要求「如果系统具有笔记本存储功能，应尽可能复用现有机制；如果没有，
 * 应实现完成该任务所需的最小分类处理机制，不要虚构已经完成的持久化操作」。
 *
 * 本工程为新建工程，不存在既有笔记本存储，因此这里定义最小接口，
 * 并提供一个真实生效的进程内内存实现。实现类必须通过 [storageDescription]
 * 如实声明自己的存储性质，不允许声称做了磁盘/数据库持久化。
 */
interface NotebookRepository {

    /** 存储性质说明，会原样出现在归档结果中，避免误导调用方。 */
    val storageDescription: String

    fun listNotebooks(): List<NotebookRule>

    fun findById(id: String): NotebookRule?

    /** 真实写入实现所使用的存储，并返回写入后的记录。 */
    fun save(record: NoteRecord): NoteRecord

    fun listNotes(notebookId: String): List<NoteRecord>

    fun count(): Int
}

/**
 * 进程内内存实现。
 *
 * 真实行为：笔记会写入内存列表，可立即按笔记本查询回来（测试中会验证）。
 * 明确限制：不做磁盘或数据库持久化，进程结束后数据即丢失。
 */
class InMemoryNotebookRepository(
    notebooks: List<NotebookRule>,
) : NotebookRepository {

    override val storageDescription: String =
        "进程内内存存储（写入后可立即读回，但不做磁盘/数据库持久化，进程退出即丢失）"

    private val notebooks: List<NotebookRule> = notebooks.toList()
    private val notes = mutableListOf<NoteRecord>()

    override fun listNotebooks(): List<NotebookRule> = notebooks.toList()

    override fun findById(id: String): NotebookRule? = notebooks.firstOrNull { it.id == id }

    override fun save(record: NoteRecord): NoteRecord {
        notes.add(record)
        return record
    }

    override fun listNotes(notebookId: String): List<NoteRecord> =
        notes.filter { it.notebookId == notebookId }

    override fun count(): Int = notes.size
}
