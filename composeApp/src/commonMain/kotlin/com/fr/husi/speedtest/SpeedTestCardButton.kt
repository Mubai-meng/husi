package com.fr.husi.speedtest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fr.husi.ktx.DisplayTime
import com.fr.husi.resources.Res
import com.fr.husi.resources.speed
import com.fr.husi.resources.warning
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import kotlin.time.Instant

/**
 * 节点卡片上的带宽测速按钮 + 下拉菜单（对齐 ProxyCard 既有 IconButton
 * 视觉，但完全独立成新文件，仅需要在 GroupProfilesHolder.ProxyCard 的
 * 图标行插入一次调用）。
 *
 * 菜单：测速此节点 / 测速整组 / 查看最近结果 / 清除本节点结果。
 * 按钮恒显仪表图标（实时速率由卡片状态行展示，图标不动）。
 *
 * ⚠️ 安卓 sing-box 频繁并发切换出站极易 panic 崩溃 —— 所有测速都经
 * [SpeedTestManager]（独立一次性实例 + 严格串行），本组件不含任何网络逻辑。
 */
@Composable
fun SpeedTestCardButton(
    proxyId: Long,
    groupId: Long,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val uiState by SpeedTestManager.uiState.collectAsStateWithLifecycle()
    val running = uiState.running

    Box(modifier) {
        IconButton(
            onClick = { menuOpen = !menuOpen },
            modifier = Modifier.size(40.dp),
        ) {
            // 恒显仪表图标：实时速率已在卡片状态行（"测速中 ↓x MB/s"）
            // 展示，这里不再替换成数字 —— 用户要求测速时图标保持不动。
            Icon(
                imageVector = vectorResource(Res.drawable.speed),
                contentDescription = "speed test",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            // 最近一次批量测速的错误摘要: 快速失败(如 VPN 劫持出站)时
            // 实时速率一闪而过, 用户感知为"点了没反应" —— 在菜单里
            // 直接给出可见的失败原因; 点击摘要打开完整错误对话框。
            uiState.latestError?.let { err ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "上次错误: " + err,
                            color = Color.Red,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.warning),
                            contentDescription = null,
                            tint = Color.Red,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        showResult = true
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("测速此节点") },
                onClick = {
                    menuOpen = false
                    // 审查 P2-11: 之前 startSingle 的 false 被直接丢弃 ——
                    // 有任务在跑时点"测速此节点"完全没反应。现在明确提示。
                    if (!SpeedTestManager.startSingle(
                            groupId,
                            SpeedTestManager.ProxyEntityParams(proxyId, groupId),
                        )
                    ) {
                        SpeedTestManager.notice("已有测速任务进行中，请先停止当前任务")
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(if (running) "测速整组（进行中…）" else "测速整组") },
                enabled = !running,
                onClick = {
                    menuOpen = false
                    scope.launch {
                        val profiles = SpeedTestGroupLoader.load(groupId)
                        if (profiles.isEmpty()) {
                            SpeedTestManager.notice("分组内没有可测速的节点")
                        } else if (!SpeedTestManager.startGroup(
                                groupId,
                                profiles.map {
                                    SpeedTestManager.ProxyEntityParams(it.first, it.second)
                                },
                            )
                        ) {
                            SpeedTestManager.notice("已有测速任务进行中，请先停止当前任务")
                        }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("查看最近结果") },
                onClick = {
                    menuOpen = false
                    showResult = true
                },
            )
            DropdownMenuItem(
                text = { Text("清除本节点结果") },
                onClick = {
                    menuOpen = false
                    scope.launch { SpeedTestManager.clearFor(proxyId) }
                },
            )
        }
    }

    if (showResult) {
        SpeedTestResultDialog(
            proxyId = proxyId,
            onDismiss = { showResult = false },
        )
    }
}

@Composable
private fun SpeedTestResultDialog(
    proxyId: Long,
    onDismiss: () -> Unit,
) {
    // 审查 P2-11: 之前是 remember(proxyId) { runBlocking { latestFor() } } ——
    // 主线程同步读库, 而且对话框开着时测速完成也看不到新结果(remember 只在
    // proxyId 变化时重算)。改成订阅 DAO 的 Flow: 无阻塞 + 自动刷新。
    val latest by SpeedTestManager.latestFlow(proxyId)
        .collectAsStateWithLifecycle(initialValue = null)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        },
        title = { Text("最近测速结果") },
        text = {
            Column {
                val result = latest
                if (result == null) {
                    Text("尚未测速")
                } else {
                    Text("下载：" + formatSpeed(result.downloadBps) + speedMbpsSuffix(result.downloadBps))
                    if (result.uploadBps > 0L) {
                        Text("上传：" + formatSpeed(result.uploadBps) + speedMbpsSuffix(result.uploadBps))
                    }
                    result.serverName?.let { Text("测速服务器：$it") }
                    if (result.error != null) {
                        Text(
                            text = "错误：" + result.error + failureKindSuffix(result.failureKind),
                            color = Color.Red,
                        )
                    }
                    // 实测口径自证：速率 = 实收字节 ÷ 计量时长。数值可疑时
                    // 用户能直接核对，不必再让开发者翻日志（v2 新增列）。
                    if (result.totalDownloadBytes > 0L && result.downloadElapsedMs > 0L) {
                        Text(
                            text = "实测：${formatBytes(result.totalDownloadBytes)} / " +
                                "%.1f s（跳预热）".format(result.downloadElapsedMs / 1000.0),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        "时间：" + DisplayTime.dateTime(
                            Instant.fromEpochMilliseconds(result.testedAt),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
    )
}

/**
 * Bytes/s → 人类可读。
 *
 * 审查 P2-11：旧实现用 1024 进制除数却标 `KB/s / MB/s / GB/s`（那是十进制
 * 单位，1 KB = 1000 B），显示值系统性偏小 2.4%/4.9%/7.4%。这里统一改为
 * 十进制（SI）：与运营商带宽、Ookla/Karing 的口径一致，读数可直接对比。
 */
internal fun formatSpeed(bps: Long): String = when {
    bps >= 1_000_000_000L -> "%.2f GB/s".format(bps / 1e9)
    bps >= 1_000_000L -> "%.1f MB/s".format(bps / 1e6)
    bps >= 1_000L -> "%.1f KB/s".format(bps / 1e3)
    bps > 0L -> "$bps B/s"
    else -> "--"
}

/** Bytes/s → Mbps（十进制比特率，便于和运营商/测速网站的数字对照）。 */
internal fun formatMbps(bps: Long): String =
    if (bps <= 0L) "--" else "%.1f Mbps".format(bps * 8 / 1e6)

private fun speedMbpsSuffix(bps: Long): String =
    if (bps > 0L) "（约 ${formatMbps(bps)}）" else ""

/** 字节数 → 人类可读（SI，与 [formatSpeed] 同口径）。 */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000L -> "%.1f KB".format(bytes / 1e3)
    else -> "$bytes B"
}

/**
 * 失败类型 → 中文括注。
 *
 * 只做解释，不改变判定：`timeout`（预算内没测出结果）和 `connect`
 * （节点确实连不上）是完全不同的两件事，混成一句话会让用户误删节点。
 */
private fun failureKindSuffix(kind: String?): String = when (kind) {
    "connect" -> "（无法连接节点）"
    "timeout" -> "（超时，节点未必不可用）"
    "http_status" -> "（测速服务器返回错误）"
    "zero_bytes" -> "（连上了但没收到数据）"
    "discovery" -> "（测速服务器列表获取失败）"
    "config" -> "（配置/配置解析失败）"
    "cancelled" -> "（已取消）"
    "unsupported" -> "（当前不支持）"
    else -> ""
}
