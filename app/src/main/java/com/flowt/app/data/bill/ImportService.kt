package com.flowt.app.data.bill

import androidx.room.withTransaction
import com.flowt.app.data.CategoryException
import com.flowt.app.data.CategoryRepository
import com.flowt.app.data.ImportedFileRecord
import com.flowt.app.data.PrefsRepository
import com.flowt.app.data.TxRepository
import com.flowt.app.data.db.FlowtDatabase
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.data.db.TxFingerprint
import com.flowt.app.data.fingerprint
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/** 解析好的文件：还没碰数据库，纯粹是"读出来了"。 */
data class ParsedFile(
    val fileName: String,
    val fileHash: String,
    /** 原始二维表格。用户在预览页改列绑定时，靠它在内存里重算，不必重读文件。 */
    val table: List<List<String>>,
    val parsed: ParsedBill,
)

/**
 * 导入的编排层：解析 → 计划 → 落库 → 撤销。
 *
 * 所有 Room 写入都在**同一个事务**里完成（建缺失分类 + 插入流水），
 * 中途失败全部回滚，不会留下"分类建好了但流水没进来"的半截状态。
 */
class ImportService(
    private val db: FlowtDatabase,
    private val txRepo: TxRepository,
    private val categoryRepo: CategoryRepository,
    private val prefsRepo: PrefsRepository,
) {

    /**
     * 读文件并解析成行。不碰数据库，所以可以放心在 IO 线程上跑。
     *
     * [mode] 只用来做**格式校验**：从「Flowt 备份」入口进来却没认出自家表头时
     * 明确报错，而不是让用户一路点到底才发现导错了东西。
     */
    fun parseFile(bytes: ByteArray, fileName: String, mode: ImportMode): ParsedFile {
        if (bytes.isEmpty()) throw BillFormatException("这个文件是空的")

        val reader = TableReaders.byFileName(fileName)
            ?: throw BillFormatException("不认识这个格式。${TableReaders.SUPPORTED_HINT}")

        val table = reader.read(bytes)
        val parsed = BillTable.parse(table)

        if (mode == ImportMode.SelfBackup && !looksLikeFlowtBackup(parsed.headers)) {
            throw BillFormatException("这不像 Flowt 导出的备份文件，请从「二级分类结构账单」导入")
        }

        return ParsedFile(
            fileName = fileName,
            fileHash = sha256(bytes),
            table = table,
            parsed = parsed,
        )
    }

    /**
     * 用户在预览页改了列绑定后重算：解析与计划都要跟着变，源表格一直在内存里。
     *
     * [keepMappings] 是上一轮的分类去向，重算时把仍然存在的分类的选择保留下来 ——
     * 否则用户调一下列绑定，之前手工指定的分类去向就全丢了。
     */
    suspend fun rebuild(
        file: ParsedFile,
        mode: ImportMode,
        binding: ColumnBinding,
        previousMappings: List<CategoryMapping> = emptyList(),
    ): ImportPlan {
        val reparsed = BillTable.parse(file.table, binding)
        val rebuilt = file.copy(parsed = reparsed)
        val plan = buildPlan(rebuilt, mode)

        val previous = previousMappings.associateBy { it.sourcePath }
        val merged = plan.mappings.map { mapping ->
            val kept = previous[mapping.sourcePath]?.target
            if (kept != null) mapping.copy(target = kept) else mapping
        }

        return plan.copy(
            mappings = merged,
            duplicateRowIndexes = findDuplicates(reparsed.rows, merged, txRepo.fingerprintSnapshot()),
        )
    }

    /** 分类去向改了之后重算重复行：改成"绑定到已有分类"就可能与库里的记录重复。 */
    suspend fun recountDuplicates(rows: List<ImportedRow>, mappings: List<CategoryMapping>): Set<Int> =
        findDuplicates(rows, mappings, txRepo.fingerprintSnapshot())

    /** 按当前分类树算出每个账单分类的去向，并算清会跳过多少笔。 */
    suspend fun buildPlan(file: ParsedFile, mode: ImportMode): ImportPlan {
        val categories = categoryRepo.getAll()
        val idByPath = categories.associate { it.path to it.id }
        val mappings = buildMappings(file.parsed.rows, idByPath)
        val snapshot = txRepo.fingerprintSnapshot()

        return ImportPlan(
            mode = mode,
            fileName = file.fileName,
            fileHash = file.fileHash,
            parsed = file.parsed,
            mappings = mappings,
            duplicateRowIndexes = findDuplicates(file.parsed.rows, mappings, snapshot),
            previousImport = prefsRepo.prefs.first().importedFiles.firstOrNull { it.hash == file.fileHash },
        )
    }

    /**
     * 落实导入。[mappings] 是用户在预览页敲定的最终去向，可能与计划里的默认值不同。
     */
    suspend fun execute(plan: ImportPlan, mappings: List<CategoryMapping>): ImportOutcome {
        val snapshot = txRepo.fingerprintSnapshot()
        // 本地已有分类的 path → id，逐级新建时用来避免重复建
        val idByPath = categoryRepo.getAll().associate { it.path to it.id }.toMutableMap()
        val batchSource = buildBatchSource(plan.mode)

        var createdCategories = 0
        var skippedByMapping = 0
        var skippedDuplicate = 0
        val pending = mutableListOf<TransactionEntity>()

        db.withTransaction {
            // 1. 先落实分类去向：**账单里的分类路径** → 最终要落的 categoryId
            //
            //    必须走 mappings，不能拿账单里的路径直接查本地分类表 —— 用户把
            //    「本地没有的分类」改指到某个已有分类时，那个账单路径在本地根本不存在，
            //    只有映射表知道它该落到哪儿。预览判重（findDuplicates）一直是这么算的，
            //    execute 早先漏了，于是出现"预览说能导 7 笔、执行却跳过 7 笔"。
            val categoryIdOf = mutableMapOf<String, Long>()
            mappings.forEach { mapping ->
                when (val target = mapping.target) {
                    is MappingTarget.Existing -> {
                        categoryIdOf[mapping.sourcePath] = target.categoryId
                    }

                    MappingTarget.Create -> {
                        val (id, created) = ensureCategory(mapping.sourcePath, idByPath)
                        categoryIdOf[mapping.sourcePath] = id
                        if (created) createdCategories++
                    }

                    // 跳过的不进表，组流水时自然找不到 → 计入跳过数
                    MappingTarget.Skip -> Unit
                }
            }

            // 2. 组出待插入的流水
            plan.parsed.rows.forEach { row ->
                val categoryId = categoryIdOf[row.categoryPath ?: UNCATEGORIZED_PATH]
                if (categoryId == null) {
                    skippedByMapping++
                    return@forEach
                }

                val entity = TransactionEntity(
                    amountCents = row.amountCents,
                    timestamp = row.timestamp,
                    categoryId = categoryId,
                    note = row.note,
                    type = row.type,
                    source = batchSource,
                )

                // 只与"导入前"的已有数据比对：文件内的重复保留（见 ImportPlan 的说明）
                if (entity.fingerprint() in snapshot) {
                    skippedDuplicate++
                    return@forEach
                }
                pending += entity
            }

            // 3. 落库
            txRepo.insertAll(pending)
        }

        // 已导入文件的记录是 DataStore、不受 Room 事务保护，
        // 所以放到事务成功之后再写，避免"事务回滚了但记录留下了"。
        prefsRepo.recordImportedFile(
            ImportedFileRecord(
                hash = plan.fileHash,
                fileName = plan.fileName,
                importedAt = System.currentTimeMillis(),
                count = pending.size,
                batchSource = batchSource,
            ),
        )

        return ImportOutcome(
            imported = pending.size,
            skippedDuplicate = skippedDuplicate,
            skippedIncome = plan.parsed.skippedIncome,
            skippedInvalid = plan.parsed.invalidRows.size,
            skippedByMapping = skippedByMapping,
            createdCategories = createdCategories,
        )
    }

    /**
     * 撤销**上一次**导入：按批次删掉那批流水，并抹掉对应的文件记录 ——
     * 撤销是完全回滚，用户想重导不该再被警告。
     *
     * 判定依据是记在流水上的**批次号**，不是流水 id 列表：编辑流水时 `source` 是保留的
     * （EntryScreen 只改被编辑的字段），所以用户改过金额、分类、备注之后照样撤得掉 ——
     * 而库里只多存了一个字符串，不是几千个 id。
     *
     * 自动创建的分类**保留**：用户此后可能已经改过它们、甚至往里记了账，
     * 连带删除是二次损失。
     *
     * 没有可撤销的导入时返回 null：从没导过、刚清空过数据、或那条记录来自旧版本
     * （没有批次号）。
     */
    suspend fun undoLastImport(): UndoResult? {
        val last = prefsRepo.prefs.first().importedFiles.firstOrNull() ?: return null
        if (last.batchSource.isEmpty()) return null

        val deleted = txRepo.deleteBySource(last.batchSource)
        prefsRepo.forgetImportedFile(last.hash)
        return UndoResult(fileName = last.fileName, importedAt = last.importedAt, deleted = deleted)
    }

    // --- 内部 ---

    private fun buildMappings(
        rows: List<ImportedRow>,
        idByPath: Map<String, Long>,
    ): List<CategoryMapping> = rows
        .groupBy { it.categoryPath ?: UNCATEGORIZED_PATH }
        .map { (path, group) ->
            val existingId = idByPath[path]
            CategoryMapping(
                sourcePath = path,
                target = if (existingId != null) {
                    MappingTarget.Existing(existingId, path)
                } else {
                    MappingTarget.Create
                },
                rowCount = group.size,
            )
        }
        .sortedByDescending { it.rowCount }

    /**
     * 与库里已有流水重复的行下标。
     *
     * 只有能确定分类 id 的行才可能重复 —— 标着「新建」的分类本地还不存在，
     * 已有流水自然不可能引用它。
     */
    private fun findDuplicates(
        rows: List<ImportedRow>,
        mappings: List<CategoryMapping>,
        snapshot: Set<TxFingerprint>,
    ): Set<Int> {
        val idByPath = mappings.mapNotNull { mapping ->
            (mapping.target as? MappingTarget.Existing)?.let { mapping.sourcePath to it.categoryId }
        }.toMap()

        return rows.indices.filterTo(mutableSetOf()) { index ->
            val row = rows[index]
            val categoryId = idByPath[row.categoryPath ?: UNCATEGORIZED_PATH] ?: return@filterTo false
            TxFingerprint(row.timestamp, row.amountCents, categoryId, row.note) in snapshot
        }
    }

    /**
     * 确保这个路径上的每一级分类都存在，返回末级分类的 id。
     *
     * 逐级走 [CategoryRepository.create]，而不是自己拼 SQL：path 的生成规则、
     * 同级重名检查都在那里，这里重写一遍只会让两处规则慢慢跑偏。
     */
    private suspend fun ensureCategory(
        path: String,
        idByPath: MutableMap<String, Long>,
    ): Pair<Long, Boolean> {
        val segments = path.split(PATH_SEPARATOR).filter { it.isNotBlank() }
        if (segments.isEmpty()) throw CategoryException("分类路径「$path」无效")

        var parentId: Long? = null
        var currentPath = ""
        var created = false

        segments.forEach { segment ->
            currentPath = if (currentPath.isEmpty()) {
                segment
            } else {
                "$currentPath$PATH_SEPARATOR$segment"
            }

            val known = idByPath[currentPath]
            if (known != null) {
                parentId = known
            } else {
                val newId = categoryRepo.create(parentId = parentId, name = segment)
                idByPath[currentPath] = newId
                parentId = newId
                created = true
            }
        }

        return (parentId ?: throw CategoryException("分类路径「$path」无效")) to created
    }

    /** 批次号：够唯一即可，用来支持"撤销本次导入"。 */
    private fun buildBatchSource(mode: ImportMode): String {
        val kind = if (mode == ImportMode.SelfBackup) "backup" else "bill"
        return "import:$kind:${System.currentTimeMillis()}"
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/** 账单里没有分类时归到这里，保证"一笔流水必须绑定一个分类"这条规则不被导入打破。 */
const val UNCATEGORIZED_PATH = "未分类"
