package com.fr.husi.speedtest

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * 带宽测速专属设置 —— 独立 DataStore 文件（speedtest.preferences_pb），
 * 不向 com.fr.husi.database.DataStore 添加任何 Key，不触碰核心配置。
 *
 * 与延迟测试设置（connectionTestURL/Timeout/Concurrent）完全独立：
 * 带宽测速不走 URL 延迟测试，服务器选择由 Go 侧自研的发现逻辑完成
 * （见 libcore/speedtest/servers.go）。
 *
 * ⚠️ 安卓 sing-box 频繁并发切换出站极易 panic 崩溃 —— 批量严格串行
 * （同时只有 1 个一次性实例；Go 侧另有 4 会话硬上限兜底）。原来的
 * speedtest_concurrent 并发配置已废弃：它没有任何 UI 入口，也从未被
 * 批量调度读取过（审查 P2-10）。
 */
object SpeedTestSettings {

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: DataStore<Preferences> by lazy {
        val file = File(resolveRepository().filesDir, "speedtest.preferences_pb")
        file.parentFile?.mkdirs()
        // 损坏兜底：这个文件是"可选设置"，丢了只是回默认值；没有 handler
        // 时 DataStore 会抛 CorruptionException，收集方（设置页 / snapshot）
        // 会持续失败，变成"设置页打不开、测速启不来"。直接替换为空白配置。
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = storeScope,
        ) { file }
    }

    private object Key {
        /** 单节点多线程下载连接数（自研计量内核的并发 TCP 连接数）。 */
        val MAX_CONNECTIONS = intPreferencesKey("speedtest_max_connections")

        /** 下载阶段时长上限（秒），到时即停取部分结果。 */
        val DOWNLOAD_SECONDS = intPreferencesKey("speedtest_download_seconds")

        /** 上传阶段时长上限（秒）。 */
        val UPLOAD_SECONDS = intPreferencesKey("speedtest_upload_seconds")

        /** 是否测上传（上传消耗节点流量，默认关闭）。 */
        val MEASURE_UPLOAD = booleanPreferencesKey("speedtest_measure_upload")

        /** 测速失败节点自动删除（对齐 Karing testLatencyAutoRemove，默认关闭）。
         *  只删"确定性失败"（连接被拒/不可达），超时不删。 */
        val AUTO_REMOVE = booleanPreferencesKey("speedtest_auto_remove")

        /** 每节点保留的历史结果条数。 */
        val HISTORY_KEEP = intPreferencesKey("speedtest_history_keep")
    }

    // ---------- 默认值 ----------

    // 经代理链路 RTT 高, 单连接吞吐受 TCP 窗口限制严重; 并发太低会显著
    // 低估带宽。6 连接 + 15s 是稳态与耗时的折中(Karing PC 同量级)。
    private const val DEFAULT_MAX_CONNECTIONS = 6
    private const val DEFAULT_DOWNLOAD_SECONDS = 15
    private const val DEFAULT_UPLOAD_SECONDS = 10
    private const val DEFAULT_HISTORY_KEEP = 10

    // ---------- Flow 读取 ----------

    val maxConnections: Flow<Int> = store.data.map {
        (it[Key.MAX_CONNECTIONS] ?: DEFAULT_MAX_CONNECTIONS).coerceIn(1, 16)
    }
    val downloadSeconds: Flow<Int> = store.data.map {
        (it[Key.DOWNLOAD_SECONDS] ?: DEFAULT_DOWNLOAD_SECONDS).coerceIn(3, 60)
    }
    val uploadSeconds: Flow<Int> = store.data.map {
        (it[Key.UPLOAD_SECONDS] ?: DEFAULT_UPLOAD_SECONDS).coerceIn(3, 60)
    }
    val measureUpload: Flow<Boolean> = store.data.map { it[Key.MEASURE_UPLOAD] ?: false }
    val autoRemove: Flow<Boolean> = store.data.map { it[Key.AUTO_REMOVE] ?: false }
    val historyKeep: Flow<Int> = store.data.map {
        (it[Key.HISTORY_KEEP] ?: DEFAULT_HISTORY_KEEP).coerceIn(1, 100)
    }

    // ---------- 批量测速一次性快照（避免逐项挂起） ----------

    class Snapshot(
        val maxConnections: Int,
        val downloadSeconds: Int,
        val uploadSeconds: Int,
        val measureUpload: Boolean,
        val autoRemove: Boolean,
        val historyKeep: Int,
    )

    suspend fun snapshot(): Snapshot = Snapshot(
        maxConnections = maxConnections.firstOrNull() ?: DEFAULT_MAX_CONNECTIONS,
        downloadSeconds = downloadSeconds.firstOrNull() ?: DEFAULT_DOWNLOAD_SECONDS,
        uploadSeconds = uploadSeconds.firstOrNull() ?: DEFAULT_UPLOAD_SECONDS,
        measureUpload = measureUpload.firstOrNull() ?: false,
        autoRemove = autoRemove.firstOrNull() ?: false,
        historyKeep = historyKeep.firstOrNull() ?: DEFAULT_HISTORY_KEEP,
    )

    // ---------- 写入 ----------
    //
    // UI 入口：设置 → 带宽测速（`ui/settings/SpeedTestSettingsGroup.kt`，
    // 审查 P2-10 已收尾）。改动即时落盘，下一轮测速开始时由 [snapshot]
    // 取一次性快照生效 —— 正在跑的批次不受影响，也不需要 reload 服务。

    suspend fun setMaxConnections(value: Int) = set(Key.MAX_CONNECTIONS, value.coerceIn(1, 16))
    suspend fun setDownloadSeconds(value: Int) = set(Key.DOWNLOAD_SECONDS, value.coerceIn(3, 60))
    suspend fun setUploadSeconds(value: Int) = set(Key.UPLOAD_SECONDS, value.coerceIn(3, 60))
    suspend fun setMeasureUpload(value: Boolean) = set(Key.MEASURE_UPLOAD, value)
    suspend fun setAutoRemove(value: Boolean) = set(Key.AUTO_REMOVE, value)
    suspend fun setHistoryKeep(value: Int) = set(Key.HISTORY_KEEP, value.coerceIn(1, 100))

    /**
     * ⚠️ 必须直接对 `edit` 的 receiver（`MutablePreferences`）赋值。
     *
     * 曾经的写法是 `prefs.toMutablePreferences()[key] = value`：`Preferences`
     * 上的扩展在 receiver 已经是 `MutablePreferences` 时**会再复制一份**
     * （`MutablePreferences(preferencesMap.toMutableMap(), false)`），赋值
     * 落在临时副本上，DataStore 里的值从未改变 → edit 判等后不发射新值
     * → 设置项读回旧值。表现就是 2026-10-27 用户实测的"自动剔除无法连接
     * 的节点"开关点不动（弹回），滑块也只有本地 preview 在动、不落盘。
     */
    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { prefs -> prefs[key] = value }
    }
}
