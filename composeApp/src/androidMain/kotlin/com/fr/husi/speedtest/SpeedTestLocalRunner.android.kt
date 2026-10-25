package com.fr.husi.speedtest

import com.fr.husi.libcore.Libcore
import com.fr.husi.libcore.SpeedTestListener

/**
 * Android 实现：直接调用 libcore.aar 里的 JNI。
 *
 * ⚠️ 只能在「VPN 服务未运行」时使用 —— UI 进程没有 socket protect，
 * 服务运行中出站流量会被 TUN 劫持回服务实例。ServiceState.started
 * 为 true 时 [SpeedTestEngine] 会改走 bridge 路径。
 */
internal actual object SpeedTestLocalRunner {
    actual fun start(
        config: String,
        paramsJson: String,
        onEvent: (String) -> Unit,
    ): Int {
        val listener = object : SpeedTestListener {
            override fun onEvent(event: String) {
                onEvent(event)
            }
        }
        // platformInterface 传 null：直连模式不需要平台接口，Go 侧也不会
        // 注入 auto_detect_interface（否则 sing-box 会退到 Android netlink
        // 监视器路径并直接失败）。
        return Libcore.speedTestStart(config, paramsJson, null, listener)
    }

    actual fun stop(handle: Int) {
        runCatching { Libcore.speedTestStop(handle) }
    }
}
