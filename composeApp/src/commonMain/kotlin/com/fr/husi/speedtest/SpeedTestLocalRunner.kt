package com.fr.husi.speedtest

/**
 * 本地（本进程 JNI）测速会话的启动器。
 *
 * 为什么需要这个 expect：带宽测速的 Go 实现只被绑进 Android 的
 * `libcore.aar`，`com.fr.husi.libcore.Libcore` 在 desktop/其他平台不存在。
 * 而 [SpeedTestEngine] 位于 commonMain，必须能编译所有平台，所以把
 * 「本进程 JNI 直跑」这条路径下沉到平台 actual：
 *  - Android：真实调用 `Libcore.speedTestStart/Stop`；
 *  - Desktop 等无 libcore 的平台：直接回一条 Done 错误事件。
 *
 * 注意 bridge 路径（服务运行中经 gRPC 派发到 :bg 执行）与平台无关，
 * 仍留在 [SpeedTestEngine] 的公共代码里。
 */
internal expect object SpeedTestLocalRunner {
    /**
     * 启动一次本地测速，事件以 JSON 字符串回调（与 Go 侧
     * SpeedTestListener 的 payload 完全一致）。
     *
     * @param onEvent 每条事件回调；实现方需保证可在任意线程调用。
     * @return 会话取消句柄，供 [stop] 使用；<= 0 表示启动失败。
     */
    fun start(
        config: String,
        paramsJson: String,
        onEvent: (String) -> Unit,
    ): Int

    /** 取消会话。可能阻塞较久（等一次性实例关闭），调用方负责放到 IO 线程。 */
    fun stop(handle: Int)
}
