package com.fr.husi.speedtest

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
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
) {

    @androidx.room.Dao
    interface Dao {

        @Insert
        suspend fun insert(entity: SpeedTestEntity): Long

        @Query("SELECT * FROM speed_tests WHERE proxyId = :proxyId ORDER BY testedAt DESC LIMIT 1")
        suspend fun latestFor(proxyId: Long): SpeedTestEntity?

        /** 分组内全部测速记录(时间倒序); 调用方内存归并为每节点最新一条。 */
        @Query("SELECT * FROM speed_tests WHERE groupId = :groupId ORDER BY testedAt DESC")
        suspend fun latestForGroup(groupId: Long): List<SpeedTestEntity>

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
    version = 1,
    exportSchema = false,
)
@ConstructedBy(SpeedTestDatabaseConstructor::class)
abstract class SpeedTestDatabase : RoomDatabase() {

    abstract fun speedTestDao(): SpeedTestEntity.Dao

    companion object {
        val instance by lazy { SpeedTestDatabaseProvider.create() }

        val dao: SpeedTestEntity.Dao get() = instance.speedTestDao()
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SpeedTestDatabaseConstructor : RoomDatabaseConstructor<SpeedTestDatabase>
