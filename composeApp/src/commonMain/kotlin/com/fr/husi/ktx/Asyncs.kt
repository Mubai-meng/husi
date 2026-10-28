/**
 * Manage global scope dispatcher usage here to prevents `DelicateCoroutinesApi` everywhere.
 *
 * 审查 §四.2：原来这里直接用 `GlobalScope`，协程内的异常没有任何出口 ——
 * `runOnIoDispatcher { ... }` 抛出的异常只会留一行 logcat（"Uncaught exception
 * in GlobalScope"），husi 的日志页里完全看不到，只能靠"功能没生效"倒推。
 * 现在统一到一个进程级 [AppScope]：`SupervisorJob`（一个任务失败不牵连其他
 * 任务）+ `CoroutineExceptionHandler`（异常写进 husi 日志页），调用方 API 不变。
 *
 * 仍然不建议在业务代码里新起游离协程：能结构化并发（ViewModel/界面作用域）
 * 就用结构化并发；这两个 helper 只服务于"进程级、不随界面销毁"的动作
 * （重启应用、后台清理、通知刷新）。
 */

package com.fr.husi.ktx

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val appExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Logs.e("uncaught coroutine failure", throwable)
}

/** 进程级作用域：随进程存活，不随任何界面销毁。 */
private val AppScope = CoroutineScope(SupervisorJob() + appExceptionHandler)

fun runOnDefaultDispatcher(block: suspend CoroutineScope.() -> Unit) =
    AppScope.launch(Dispatchers.Default, block = block)

fun runOnIoDispatcher(block: suspend CoroutineScope.() -> Unit) =
    AppScope.launch(Dispatchers.IO, block = block)

fun runOnMainDispatcher(block: suspend CoroutineScope.() -> Unit) =
    AppScope.launch(Dispatchers.Main.immediate, block = block)
