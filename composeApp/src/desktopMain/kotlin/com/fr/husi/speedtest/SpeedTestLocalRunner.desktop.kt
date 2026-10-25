package com.fr.husi.speedtest

/**
 * 桌面等无 libcore 绑定的平台：本地 JNI 路径不可用。
 *
 * 桌面端的核心逻辑跑在独立的 husi-core 进程里，界面通过 gRPC 控制它；
 * 带宽测速只被绑进了 Android 的 libcore.aar，因此这里直接返回失败，
 * 由 [SpeedTestEngine] 转成一条 Done 错误事件。
 */
internal actual object SpeedTestLocalRunner {
    actual fun start(
        config: String,
        paramsJson: String,
        onEvent: (String) -> Unit,
    ): Int = 0

    actual fun stop(handle: Int) {
        // 没有本地会话，无需处理。
    }
}
