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
import com.fr.husi.resources.Res
import com.fr.husi.resources.speed
import com.fr.husi.resources.warning
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource

/**
 * 节点卡片上的带宽测速按钮 + 下拉菜单（对齐 ProxyCard 既有 IconButton
 * 视觉，但完全独立成新文件，仅需要在 GroupProfilesHolder.ProxyCard 的
 * 图标行插入一次调用，见 README-SPEEDTEST-KOTLIN.md 的接入补丁）。
 *
 * 菜单：测速此节点 / 测速整组 / 查看最近结果 / 清除本节点结果。
 * 按钮恒显仪表图标（实时速率由卡片状态行展示，图标不动）。
 *
 * ⚠️ 安卓 sing-box 频繁并发切换出站极易 panic 崩溃 —— 所有测速都经
 * [SpeedTestManager]（独立实例 + 并发 ≤2），本组件不含任何网络逻辑。
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
                    SpeedTestManager.startSingle(
                        groupId,
                        SpeedTestManager.ProxyEntityParams(proxyId, groupId),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(if (running) "测速整组（进行中…）" else "测速整组") },
                enabled = !running,
                onClick = {
                    menuOpen = false
                    scope.launch {
                        val profiles = SpeedTestGroupLoader.load(groupId)
                        SpeedTestManager.startGroup(
                            groupId,
                            profiles.map {
                                SpeedTestManager.ProxyEntityParams(it.first, it.second)
                            },
                        )
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
    val latest = remember(proxyId) {
        kotlinx.coroutines.runBlocking { SpeedTestManager.latestFor(proxyId) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        },
        title = { Text("最近测速结果") },
        text = {
            Column {
                if (latest == null) {
                    Text("尚未测速")
                } else {
                    Text("下载：" + formatSpeed(latest.downloadBps))
                    if (latest.uploadBps > 0L) {
                        Text("上传：" + formatSpeed(latest.uploadBps))
                    }
                    if (latest.error != null) {
                        Text("错误：" + latest.error, color = Color.Red)
                    }
                    Text(
                        "时间：" + java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            java.util.Locale.getDefault(),
                        ).format(java.util.Date(latest.testedAt)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
    )
}

/** Bytes/s → 人类可读(单位阶梯必须与除数一致: KiB/MiB/GiB)。 */
internal fun formatSpeed(bps: Long): String = when {
    bps >= 1L shl 30 -> "%.1f GB/s".format(bps / 1073741824.0)
    bps >= 1L shl 20 -> "%.1f MB/s".format(bps / 1048576.0)
    bps >= 1L shl 10 -> "%.1f KB/s".format(bps / 1024.0)
    bps > 0L -> "$bps B/s"
    else -> "--"
}
