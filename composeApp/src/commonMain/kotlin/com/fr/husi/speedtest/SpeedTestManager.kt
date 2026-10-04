package com.fr.husi.speedtest

import com.fr.husi.bg.ServiceState
import com.fr.husi.bg.buildPluginSpecs
import com.fr.husi.bg.initPlugins
import com.fr.husi.core.CoreClient
import com.fr.husi.core.ServiceEvent
import com.fr.husi.database.DataStore
import com.fr.husi.database.SagerDatabase
import com.fr.husi.fmt.buildConfig
import com.fr.husi.GroupOrder
import com.fr.husi.ktx.readableMessage
import com.fr.husi.plugin.PluginNotFoundException
import com.fr.husi.repository.resolveRepository
import com.fr.husi.ui.configuration.proxyDisplayComparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * 批量带宽测速调度器 —— 与延迟测试（ConfigurationScreenViewModel.testJob
 * 一线）完全独立的任务系统：独立作用域、独立状态、独立结果存储。
 *
 * ⚠️ 并发纪律（每次改动本模块都必须重申）：
 * 安卓 sing-box 频繁并发切换出站极易 panic 崩溃。
 *   1. 本调度器只通过 [SpeedTestEngine] 创建独立一次性实例，
 *      绝不触碰正在运行的 sing-box 服务实例，绝不做运行实例出站切换；
 *   2. 批量严格顺序执行（从第一个节点按列表顺序逐个测，用户要求；
 *      顺带彻底消除并发出站切换），单节点入口队列长度 1；
 *   3. 绝不在服务 reload / 订阅更新过程中发起批量测速；
 *   4. 单个会话失败/超时只跳过该节点，不影响队列其余任务。
 */
object SpeedTestManager {

    // 独立作用域：不挂在任何 ViewModel 上，切屏不中断批量任务；
    // cancel() 显式停止。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutex = Mutex()
    private var job: Job? = null

    private fun resolveCoreClient(): CoreClient = GlobalContext.get().get()

    /** 单次守护进程状态查询的上限（VPN 未运行时连接会悬挂，必须限时）。 */
    private const val SERVICE_STATE_SYNC_TIMEOUT_MS = 3_000L

    /**
     * 批量测速单节点硬超时：到点自动切下一个节点（用户要求 5 秒）。
     * 单节点入口（节点卡片"测速此节点"）不套此上限，按设置跑满时长。
     */
    const val NODE_TIMEOUT_MS = 5_000L

    /** 超时切下一个节点前的缓冲：speedTestStop 在 IO 线程异步执行
     * （最长阻塞 35s 等实例关闭），Go 侧并发会话上限 4，稍等片刻
     * 避免连续超时时旧实例堆积挤占会话配额。 */
    private const val NODE_TIMEOUT_COOLDOWN_MS = 500L

    /** UI 状态（对齐延迟测试的 ConfigurationTestUiState 语义，但独立持有）。 */
    data class UiState(
        val running: Boolean = false,
        val total: Int = 0,
        val processed: Int = 0,
        /** 正在测速的节点 → 当前实时速率（Bytes/s）。 */
        val liveRates: Map<Long, Long> = emptyMap(),
        val latestError: String? = null,
        /** 进行中的辅助状态文案（如"正在启动网络隧道…"），进度条区域显示。 */
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 当前已加载分组的节点测速结果缓存（proxyId → 最新一条）。
     * 节点卡片状态行展示"测速结果在延迟前"的数据源；
     * 由 [loadGroupResults] 按组加载，测速落库/清除时同步更新。
     */
    private val _groupResults = MutableStateFlow<Map<Long, SpeedTestEntity>>(emptyMap())
    val groupResults: StateFlow<Map<Long, SpeedTestEntity>> = _groupResults.asStateFlow()

    /** 加载某分组的节点最新测速结果（卡片展示前调用）。 */
    suspend fun loadGroupResults(groupId: Long) {
        val latest = SpeedTestDatabase.dao.latestForGroup(groupId)
            .groupBy { it.proxyId }
            .mapValues { (_, list) -> list.first() }
        _groupResults.value = latest
    }

    fun isRunning(): Boolean = _uiState.value.running

    /**
     * 批量测速一个分组。已有任务运行中则忽略（返回 false）。
     * 结果逐条写入 [SpeedTestDatabase]；失败自动删除可选
     * （SpeedTestSettings.autoRemove，对齐 Karing testLatencyAutoRemove）。
     *
     * @param perNodeTimeoutMs 单节点硬超时；null = 不限时（单节点入口用）。
     *   超时的节点记为错误结果并自动切下一个。
     */
    fun startGroup(
        groupId: Long,
        profiles: List<ProxyEntityParams>,
        perNodeTimeoutMs: Long? = NODE_TIMEOUT_MS,
    ): Boolean {
        if (profiles.isEmpty()) return false
        // 非挂起入口：tryLock 抢占，避免阻塞 UI 线程。
        if (!mutex.tryLock()) return false
        try {
            val current = job
            if (current?.isActive == true) {
                // 一致性守护：running/message 均已复位但 job 仍挂着
                // （收尾阶段卡住等）→ 强制让位，避免"点测速没反应，
                // 必须重启应用才能恢复"（2026-10 用户实测反馈）。
                // running=true 或停止服务期间（message 非空）= 正常占用，
                // 维持原拒绝语义。
                val state = _uiState.value
                if (state.running || state.message != null) return false
                current.cancel()
            }
            job = scope.launch { runBatch(groupId, profiles, perNodeTimeoutMs) }
        } finally {
            mutex.unlock()
        }
        return true
    }

    /** 单节点测速（节点卡片菜单入口），同样走批量管道（队列长度=1，不限时）。 */
    fun startSingle(groupId: Long, profile: ProxyEntityParams): Boolean =
        startGroup(groupId, listOf(profile), perNodeTimeoutMs = null)

    /** 停止当前批量任务（幂等）。已完成的单条结果保留。 */
    fun cancel() {
        job?.cancel()
    }

    /** 节点最近一次测速结果（卡片展示用）。 */
    suspend fun latestFor(proxyId: Long): SpeedTestEntity? =
        SpeedTestDatabase.dao.latestFor(proxyId)

    /** 节点测速历史。 */
    fun historyFor(proxyId: Long, limit: Int = 20): Flow<List<SpeedTestEntity>> =
        SpeedTestDatabase.dao.historyFor(proxyId, limit)

    /** 清除某节点结果（卡片菜单入口）。 */
    suspend fun clearFor(proxyId: Long) {
        SpeedTestDatabase.dao.deleteByProxy(proxyId)
        _groupResults.update { it - proxyId }
    }

    /** 清除某分组结果。 */
    suspend fun clearGroup(groupId: Long) {
        SpeedTestDatabase.dao.deleteByGroup(groupId)
        _groupResults.update { entries ->
            entries.filterValues { it.groupId != groupId }
        }
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 批量任务入参（避免把 Room 实体跨线程传递时携带脏状态）。 */
    class ProxyEntityParams(
        val id: Long,
        val groupId: Long,
    )

    private suspend fun runBatch(
        groupId: Long,
        profiles: List<ProxyEntityParams>,
        perNodeTimeoutMs: Long?,
    ) {
        val settings = SpeedTestSettings.snapshot()
        _uiState.update { UiState(running = true, total = profiles.size) }

        var autoStarted = false
        val results = mutableListOf<SpeedTestEntity>()
        // ⚠️ 整个批次（含服务启动段）必须在 try/finally 内：running=true
        // 一旦置位，任何退出路径（取消、异常、提前 return）都必须复位，
        // 否则 UI 永远显示"测速进行中"，后续所有测速入口全部无响应，
        // 只能重启应用（2026-10 用户实测反馈的根因）。
        try {
            // 可靠测速路径需要 :bg 进程的 protect 通道（服务运行中 → bridge）；
            // 未启动服务时本地实例直连节点，在运营商网络下大多被拒绝。
            // 用户要求：测速前自动启动服务，测完自动恢复原状态。
            //
            // ⚠️ 路由正确性前提：DataStore.serviceState 决定引擎的 bridge/local
            // 选择，而它是事件镜像（WhileSubscribed 5s 停更）的离线副本，可能
            // 严重过期 —— 过期会导致"VPN 实际在跑却误走本地 JNI（无 protect）"，
            // 测速出站被本机 TUN 全量劫持：测速变成双重代理、流量全部计入
            // 被测节点统计（2026-10-01 三轮日志实测）。因此每次批量前先向
            // :bg 守护进程做一次性状态同步（订阅即重放 lastState），之后的
            // 轮询也直接问守护进程，不再信任本地镜像。
            val synced = refreshServiceState()
            if (synced != null) {
                DataStore.serviceState = synced.state
            } else {
                // 守护进程不可达（带超时确认）→ :bg 进程不在 → VPN 服务物理上
                // 必然未运行 → 过期的"已启动"镜像不可信，强制纠正为 Idle，
                // 否则引擎会误走 bridge 并悬挂/失败。
                DataStore.serviceState = ServiceState.Idle
            }

            if (!DataStore.serviceState.started) {
                _uiState.update { it.copy(message = "正在启动网络隧道…") }
                runCatching { resolveRepository().startService() }
                    .onSuccess { autoStarted = true }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(message = "服务启动失败: " + e.readableMessage)
                        }
                    }
                if (autoStarted) {
                    if (awaitServiceStarted(timeoutMs = 30_000)) {
                        _uiState.update { it.copy(message = null) }
                    } else {
                        // 守护进程确认 30s 仍未 started：TUN 是否存在不可知，
                        // 此时本地直连可能被 TUN 劫持（双重代理 + 流量误计），
                        // 宁可取消本次测速，也不能给出污染的结果。
                        autoStarted = false
                        _uiState.update {
                            it.copy(
                                running = false,
                                message = "服务启动确认超时，已取消本次测速（可稍后重试）",
                            )
                        }
                        return
                    }
                }
            }

            // ---- 顺序批量：严格从第一个节点按列表顺序逐个测（用户要求）。
            // 顺序执行同时天然规避了 sing-box 并发出站切换的 panic 风险
            // （⚠️ 安卓 sing-box 频繁并发切换出站极易 panic —— 见类注释）。
            for (profile in profiles) {
                currentCoroutineContext().ensureActive()
                val entity = withContext(Dispatchers.IO) {
                    if (perNodeTimeoutMs == null) {
                        testProfile(profile, settings)
                    } else {
                        withTimeoutOrNull(perNodeTimeoutMs) {
                            testProfile(profile, settings)
                        } ?: SpeedTestEntity(
                            proxyId = profile.id,
                            groupId = profile.groupId,
                            testedAt = System.currentTimeMillis(),
                        ).copy(error = "测速超时（单节点 ${perNodeTimeoutMs / 1000} 秒上限）")
                    }
                }
                results.add(entity)
                _uiState.update {
                    it.copy(
                        processed = it.processed + 1,
                        liveRates = it.liveRates - entity.proxyId,
                    )
                }
                // 每条结果立即落库，中途取消也不丢已完成部分。
                SpeedTestDatabase.dao.insert(entity)
                // 同步刷新卡片展示缓存（测速结果在延迟前显示）。
                _groupResults.update { it + (entity.proxyId to entity) }
                if (entity.error?.startsWith("测速超时") == true) {
                    // 超时切下一个节点前给旧实例关闭留缓冲（speedTestStop
                    // 在 IO 线程异步执行，Go 侧并发会话上限 4）。
                    delay(NODE_TIMEOUT_COOLDOWN_MS)
                }
            }
        } finally {
            // 历史裁剪（按设置保留每节点最近 N 条）。
            runCatching { SpeedTestDatabase.dao.prune(settings.historyKeep) }

            // 失败自动剔除（可选，对齐 Karing testLatencyAutoRemove）。
            if (settings.autoRemove) {
                val failedIds = results.filter { it.error != null }.map { it.proxyId }
                for (id in failedIds) {
                    runCatching { SagerDatabase.proxyDao.deleteById(id) }
                }
            }

            val lastError = results.lastOrNull { it.error != null }?.error
            _uiState.update {
                it.copy(running = false, liveRates = emptyMap(), latestError = lastError)
            }

            // 测速前由本任务自动启动的服务 → 测完自动关闭，恢复原状态。
            if (autoStarted && DataStore.serviceState.canStop) {
                _uiState.update { it.copy(message = "正在停止网络隧道…") }
                runCatching { resolveRepository().stopService() }
                // 等状态离开 started，避免 UI 残留"运行中"错觉。
                awaitServiceStopped(timeoutMs = 15_000)
            }
            _uiState.update { it.copy(message = null) }
        }
    }

    /**
     * 向 :bg 守护进程做一次性服务状态同步。事件镜像
     * ([com.fr.husi.bg.ServiceEventMirror]，WhileSubscribed 5s 停更）可能
     * 严重过期，而测速的 bridge/local 路由完全依赖它 —— 必须以守护进程
     * 的 lastState 为准（订阅即重放，见 coresvc/eventBroadcaster.Subscribe）。
     * 守护进程不可达（VPN 从未启动、:bg 未拉起）时返回 null，状态保持原值。
     */
    private suspend fun refreshServiceState(): ServiceEvent.State? {
        // ⚠️ 必须带超时：守护进程不可达（VPN 未运行、:bg 未拉起）时
        // gRPC 订阅流既不吐事件也不报错，裸 .first() 会无限挂起 —— 2026-10-01
        // 实测整个批量任务卡死在起点（UI 永远"进行中"且无任何日志）。
        val event = withTimeoutOrNull(SERVICE_STATE_SYNC_TIMEOUT_MS) {
            runCatching { resolveCoreClient().subscribeServiceEvents().first() }.getOrNull()
        } ?: return null
        val stateEvent = event as? ServiceEvent.State ?: return null
        // 同步回本地镜像，保持两处状态一致。
        DataStore.serviceState = stateEvent.state
        return stateEvent
    }

    /** 轮询等待服务进入 started —— 每次都直接问守护进程，不信任本地镜像。 */
    private suspend fun awaitServiceStarted(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (refreshServiceState()?.state?.started == true) return true
            delay(500)
        }
        return refreshServiceState()?.state?.started == true
    }

    private suspend fun awaitServiceStopped(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = refreshServiceState()?.state
            // 守护进程不可达 = 服务必然未运行（:bg 都没了）。
            if (state == null || !state.canStop) return
            delay(500)
        }
    }

    /**
     * 测速单个节点。任何失败都折叠为 error 字段，绝不抛出 —— 保证
     * 单节点异常不中断整个批量队列（CancellationException 除外）。
     */
    private suspend fun testProfile(
        profile: ProxyEntityParams,
        settings: SpeedTestSettings.Snapshot,
    ): SpeedTestEntity {
        val entity = SpeedTestEntity(
            proxyId = profile.id,
            groupId = profile.groupId,
            testedAt = System.currentTimeMillis(),
        )
        val cacheFiles = ArrayList<File>()
        try {
            // 中途取消立即让位。
            currentCoroutineContext().ensureActive()

            // 执行位置由 SpeedTestEngine 自动选择：服务运行中 → :bg 进程
            // (bridge, 出站可 protect)；未运行 → 本地 JNI。两种路径都是
            // 独立一次性实例，绝不触碰运行中的 sing-box 服务实例。

            val proxy = SagerDatabase.proxyDao.getById(profile.id)
                ?: return entity.copy(error = "profile deleted")

            // 复用 husi 的 forTest 配置构建（与延迟测试同源，但互不耦合：
            // 配置每次独立构建、实例完全独立）。
            val config = buildConfig(proxy, forTest = true)

            // 插件链（hysteria2/tuic 等外部插件）存在时暂时跳过：
            // JNI 直调路径不消费 PluginProcessSpec（仅 gRPC 路径生效），
            // 宁可报错也不静默测出错误速度。
            if (config.metadata.externalIndex.any { it.chain.isNotEmpty() }) {
                entity.error = "plugin chain not supported in JNI speed test yet"
                return entity
            }

            val params = SpeedTestEngine.Params(
                maxConnections = settings.maxConnections,
                downloadSeconds = settings.downloadSeconds,
                uploadSeconds = settings.uploadSeconds,
                measureUpload = settings.measureUpload,
            )

            SpeedTestEngine.run(config.configJson, params).collect { event ->
                when (event) {
                    is SpeedTestEngine.Event.OnProgress -> _uiState.update {
                        if (event.progress.phase == "download" ||
                            event.progress.phase == "upload"
                        ) {
                            it.copy(
                                liveRates = it.liveRates +
                                    (profile.id to event.progress.rateBps),
                            )
                        } else {
                            it
                        }
                    }

                    is SpeedTestEngine.Event.Finished -> {
                        entity.downloadBps = event.done.downloadBps
                        entity.uploadBps = event.done.uploadBps
                        entity.error = event.done.error
                    }
                }
            }
            currentCoroutineContext().ensureActive()
        } catch (e: PluginNotFoundException) {
            entity.error = "plugin not found: ${e.message}"
        } catch (e: CancellationException) {
            throw e // 取消必须向上传播，让 flatMapMerge 停止派发
        } catch (e: Throwable) {
            entity.error = e.readableMessage
        } finally {
            cacheFiles.forEach { runCatching { it.delete() } }
        }
        return entity
    }
}

/** 分组节点加载（从核心 DAO 只读查询，避免 UI 层直接触碰 Room 细节）。 */
internal object SpeedTestGroupLoader {
    /**
     * @return (proxyId, groupId) 列表。
     *
     * ⚠️ 顺序 = 主界面实际显示顺序（group.order 的显示比较器，见
     * proxyDisplayComparator）：用户要求的"从第一个按顺序来"指的是
     * 界面上看到的节点顺序，而不是数据库 userOrder 顺序 —— 按名称/
     * 按延迟排序后两者不同（2026-10-04 日志实测偏差）。
     */
    suspend fun load(groupId: Long): List<Pair<Long, Long>> {
        val group = SagerDatabase.groupDao.getById(groupId).firstOrNull()
        val profiles = SagerDatabase.proxyDao.getByGroup(groupId).firstOrNull().orEmpty()
        val sorted = profiles.sortedWith(
            proxyDisplayComparator(group?.order ?: GroupOrder.ORIGIN),
        )
        return sorted.map { it.id to it.groupId }
    }
}
