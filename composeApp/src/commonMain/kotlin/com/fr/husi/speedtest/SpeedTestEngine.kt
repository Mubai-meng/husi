package com.fr.husi.speedtest

import com.fr.husi.database.DataStore
import com.fr.husi.ktx.Logs
import com.fr.husi.platform.PlatformInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.koin.mp.KoinPlatform

/**
 * Go libcore 带宽测速会话的 Kotlin 封装（单节点、单会话）。
 *
 * 流量路径由 Go 侧保证：所有测量请求经一次性 sing-box 实例的回环
 * mixed 入站走被测出站，无任何系统直连连接（详见 libcore/speedtest 包注释）。
 *
 * ⚠️ 安卓 sing-box 频繁并发切换出站极易 panic 崩溃 —— 本引擎只创建
 * 独立实例，绝不触碰运行中实例，不与延迟测试（urltest/ping）共享任何
 * 状态；上层并发由 [SpeedTestManager] 限制。
 *
 * 执行位置选择（关键！）
 * ----------------------
 * VPN 服务运行在 :bg 独立进程，socket protect 依赖该进程的 vpnService。
 * 本进程（UI）没有保护 fd：VPN 运行时本地实例的出站流量会被 TUN 劫持回
 * 服务实例，被测节点连接被拒（表现为 SOCKS reply "connection refused"）。
 * 因此：
 *  - 安卓 + 服务运行中（[DataStore.serviceState.started]）→ 经 bridge
 *    派发到 :bg 的 core host 执行（ApplicationService.SpeedTestRun），
 *    实例在 :bg 进程内启动，出站被正确 protect —— 与上游
 *    StandaloneURLTest 的放置一致；
 *  - 安卓 + 服务未运行 → 本地 JNI 直跑（无 TUN 干扰，开销最小）；
 *  - 桌面 → 一律 bridge：桌面没有 TUN，一次性测速实例不需要 protect，
 *    而 desktop 的本地 runner 是 stub（必然返回"本地测速不可用"）。
 *    之前按 serviceState 分支，导致桌面未连 VPN 时测速必然失败（审查 P1-5）。
 *
 * 本地 JNI 路径经 [SpeedTestLocalRunner] 下沉到平台层：libcore.aar 只绑
 * Android，commonMain 不能直接引用 `com.fr.husi.libcore.*`。
 */
object SpeedTestEngine {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 执行位置。
     *
     * 审查 §四.1：路由决定改成**显式参数**，不再由引擎反向读
     * [DataStore.serviceState] —— 那个字段是"服务状态"这个公共事实源的
     * 离线镜像，测速模块（以及任何模块）往里写都会污染它（例如守护进程
     * 不可达时把 Idle 写进去，界面上的 VPN 开关状态会跟着变）。
     *
     * [AUTO] 仅在没有明确信息时使用（保留给未来调用方）。
     */
    enum class Route {
        AUTO,

        /** 经 bridge 到服务进程（core host）执行 —— 出站可 protect。 */
        BRIDGE,

        /** 本地 JNI 直跑 —— 只在确认没有 TUN 劫持时安全。 */
        LOCAL,
    }

    /** 单节点测速参数（与 libcore SpeedTestParams 一一对应）。 */
    class Params(
        val outboundTag: String = "",
        // 6 连接: 高带宽×高 RTT(经代理跨境)链路单/少连接受 TCP 窗口限制明显,
        // Ookla 官方客户端也是多流并发; 6 流已足够填满绝大多数链路且不易
        // 触发服务器限流。
        val maxConnections: Int = 6,
        val downloadSeconds: Int = 10,
        val uploadSeconds: Int = 10,
        val measureUpload: Boolean = false,
        val serverKeyword: String = "",
    ) {
        /**
         * @param emitDiagnosticLogs true 时 Go 侧把诊断日志（每阶段
         *   总字节/耗时/速率）额外作为 log 事件回传 —— 只有本地 JNI 路径
         *   需要：UI 进程从未 InitCore，Go 的 log.Info 只落到 logcat，
         *   日志页搜不到（审查 P1-4）。
         */
        internal fun toJson(emitDiagnosticLogs: Boolean = false): String = buildString {
            append("{")
            append("\"outbound_tag\":\"").append(outboundTag.escape()).append("\",")
            append("\"max_connections\":").append(maxConnections).append(",")
            append("\"download_seconds\":").append(downloadSeconds).append(",")
            append("\"upload_seconds\":").append(uploadSeconds).append(",")
            append("\"measure_upload\":").append(measureUpload).append(",")
            append("\"server_keyword\":\"").append(serverKeyword.escape()).append("\",")
            append("\"emit_diagnostic_logs\":").append(emitDiagnosticLogs)
            append("}")
        }

        private fun String.escape(): String =
            replace("\\", "\\\\").replace("\"", "\\\"")
    }

    /** 进度事件：phase ∈ {server, download, upload}。 */
    data class Progress(
        val phase: String,
        val rateBps: Long,
        val message: String,
    )

    /**
     * 终止事件：error 为空 = 成功；速率 Bytes/s。
     *
     * @param failureKind 失败类型（Go 侧稳定枚举）：connect / timeout /
     *   http_status / zero_bytes / discovery / config / cancelled。
     *   [SpeedTestManager] 据此决定是否自动剔除节点 —— 超时不能等同于
     *   "节点不可用"（审查 P1-6）。
     */
    data class Done(
        val downloadBps: Long,
        val uploadBps: Long,
        val serverName: String,
        val error: String?,
        val failureKind: String? = null,
        /** 实际参与计量的并发连接数（= 配置连接数 - 提前退出的 worker）。 */
        val activeConnections: Int = 0,
        /** 因连续失败提前退出的并发连接数（审查 P2-8）。 */
        val workerDropouts: Int = 0,
        /**
         * 下载阶段实收字节 / 计量时长。速率 = 字节 ÷ 时长，
         * 结果对话框据此展示"实测口径"，数值可疑时用户能自行核算。
         */
        val totalDownloadBytes: Long = 0L,
        val downloadElapsedMs: Long = 0L,
    )

    sealed class Event {
        data class OnProgress(val progress: Progress) : Event()
        data class Finished(val done: Done) : Event()

        /** 诊断日志（仅本地 JNI 路径产出），不进业务流。 */
        data class Diagnostic(val line: String) : Event()
    }

    /**
     * 运行一次带宽测速。按平台与服务状态选择执行位置（bridge / 本地）。
     * 取消 Flow 收集（或协程取消）会触发对应通道的会话取消，已测得的
     * 部分速率仍会以 Done 事件产出。Done 是最后一条，流随之关闭。
     */
    fun run(
        config: String,
        params: Params,
        route: Route = Route.AUTO,
    ): Flow<Event> {
        val viaBridge = when (route) {
            Route.BRIDGE -> true
            Route.LOCAL -> false
            // 桌面没有 TUN，一次性实例不需要 protect，本地 runner 又是
            // stub → 一律走 core host；安卓才需要看服务状态（事件镜像的
            // 离线副本，调用方已在批量开始前向守护进程同步过一次）。
            Route.AUTO -> if (PlatformInfo.isAndroid) {
                runCatching { DataStore.serviceState.started }.getOrDefault(false)
            } else {
                true
            }
        }
        Logs.d(
            "speedtest: route via " + if (viaBridge) {
                "bridge(:bg/core host, protect available)"
            } else {
                "local JNI(no protect; only safe when VPN/TUN is off)"
            },
        )
        return if (viaBridge) {
            runViaBridge(config, params)
        } else {
            runLocally(config, params)
        }
    }

    // ------------------------------------------------------------------
    // 本地 JNI 路径（安卓 + 服务未运行）
    // ------------------------------------------------------------------

    private fun runLocally(
        config: String,
        params: Params,
    ): Flow<Event> = callbackFlow {
        var handle: Int = 0
        try {
            // 诊断日志改走事件（UI 进程没有文件日志器），由下面转写进
            // 日志页，日志页可搜 "speedtest"。
            handle = SpeedTestLocalRunner.start(config, params.toJson(emitDiagnosticLogs = true)) { event ->
                when (val e = parseEvent(event)) {
                    null -> Unit
                    is Event.Diagnostic -> Logs.i("speedtest: " + e.line)
                    is Event.Finished -> {
                        trySendBlocking(e)
                        close()
                    }

                    is Event.OnProgress -> trySendBlocking(e)
                }
            }
        } catch (e: Throwable) {
            // AAR 未重绑/会话超限/平台不支持等错误统一收敛为 Done。
            trySendBlocking(
                Event.Finished(Done(0L, 0L, "", e.readableMessage())),
            )
            close()
            return@callbackFlow
        }

        if (handle <= 0) {
            trySendBlocking(
                Event.Finished(Done(0L, 0L, "", "本地测速不可用（libcore 未绑定该平台）")),
            )
            close()
            return@callbackFlow
        }

        awaitClose {
            // stop 最长阻塞 35s（等实例关闭），放到 IO 线程，
            // 不阻塞流的取消路径。
            stopScope.launch {
                runInterruptible(Dispatchers.IO) {
                    SpeedTestLocalRunner.stop(handle)
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    // ------------------------------------------------------------------
    // bridge 路径（:bg / 桌面 core host —— 出站可 protect）
    // ------------------------------------------------------------------

    private fun runViaBridge(
        config: String,
        params: Params,
    ): Flow<Event> = flow {
        val client = KoinPlatform.getKoin().getOrNull<com.fr.husi.core.CoreClient>()
        if (client == null) {
            emit(
                Event.Finished(
                    Done(0L, 0L, "", "core client 不可用，无法在服务进程内测速"),
                ),
            )
            return@flow
        }

        val request = com.fr.husi.proto.v1.speedTestRunRequest {
            this.config = config
            this.outboundTag = params.outboundTag
            this.maxConnections = params.maxConnections
            this.downloadSeconds = params.downloadSeconds
            this.uploadSeconds = params.uploadSeconds
            this.measureUpload = params.measureUpload
            this.serverKeyword = params.serverKeyword
        }

        try {
            client.speedTestRun(request).collect { protoEvent ->
                when (val e = parseEvent(protoEvent.json)) {
                    null -> return@collect
                    // bridge 路径的诊断日志已由 :bg 的 log.Info 写进同一份
                    // 日志文件，这里不再重复落盘。
                    is Event.Diagnostic -> return@collect
                    is Event.Finished -> emit(e) // 终态：流随之结束
                    is Event.OnProgress -> emit(e)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 取消向上传播（bridge 流取消 = 会话取消）
        } catch (e: Throwable) {
            emit(
                Event.Finished(
                    Done(0L, 0L, "", "服务进程测速失败: " + e.readableMessage()),
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /** SpeedTestListener JSON → Event；解析失败返回 null（忽略坏事件）。 */
    private fun parseEvent(event: String): Event? {
        val obj: JsonObject = try {
            json.parseToJsonElement(event).let { it as JsonObject }
        } catch (_: Exception) {
            return null
        }
        val phase = obj.string("phase") ?: return null
        return when (phase) {
            "done" -> Event.Finished(
                Done(
                    downloadBps = obj.long("download_bps") ?: 0L,
                    uploadBps = obj.long("upload_bps") ?: 0L,
                    serverName = obj.string("server_name").orEmpty(),
                    error = obj.string("error")?.takeIf { it.isNotEmpty() },
                    failureKind = obj.string("failure_kind")?.takeIf { it.isNotEmpty() },
                    activeConnections = obj.int("active_connections") ?: 0,
                    workerDropouts = obj.int("worker_dropouts") ?: 0,
                    totalDownloadBytes = obj.long("total_download_bytes") ?: 0L,
                    downloadElapsedMs = obj.long("download_elapsed_ms") ?: 0L,
                ),
            )

            "log" -> Event.Diagnostic(obj.string("message").orEmpty())

            else -> Event.OnProgress(
                Progress(
                    phase = phase,
                    rateBps = obj.long("rate_bps") ?: 0L,
                    message = obj.string("message").orEmpty(),
                ),
            )
        }
    }

    private val stopScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
    )

    private fun JsonObject.string(key: String): String? =
        this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }

    private fun JsonObject.long(key: String): Long? =
        this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }

    private fun Throwable.readableMessage(): String = message ?: toString()
}
