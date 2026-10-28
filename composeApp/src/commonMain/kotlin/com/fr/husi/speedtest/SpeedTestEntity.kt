package com.fr.husi.speedtest

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.flow.Flow

/**
 * 单次带宽测速结果（独立表，不改动 SagerDatabase / ProxyEntity —— 便于合并上游）。
 *
 * 节点的"当前速度快照"不落 ProxyEntity，统一由 [SpeedTestDatabase.Dao.latestFor]
 * 实时查询，避免任何对既有核心实体的修改。
 *
 * ⚠️ 并发纪律（每次改动本模块都必须重申）：
 * 安卓 sing-box 频繁并发切换出站极易 panic 崩溃。带宽测速的每个会话都是
 * 独立的一次性实例（Go 侧保证），Kotlin 侧并发上限见 [SpeedTestManager]，
 * 绝不在运行实例上切换出站，绝不在服务 reload 过程中发起测速。
 */
@Entity(tableName = "speed_tests")
data class SpeedTestEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var proxyId: Long = 0L,
    var groupId: Long = 0L,
    /** Bytes/s，0 = 该方向未测或测速失败。 */
    var downloadBps: Long = 0L,
    /** Bytes/s，0 = 未启用上传测速或失败。 */
    var uploadBps: Long = 0L,
    /** 非空 = 测速失败原因。 */
    var error: String? = null,
    /** epoch ms。 */
    var testedAt: Long = 0L,
    /** 本次实际使用的测速服务器名（speedtest.net 自动挑选，可为空）。 */
    var serverName: String? = null,
    /**
     * 下载阶段实收字节数与计量时长。
     *
     * 结果可信度自证：`downloadBps ≈ totalDownloadBytes * 1000 / downloadElapsedMs`，
     * 用户在结果对话框里能直接核对，不必翻日志（v2 迁移新增列）。
     */
    var totalDownloadBytes: Long = 0L,
    var downloadElapsedMs: Long = 0L,
    /**
     * 失败类型（Go 侧稳定枚举：connect / timeout / http_status /
     * zero_bytes / discovery / config / cancelled / unsupported）。
     *
     * 只用于展示与解释（"超时"和"连接被拒"是两回事）；自动剔除规则
     * 仍然只认 [SpeedTestManager] 内的 `connect`（v2 迁移新增列）。
     */
    var failureKind: String? = null,
) {

    @androidx.room.Dao
    interface Dao {

        @Insert
        suspend fun insert(entity: SpeedTestEntity): Long

        @Query("SELECT * FROM speed_tests WHERE proxyId = :proxyId ORDER BY testedAt DESC LIMIT 1")
        suspend fun latestFor(proxyId: Long): SpeedTestEntity?

        /**
         * 分组内每个节点**最新一条**记录。
         *
         * 审查 P2-11: 之前是 `SELECT * WHERE groupId=:g ORDER BY testedAt DESC`
         * 再在内存归并 —— 每节点保留 10 条历史, 200 个节点就是 2000 行全量
         * 读进 Kotlin 堆。改成 SQL 侧分组取最大 id(自增主键, 与插入顺序一致)。
         */
        @Query(
            "SELECT * FROM speed_tests WHERE id IN (" +
                "SELECT MAX(id) FROM speed_tests WHERE groupId = :groupId GROUP BY proxyId" +
                ") ORDER BY testedAt DESC",
        )
        suspend fun latestForGroup(groupId: Long): List<SpeedTestEntity>

        /**
         * 节点最近一次结果的响应式流 —— 卡片/结果对话框订阅它,
         * 既不需要 runBlocking 读库, 也能在测速落库后自动刷新(审查 P2-11)。
         */
        @Query("SELECT * FROM speed_tests WHERE proxyId = :proxyId ORDER BY testedAt DESC LIMIT 1")
        fun latestFlow(proxyId: Long): Flow<List<SpeedTestEntity>>

        @Query("SELECT * FROM speed_tests WHERE proxyId = :proxyId ORDER BY testedAt DESC LIMIT :limit")
        fun historyFor(proxyId: Long, limit: Int): Flow<List<SpeedTestEntity>>

        @Query("DELETE FROM speed_tests WHERE proxyId = :proxyId")
        suspend fun deleteByProxy(proxyId: Long)

        @Query("DELETE FROM speed_tests WHERE groupId = :groupId")
        suspend fun deleteByGroup(groupId: Long)

        /**
         * 保留每个节点最近 [keep] 条记录，超出部分清除。
         * 批量测速收尾时按设置调用，防止表无限增长。
         */
        @Query(
            "DELETE FROM speed_tests WHERE id NOT IN (" +
                "SELECT id FROM (" +
                "SELECT id, ROW_NUMBER() OVER (PARTITION BY proxyId ORDER BY testedAt DESC) AS rn " +
                "FROM speed_tests" +
                ") WHERE rn <= :keep" +
                ")",
        )
        suspend fun prune(keep: Int)
    }
}

@Database(
    entities = [SpeedTestEntity::class],
    version = 2,
    exportSchema = false,
)
@ConstructedBy(SpeedTestDatabaseConstructor::class)
abstract class SpeedTestDatabase : RoomDatabase() {

    abstract fun speedTestDao(): SpeedTestEntity.Dao

    companion object {
        val instance by lazy { SpeedTestDatabaseProvider.create() }

        val dao: SpeedTestEntity.Dao get() = instance.speedTestDao()

        /**
         * v1 → v2：结果表补 4 列（服务器名 / 下载实收字节 / 下载耗时 / 失败类型）。
         *
         * 只 ADD COLUMN，不改不删既有列，旧记录的新列取默认值（NULL / 0），
         * 老数据仍然可用。Room 的 KMP 迁移入口是 [Migration.migrate] 的
         * `SQLiteConnection` 版本（androidx.room 2.7+）。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE speed_tests ADD COLUMN serverName TEXT")
                connection.execSQL("ALTER TABLE speed_tests ADD COLUMN totalDownloadBytes INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE speed_tests ADD COLUMN downloadElapsedMs INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE speed_tests ADD COLUMN failureKind TEXT")
            }
        }

        private fun SQLiteConnection.execSQL(sql: String) {
            prepare(sql).use { it.step() }
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SpeedTestDatabaseConstructor : RoomDatabaseConstructor<SpeedTestDatabase>
