package com.fr.husi.speedtest

import com.fr.husi.database.DataStore
import com.fr.husi.libcore.Libcore
import com.fr.husi.libcore.SpeedTestListener
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
 *  - 服务运行中（[BackendState.ServiceState.started]）→ 经 gRPC bridge
 *    派发到 :bg 的 core host 执行（ApplicationService.SpeedTestRun），
 *    实例在 :bg 进程内启动，出站被正确 protect —— 与上游
 *    StandaloneURLTest 的放置一致；
 *  - 服务未运行 → 本地 JNI 直跑（无 TUN 干扰，开销最小）。
 */
object SpeedTestEngine {

    private val json = Json { ignoreUnknownKeys = true }

    /** 单节点测速参数（与 libcore SpeedTestParams 一一对应）。 */
    class Params(
        val outboundTag: String = "",
        val maxConnections: Int = 3,
        val downloadSeconds: Int = 10,
        val uploadSeconds: Int = 10,
        val measureUpload: Boolean = false,
        val serverKeyword: String = "",
    ) {
        internal fun toJson(): String = buildString {
            append("{")
            append("\"outbound_tag\":\"").append(outboundTag.escape()).append("\",")
            append("\"max_connections\":").append(maxConnections).append(",")
            append("\"download_seconds\":").append(downloadSeconds).append(",")
            append("\"upload_seconds\":").append(uploadSeconds).append(",")
            append("\"measure_upload\":").append(measureUpload).append(",")
            append("\"server_keyword\":\"").append(serverKeyword.escape()).append("\"")
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

    /** 终止事件：error 为空 = 成功；速率 Bytes/s。 */
    data class Done(
        val downloadBps: Long,
        val uploadBps: Long,
        val serverName: String,
        val error: String?,
    )

    sealed class Event {
        data class OnProgress(val progress: Progress) : Event()
        data class Finished(val done: Done) : Event()
    }

    /**
     * 运行一次带宽测速。按服务状态自动选择执行位置（bridge / 本地）。
     * 取消 Flow 收集（或协程取消）会触发对应通道的会话取消，已测得的
     * 部分速率仍会以 Done 事件产出。Done 是最后一条，流随之关闭。
     */
    fun run(
        config: String,
        params: Params,
    ): Flow<Event> {
        val viaBridge = try {
            DataStore.serviceState.started
        } catch (_: Exception) {
            false
        }
        com.fr.husi.ktx.Logs.d(
            "speedtest: route via " + if (viaBridge) {
                "bridge(:bg, protect available)"
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
    // 本地 JNI 路径（服务未运行时）
    // ------------------------------------------------------------------

    private fun runLocally(
        config: String,
        params: Params,
    ): Flow<Event> = callbackFlow {
        var handle: Int = 0
        val listener = object : SpeedTestListener {
            override fun onEvent(event: String) {
                when (val e = parseEvent(event)) {
                    null -> return
                    is Event.Finished -> {
                        trySendBlocking(e)
                        close()
                    }

                    is Event.OnProgress -> trySendBlocking(e)
                }
            }
        }

        try {
            handle = Libcore.speedTestStart(config, params.toJson(), null, listener)
        } catch (e: Throwable) {
            trySendBlocking(
                // AAR 未重绑/会话超限等错误统一收敛为 Done。
                Event.Finished(Done(0L, 0L, "", e.readableMessage())),
            )
            close()
            return@callbackFlow
        }

        awaitClose {
            if (handle > 0) {
                // speedTestStop 最长阻塞 35s（等实例关闭），放到 IO 线程，
                // 不阻塞流的取消路径。
                stopScope.launch {
                    runInterruptible(Dispatchers.IO) {
                        runCatching { Libcore.speedTestStop(handle) }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    // ------------------------------------------------------------------
    // bridge 路径（服务运行中，:bg 进程执行 —— 出站可 protect）
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
                    is Event.Finished -> emit(e) // 终态：流随之结束
                    is Event.OnProgress -> emit(e)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 取消向上传播（bridge 流取消 = :bg 会话取消）
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
                ),
            )

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

    private fun Throwable.readableMessage(): String = message ?: toString()
}
