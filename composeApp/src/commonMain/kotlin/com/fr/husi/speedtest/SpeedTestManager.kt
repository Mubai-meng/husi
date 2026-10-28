package com.fr.husi.speedtest

import com.fr.husi.core.CoreClient
import com.fr.husi.core.ServiceEvent
import com.fr.husi.database.ProfileManager
import com.fr.husi.database.SagerDatabase
import com.fr.husi.fmt.buildConfig
import com.fr.husi.GroupOrder
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.readableMessage
import com.fr.husi.platform.PlatformInfo
import com.fr.husi.plugin.PluginNotFoundException
import com.fr.husi.ui.configuration.proxyDisplayComparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

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

    /**
     * 批次代号（受 [mutex] 保护）。收尾时只有 [generation] 仍等于自己的
     * 代号才允许复位 UI —— 否则会把新批次刚置上的 running=true 抹掉
     * （审查 P1-1 竞态 B：表现为"点了没反应 / 状态乱跳，必须重启应用"）。
     */
    private var generation = 0L

    private fun resolveCoreClient(): CoreClient = GlobalContext.get().get()

    /** 单次守护进程状态查询的上限（VPN 未运行时连接会悬挂，必须限时）。 */
    private const val SERVICE_STATE_SYNC_TIMEOUT_MS = 3_000L

    /**
     * 批量测速单节点硬预算。
     *
     * 用户最初要求 5 秒；2026-10-26 审查后提到 8 秒：5s 减去"实例启动 +
     * 连接建立"后只剩 4s，其中还要丢掉预热期，真正参与计算的稳态窗口约
     * 3s —— 批量结果普遍比单节点低 2~3 倍，冷缓存时的首个节点还常常先撞
     * Kotlin 兜底超时被判"测速超时"（审查 P0-1）。
     *
     * 单节点入口（节点卡片"测速此节点"）不套此预算，按设置跑满时长。
     */
    const val NODE_TIMEOUT_MS = 8_000L

    /**
     * 每节点预算中预留给"一次性 sing-box 实例启动 + TLS/HTTP 连接建立"
     * 的部分。审查实测冷缓存开销常态 2~4s，1s 的假设与代码自己的注释
     * （"慢节点上 TLS+HTTP 往返要 1~2s"）直接矛盾。
     */
    private const val NODE_STARTUP_OVERHEAD_MS = 2_000L

    /**
     * Kotlin 侧兜底超时在 Go 预算之上额外放宽的部分。正常情况 Go 会话
     * 会在预算内自然收尾并回传完整部分结果，兜底只拦截真正挂死的异常。
     *
     * 放宽 5s 而不是 3s：批量首个节点若服务器列表未命中缓存，还要额外付
     * 一次"拉列表 + 探测"（约 1~3s），这段不能算到节点头上。
     */
    private const val NODE_GUARD_EXTRA_MS = 5_000L

    /** 单节点下载阶段的下限（再短就连预热都扣不出稳态窗口）。 */
    private const val MIN_DOWNLOAD_SECONDS = 3

    /** 超时切下一个节点前的缓冲：Go 侧配额在 done 事件后即释放，
     * 只需给实例关闭留一点点时间。 */
    private const val NODE_TIMEOUT_COOLDOWN_MS = 200L

    /** UI 状态（对齐延迟测试的 ConfigurationTestUiState 语义，但独立持有）。 */
    data class UiState(
        val running: Boolean = false,
        val total: Int = 0,
        val processed: Int = 0,
        /**
         * 本批次涉及的 groupId 集合。
         *
         * 横幅挂在每个分组的列表页上（HorizontalPager 的每一页各有一份
         * GroupHolderScreen），只有被测分组才该显示进度 —— 否则用户滑到
         * 别的分组也会看到同一个"带宽测速 3/37"，误以为是那一组的进度。
         */
        val groupIds: Set<Long> = emptySet(),
        /** 正在测速的节点 → 当前实时速率（Bytes/s）。 */
        val liveRates: Map<Long, Long> = emptyMap(),
        val latestError: String? = null,
        /** 进行中的辅助状态文案（如"正在启动网络隧道…"），进度条区域显示。 */
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 一次性提示（UI 收集后弹 snackbar）。
     *
     * 审查 P2-11：像"已有测速任务进行中"这类即时拒绝，之前 `startGroup`
     * 返回 false 后调用方直接丢弃，用户点了完全没反应（卡片菜单/多选对话框
     * 两条路径都如此，只有连接测试菜单那条有 snackbar）。
     */
    private val _notices = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /** 发一条一次性提示（非挂起、不阻塞调用方，通常是 UI 线程）。 */
    fun notice(message: String) {
        _notices.tryEmit(message)
    }

    /**
     * 当前已加载分组的节点测速结果缓存（proxyId → 最新一条）。
     * 节点卡片状态行展示"测速结果在延迟前"的数据源；
     * 由 [loadGroupResults] 按组加载，测速落库/清除时同步更新。
     */
    private val _groupResults = MutableStateFlow<Map<Long, SpeedTestEntity>>(emptyMap())
    val groupResults: StateFlow<Map<Long, SpeedTestEntity>> = _groupResults.asStateFlow()

    /** 加载某分组的节点最新测速结果（卡片展示前调用）。 */
    suspend fun loadGroupResults(groupId: Long) {
        // DAO 已在 SQL 侧按节点归并（审查 P2-11），这里只需建索引。
        _groupResults.value = SpeedTestDatabase.dao.latestForGroup(groupId)
            .associateBy { it.proxyId }
    }

    fun isRunning(): Boolean = _uiState.value.running

    /**
     * 批量测速一个分组。已有任务运行中则忽略（返回 false）。
     * 结果逐条写入 [SpeedTestDatabase]；确定性失败的节点可按
     * [SpeedTestSettings.autoRemove] 自动剔除。
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
                val state = _uiState.value
                // running 由本函数同步置位，所以 running=true 一定是
                // "正在跑"的合法任务 → 维持拒绝语义。
                if (state.running) return false
                // 停止服务期间（message 非空）也维持拒绝。
                if (state.message != null) return false
                // 走到这里：running/message 均已复位但协程仍未退出
                // （收尾阶段卡住）→ 强制让位，避免"点测速没反应，必须
                // 重启应用才能恢复"（2026-10 用户实测反馈）。
                current.cancel()
            }
            // ⚠️ 同步置位（审查 P1-1 竞态 A）：必须在 launch 之前、
            // 且在锁内完成。之前 running=true 是在协程内、且在一个挂起点
            // （设置快照）之后才设置的，于是"刚启动"的合法任务与"收尾
            // 卡住"的僵尸任务在状态上无法区分 —— 用户连点（卡片菜单 +
            // 整组同时触发）时第二个请求会把刚启动的整组任务当僵尸
            // cancel 掉，整组静默截断。
            generation += 1
            val gen = generation
            _uiState.value = UiState(
                running = true,
                total = profiles.size,
                // 只让被测分组自己的横幅亮起（多选跨组时可能有多个）。
                groupIds = profiles.mapTo(mutableSetOf()) { it.groupId },
            )
            job = scope.launch {
                runBatch(gen, groupId, profiles, perNodeTimeoutMs)
            }
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

    /** 节点最近一次结果的响应式流（卡片/结果对话框订阅，无需 runBlocking）。 */
    fun latestFlow(proxyId: Long): Flow<SpeedTestEntity?> =
        SpeedTestDatabase.dao.latestFlow(proxyId).map { it.firstOrNull() }

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

    /**
     * 单节点结果 + 失败类型。
     *
     * 失败类型随结果一起落库（v2 表新增 `failureKind` 列）：它既决定
     * "能否自动剔除"（审查 P1-6），也决定结果对话框怎么解释这次失败
     * （超时 ≠ 节点不可用）。此前只在内存里传递，批量结束就丢了。
     */
    private class NodeOutcome(
        val entity: SpeedTestEntity,
        /** 成功但并发连接提前退出（结果偏低，不是失败）。审查 P2-8。 */
        val degraded: Boolean = false,
    ) {
        val failureKind: String? get() = entity.failureKind

        /** 只有"节点确定连不上"才算确定性故障；超时/取消/配置类都不算。 */
        val isDeterministicFailure: Boolean
            get() = failureKind == FAILURE_CONNECT
    }

    private suspend fun runBatch(
        gen: Long,
        groupId: Long,
        profiles: List<ProxyEntityParams>,
        perNodeTimeoutMs: Long?,
    ) {
        val outcomes = mutableListOf<NodeOutcome>()
        var settings: SpeedTestSettings.Snapshot? = null
        // ⚠️ 整个批次必须在 try/finally 内：running=true 已在 startGroup
        // 里置位，任何退出路径（取消、异常、提前 return）都必须复位，
        // 否则 UI 永远显示"测速进行中"，后续所有测速入口全部无响应，
        // 只能重启应用（2026-10 用户实测反馈的根因）。
        try {
            settings = SpeedTestSettings.snapshot()
            runBatchBody(groupId, profiles, perNodeTimeoutMs, settings, outcomes)
        } catch (e: CancellationException) {
            // 用户停止 / 被新批次抢占：已完成的结果已逐条落库，直接收尾。
            throw e
        } catch (e: Throwable) {
            _uiState.update { it.copy(latestError = e.readableMessage) }
        } finally {
            // NonCancellable：协程被取消时 finally 里任何挂起点都会立刻
            // 抛 CancellationException —— 那样 UI 复位就永远跑不到，
            // running 卡死为 true。收尾必须无条件执行。
            withContext(NonCancellable) {
                finishBatch(gen, settings, outcomes)
            }
        }
    }

    private suspend fun runBatchBody(
        groupId: Long,
        profiles: List<ProxyEntityParams>,
        perNodeTimeoutMs: Long?,
        settings: SpeedTestSettings.Snapshot,
        outcomes: MutableList<NodeOutcome>,
    ) {
        // ---- 执行位置路由（不改变 VPN 状态，用户 2026-10-08 要求）----
        //
        // 服务运行中 → bridge 到 :bg（出站可 protect，防 TUN 劫持）；
        // 服务未运行 → 本地 JNI 直跑：此时本机没有 TUN，测速实例
        // 直连节点服务器即可，被墙节点会如实失败（直连物理不可达），
        // 不再为测速强制开启/关闭 VPN 通道（旧逻辑 2026-10-04 前）。
        //
        // ⚠️ DataStore.serviceState 是事件镜像（WhileSubscribed 5s 停更）
        // 的离线副本，可能严重过期 —— 过期会导致"VPN 实际在跑却误走本地
        // JNI（无 protect）"，测速出站被本机 TUN 全量劫持：测速变成双重
        // 代理、流量全部计入被测节点统计（2026-10-01 三轮日志实测）。因此
        // 每次批量前先向 :bg 守护进程做一次性状态同步（订阅即重放 lastState）。
        //
        // ⚠️ 审查 §四.1：这里**只读**服务状态，绝不回写 DataStore ——
        // 那是跨模块的公共事实源（界面上的 VPN 开关也读它），测速模块往
        // 里写会污染语义（尤其"守护进程不可达就强写 Idle"这一条，会让
        // 界面在 VPN 正常运行时误显示未连接）。路由改为显式传给引擎。
        val synced = refreshServiceState()
        val serviceStarted = synced?.state?.started == true
        val route = if (!PlatformInfo.isAndroid || serviceStarted) {
            // 桌面没有 TUN → 一律 core host（本地 runner 是 stub）；
            // 安卓 + 服务在跑 → :bg 内执行，出站可 protect。
            SpeedTestEngine.Route.BRIDGE
        } else {
            // 安卓 + 服务未运行（含守护进程不可达）= 本机没有 TUN → 本地直跑。
            SpeedTestEngine.Route.LOCAL
        }
        // 桌面不该出现"直连模式"提示（审查 P1-5）。
        if (PlatformInfo.isAndroid && !serviceStarted) {
            _uiState.update {
                it.copy(message = "未连接 VPN：直连模式测速（被墙节点可能失败）")
            }
        }

        // ---- 顺序批量：严格从第一个节点按列表顺序逐个测（用户要求）。
        // 顺序执行同时天然规避了 sing-box 并发出站切换的 panic 风险
        // （⚠️ 安卓 sing-box 频繁并发切换出站极易 panic —— 见类注释）。
        //
        // ⚠️ 预算下移为 Go 参数（2026-10-04 日志教训）：之前把 5s 硬
        // 超时放在 Kotlin collect 上，Go 侧 downloadSeconds=15 还在跑，
        // 5s 到点直接掐断 collect → Go 测得的部分速率（日志实测 41.72
        // Mbps）根本回不来，UI 全部显示"测速超时"。现在改为：预算减去
        // 启动开销后作为 Go 的 downloadSeconds —— Go 到点自然收尾并
        // 回传完整部分结果；Kotlin 超时只作为兜底（预算+5s），拦真正
        // 挂死的异常。
        //
        // 批量预算全给下载（上传不吃批量预算）。
        val batchDownloadSeconds = perNodeTimeoutMs?.let { budget ->
            ((budget - NODE_STARTUP_OVERHEAD_MS) / 1000).toInt()
                .coerceIn(MIN_DOWNLOAD_SECONDS, settings.downloadSeconds)
        }
        val guardTimeoutMs = perNodeTimeoutMs?.plus(NODE_GUARD_EXTRA_MS)
        for (profile in profiles) {
            currentCoroutineContext().ensureActive()
            val outcome = withContext(Dispatchers.IO) {
                if (guardTimeoutMs == null) {
                    testProfile(profile, settings, null, route)
                } else {
                    withTimeoutOrNull(guardTimeoutMs) {
                        testProfile(profile, settings, batchDownloadSeconds, route)
                    } ?: NodeOutcome(
                        SpeedTestEntity(
                            proxyId = profile.id,
                            groupId = profile.groupId,
                            testedAt = System.currentTimeMillis(),
                            // 超时 ≠ 节点不可用：绝不允许被自动剔除（审查 P1-6）。
                            failureKind = FAILURE_TIMEOUT,
                        ).copy(error = "测速超时（单节点 ${perNodeTimeoutMs / 1000} 秒上限）"),
                    )
                }
            }
            outcomes.add(outcome)
            _uiState.update {
                it.copy(
                    processed = it.processed + 1,
                    liveRates = it.liveRates - outcome.entity.proxyId,
                )
            }
            // 每条结果立即落库，中途取消也不丢已完成部分。
            SpeedTestDatabase.dao.insert(outcome.entity)
            // 同步刷新卡片展示缓存（测速结果在延迟前显示）。
            _groupResults.update { it + (outcome.entity.proxyId to outcome.entity) }
            if (outcome.failureKind == FAILURE_TIMEOUT) {
                // 超时切下一个节点前给旧实例关闭留一点点缓冲
                // （Go 侧配额在 done 事件后即释放，不需要长等）。
                delay(NODE_TIMEOUT_COOLDOWN_MS)
            }
        }
    }

    /**
     * 批次收尾：复位 UI + 历史裁剪 + 失败自动剔除。
     *
     * ⚠️ UI 复位必须先做, 且必须带代号校验（审查 P1-1 竞态 B）：被抢占的
     * 旧批次如果在自己的 finally 里无条件 running=false，会把新批次刚置上
     * 的 running=true 抹掉 —— UI 显示"未在测速"、进度条消失、按钮重新可点，
     * 用户再点一次又能插进来，表现为"状态乱跳"。
     */
    private suspend fun finishBatch(
        gen: Long,
        settings: SpeedTestSettings.Snapshot?,
        outcomes: List<NodeOutcome>,
    ) {
        val owned = mutex.withLock {
            if (generation != gen) return@withLock false
            val lastError = outcomes.lastOrNull { it.entity.error != null }?.entity?.error
            _uiState.value = _uiState.value.copy(
                running = false,
                liveRates = emptyMap(),
                latestError = lastError,
                message = null,
                groupIds = emptySet(),
            )
            true
        }
        // 已被新批次接管：收尾工作交给它，别在这里动共享状态。
        if (!owned || settings == null) return

        // 历史裁剪（按设置保留每节点最近 N 条）。
        runCatching { SpeedTestDatabase.dao.prune(settings.historyKeep) }

        // 连接减员提示（审查 P2-8）：这些节点"成功"了但实际并发不足,
        // 数值偏低; 逐节点提示会刷屏, 这里汇总成一条一次性提示。
        val degraded = outcomes.count { it.degraded }
        if (degraded > 0) {
            notice("$degraded 个节点的并发连接提前退出，测速结果可能偏低")
        }

        // 失败自动剔除（可选，对齐 Karing testLatencyAutoRemove）。
        // ⚠️ 只剔"确定性失败"（连接被拒/不可达/DNS/TLS），审查 P1-6：
        // 之前把超时、取消、插件不支持一视同仁地 deleteById —— 不可逆、
        // 无撤销、而且超时往往只是预算不够（旧预算 5s 的必然结果）。
        if (settings.autoRemove) {
            val removable = outcomes.filter { it.isDeterministicFailure }
            val skipped = outcomes.count { it.entity.error != null } - removable.size
            if (skipped > 0) {
                Logs.w("speedtest: autoRemove 跳过 $skipped 个非确定性失败节点（超时/取消/配置类不删）")
            }
            if (removable.isNotEmpty()) {
                // ⚠️ 必须走 ProfileManager.deleteProfiles 而不是裸 DAO：
                // 直接 deleteById 会漏掉两件收尾 ——
                //  1. 被删的正好是当前选中节点时，selectedProxy 仍指向已
                //     消失的 id，界面选中态错乱（下次启动指向不存在的节点）；
                //  2. 不调 GroupManager.rearrange，userOrder 留空洞，
                //     卡片顺序与拖拽排序后续会错位。
                // 按 groupId 聚合，每组只做一次 count+rearrange。
                for ((groupId, ids) in removable.groupBy(
                    { it.entity.groupId },
                    { it.entity.proxyId },
                )) {
                    runCatching { ProfileManager.deleteProfiles(groupId, ids) }
                        .onFailure { Logs.w("speedtest: autoRemove 删除失败 group=$groupId", it) }
                }
                Logs.w("speedtest: autoRemove 已删除 ${removable.size} 个连接不可达的节点")
            }
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
        // ⚠️ 只返回, 不回写 DataStore.serviceState(审查 §四.1: 那是跨模块
        // 公共事实源, 由服务事件的镜像逻辑独占维护)。
        return event as? ServiceEvent.State
    }

    /**
     * 测速单个节点。任何失败都折叠为 error 字段，绝不抛出 —— 保证
     * 单节点异常不中断整个批量队列（CancellationException 除外）。
     *
     * @param batchDownloadSeconds 批量模式下的下载阶段时长（Go 侧到时
     *   自然收尾并回传部分结果）；null = 单节点入口，按设置跑满。
     */
    private suspend fun testProfile(
        profile: ProxyEntityParams,
        settings: SpeedTestSettings.Snapshot,
        batchDownloadSeconds: Int?,
        route: SpeedTestEngine.Route,
    ): NodeOutcome {
        val entity = SpeedTestEntity(
            proxyId = profile.id,
            groupId = profile.groupId,
            testedAt = System.currentTimeMillis(),
        )
        var degraded = false
        try {
            // 中途取消立即让位。
            currentCoroutineContext().ensureActive()

            // 执行位置由 SpeedTestEngine 自动选择：服务运行中 → :bg 进程
            // (bridge, 出站可 protect)；未运行 → 本地 JNI；桌面 → core host。
            // 两种路径都是独立一次性实例，绝不触碰运行中的服务实例。

            val proxy = SagerDatabase.proxyDao.getById(profile.id)
                ?: return NodeOutcome(
                    entity.copy(error = "profile deleted", failureKind = FAILURE_CONFIG),
                )

            // 复用 husi 的 forTest 配置构建（与延迟测试同源，但互不耦合：
            // 配置每次独立构建、实例完全独立）。
            val config = buildConfig(proxy, forTest = true)

            // 插件链（hysteria2/tuic 等外部插件）存在时暂时跳过：
            // bridge 与 JNI 两条路径都起不了插件进程（SpeedTestRunRequest
            // 根本没有 plugins 字段），宁可报错也不静默测出错误速度。
            // 文案不再说"JNI 不支持" —— 那是事实错误（审查 §四.5）。
            if (config.metadata.externalIndex.any { it.chain.isNotEmpty() }) {
                entity.error = "该节点需要外部插件进程，暂不支持带宽测速"
                entity.failureKind = FAILURE_UNSUPPORTED
                return NodeOutcome(entity)
            }

            val params = SpeedTestEngine.Params(
                // ⚠️ 必须透传根出站 tag（审查 P1-2）：丢弃 mainTag 会让 Go
                // 侧回退到 firstTestableOutbound（跳过 urltest/selector），
                // ProxySet/分组型或自定义 config 型 profile 就会测到
                // "第一个成员出站"而不是该 profile 的根 —— 用户看到的是
                // 别的节点的速度。
                outboundTag = config.metadata.mainTag,
                maxConnections = settings.maxConnections,
                downloadSeconds = batchDownloadSeconds ?: settings.downloadSeconds,
                uploadSeconds = settings.uploadSeconds,
                // 批量预算全给下载：8s 内再塞上传只会两个方向都测不准。
                measureUpload = if (batchDownloadSeconds != null) {
                    false
                } else {
                    settings.measureUpload
                },
            )

            SpeedTestEngine.run(config.configJson, params, route).collect { event ->
                when (event) {
                    is SpeedTestEngine.Event.OnProgress -> _uiState.update {
                        when {
                            event.progress.phase == "download" ||
                                event.progress.phase == "upload" ->
                                it.copy(
                                    liveRates = it.liveRates +
                                        (profile.id to event.progress.rateBps),
                                )
                            // 展示本次实际使用的测速服务器（审查 P2-4 建议）：
                            // speedtest.net 的节点是自动挑的，用户看到数值异常
                            // 时第一反应是"走的哪台服务器"，不显示就只能翻日志。
                            event.progress.phase == "server" &&
                                event.progress.message.isNotEmpty() ->
                                it.copy(message = "测速服务器：" + event.progress.message)
                            else -> it
                        }
                    }

                    is SpeedTestEngine.Event.Finished -> {
                        entity.downloadBps = event.done.downloadBps
                        entity.uploadBps = event.done.uploadBps
                        entity.error = event.done.error
                        entity.failureKind = event.done.failureKind?.takeIf { it.isNotEmpty() }
                        // 结果自证数据（v2 列）：实收字节 / 计量耗时 / 服务器名。
                        entity.serverName = event.done.serverName.takeIf { it.isNotEmpty() }
                        entity.totalDownloadBytes = event.done.totalDownloadBytes
                        entity.downloadElapsedMs = event.done.downloadElapsedMs
                        // 连接减员（审查 P2-8）：结果"成功"但并发连接提前
                        // 退出 → 速率偏低。逐节点提示会刷屏，批量收尾时统一
                        // 报一次（见 finishBatch）。
                        degraded = event.done.error == null && event.done.workerDropouts > 0
                    }

                    // 诊断日志事件由引擎自己写进日志页，这里无需处理。
                    is SpeedTestEngine.Event.Diagnostic -> Unit
                }
            }
            currentCoroutineContext().ensureActive()
        } catch (e: PluginNotFoundException) {
            entity.error = "plugin not found: ${e.message}"
            entity.failureKind = FAILURE_UNSUPPORTED
        } catch (e: CancellationException) {
            throw e // 取消必须向上传播，让批量循环停止派发
        } catch (e: Throwable) {
            entity.error = e.readableMessage
            entity.failureKind = FAILURE_CONFIG
        }
        if (entity.error == null) {
            entity.failureKind = null
        } else {
            degraded = false
        }
        return NodeOutcome(entity, degraded)
    }

    // 失败类型（与 Go 侧 speedtest.Failure* 常量一一对应）。
    private const val FAILURE_CONNECT = "connect"
    private const val FAILURE_TIMEOUT = "timeout"
    private const val FAILURE_CONFIG = "config"
    private const val FAILURE_UNSUPPORTED = "unsupported"
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
