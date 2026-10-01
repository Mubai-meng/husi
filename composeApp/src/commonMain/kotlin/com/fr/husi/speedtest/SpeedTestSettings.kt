package com.fr.husi.speedtest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 带宽测速专属设置 —— 独立 DataStore 文件（speedtest.preferences_pb），
 * 不向 com.fr.husi.database.DataStore 添加任何 Key，不触碰核心配置。
 *
 * 与延迟测试设置（connectionTestURL/Timeout/Concurrent）完全独立：
 * 带宽测速不走 URL 延迟测试，speedtest.net 服务器选择在 Go 侧完成。
 *
 * ⚠️ 安卓 sing-box 频繁并发切换出站极易 panic 崩溃 —— 默认并发为 2
 * （远低于延迟测试的 5），且 Go 侧另有 4 会话硬上限兜底。
 */
object SpeedTestSettings {

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: DataStore<Preferences> by lazy {
        val file = File(resolveRepository().filesDir, "speedtest.preferences_pb")
        file.parentFile?.mkdirs()
        PreferenceDataStoreFactory.create(scope = storeScope) { file }
    }

    private object Key {
        /** 批量测速并发（同时测速的节点数 = 同时存活的 sing-box 实例数）。 */
        val CONCURRENT = intPreferencesKey("speedtest_concurrent")

        /** 单节点多线程下载连接数（speedtest-go MaxConnections）。 */
        val MAX_CONNECTIONS = intPreferencesKey("speedtest_max_connections")

        /** 下载阶段时长上限（秒），到时即停取部分结果。 */
        val DOWNLOAD_SECONDS = intPreferencesKey("speedtest_download_seconds")

        /** 上传阶段时长上限（秒）。 */
        val UPLOAD_SECONDS = intPreferencesKey("speedtest_upload_seconds")

        /** 是否测上传（上传消耗节点流量，默认关闭）。 */
        val MEASURE_UPLOAD = booleanPreferencesKey("speedtest_measure_upload")

        /** 测速失败节点自动删除（对齐 Karing testLatencyAutoRemove，默认关闭）。 */
        val AUTO_REMOVE = booleanPreferencesKey("speedtest_auto_remove")

        /** 每节点保留的历史结果条数。 */
        val HISTORY_KEEP = intPreferencesKey("speedtest_history_keep")
    }

    // ---------- 默认值 ----------

    private const val DEFAULT_CONCURRENT = 2
    // 经代理链路 RTT 高, 单连接吞吐受 TCP 窗口限制严重; 并发太低会显著
    // 低估带宽。6 连接 + 15s 是稳态与耗时的折中(Karing PC 同量级)。
    private const val DEFAULT_MAX_CONNECTIONS = 6
    private const val DEFAULT_DOWNLOAD_SECONDS = 15
    private const val DEFAULT_UPLOAD_SECONDS = 10
    private const val DEFAULT_HISTORY_KEEP = 10

    // ---------- Flow 读取 ----------

    val concurrent: Flow<Int> = store.data.map {
        (it[Key.CONCURRENT] ?: DEFAULT_CONCURRENT).coerceIn(1, 2)
    }
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
        val concurrent: Int,
        val maxConnections: Int,
        val downloadSeconds: Int,
        val uploadSeconds: Int,
        val measureUpload: Boolean,
        val autoRemove: Boolean,
        val historyKeep: Int,
    )

    suspend fun snapshot(): Snapshot = Snapshot(
        concurrent = concurrent.firstOrNull() ?: DEFAULT_CONCURRENT,
        maxConnections = maxConnections.firstOrNull() ?: DEFAULT_MAX_CONNECTIONS,
        downloadSeconds = downloadSeconds.firstOrNull() ?: DEFAULT_DOWNLOAD_SECONDS,
        uploadSeconds = uploadSeconds.firstOrNull() ?: DEFAULT_UPLOAD_SECONDS,
        measureUpload = measureUpload.firstOrNull() ?: false,
        autoRemove = autoRemove.firstOrNull() ?: false,
        historyKeep = historyKeep.firstOrNull() ?: DEFAULT_HISTORY_KEEP,
    )

    /** 与 husi 既有 DataStore 风格一致的阻塞读取（仅用于非协程上下文）。 */
    fun snapshotBlocking(): Snapshot = runBlocking { snapshot() }

    // ---------- 写入（设置页/调试用，UI 可后补） ----------

    suspend fun setConcurrent(value: Int) = set(Key.CONCURRENT, value.coerceIn(1, 2))
    suspend fun setMaxConnections(value: Int) = set(Key.MAX_CONNECTIONS, value.coerceIn(1, 16))
    suspend fun setDownloadSeconds(value: Int) = set(Key.DOWNLOAD_SECONDS, value.coerceIn(3, 60))
    suspend fun setUploadSeconds(value: Int) = set(Key.UPLOAD_SECONDS, value.coerceIn(3, 60))
    suspend fun setMeasureUpload(value: Boolean) = set(Key.MEASURE_UPLOAD, value)
    suspend fun setAutoRemove(value: Boolean) = set(Key.AUTO_REMOVE, value)
    suspend fun setHistoryKeep(value: Int) = set(Key.HISTORY_KEEP, value.coerceIn(1, 100))

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { prefs -> prefs.toMutablePreferences()[key] = value }
    }
}
