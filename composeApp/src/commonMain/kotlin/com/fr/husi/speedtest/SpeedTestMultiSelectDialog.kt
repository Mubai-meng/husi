package com.fr.husi.speedtest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fr.husi.database.SagerDatabase
import kotlinx.coroutines.flow.firstOrNull

/**
 * 多选节点批量测速对话框 —— "连接测试"菜单入口。
 *
 * 从当前分组加载全部节点（只读查询 SagerDatabase），用户勾选后
 * 一次性提交给 [SpeedTestManager.startGroup]（队列式批量测速）。
 *
 * ⚠️ 安卓 sing-box 频繁并发切换出站极易 panic 崩溃 —— 本对话框
 * 不含任何网络逻辑；勾选多少节点都由 SpeedTestManager 的并发
 * 钳制（≤2）+ Go 侧 4 会话硬上限兜底，逐个用独立一次性实例测速。
 */
@Composable
fun SpeedTestMultiSelectDialog(
    groupId: Long,
    onDismiss: () -> Unit,
) {
    /** null = 加载中。 */
    var profiles by remember(groupId) {
        mutableStateOf<List<Pair<Long, String>>?>(null)
    }
    val checked = remember(groupId) { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(groupId) {
        profiles = SagerDatabase.proxyDao.getByGroup(groupId)
            .firstOrNull()
            .orEmpty()
            .map { it.id to it.requireBean().displayName() }
    }

    val list = profiles
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !checked.value.isEmpty() && SpeedTestManager.isRunning().not(),
                onClick = {
                    val idToGroup = list
                        ?.filter { it.first in checked.value }
                        ?.map { SpeedTestManager.ProxyEntityParams(it.first, groupId) }
                        .orEmpty()
                    SpeedTestManager.startGroup(groupId, idToGroup)
                    onDismiss()
                },
            ) {
                Text(
                    if (SpeedTestManager.isRunning()) {
                        "测速进行中…"
                    } else {
                        "开始测速(" + checked.value.size + ")"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("选择要测速的节点") },
        text = {
            when (list) {
                null -> Text("加载中…")
                else -> Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checked.value = if (checked.value.size == list.size) {
                                    emptySet()
                                } else {
                                    list.map { it.first }.toSet()
                                }
                            },
                    ) {
                        Checkbox(
                            checked = checked.value.size == list.size && list.isNotEmpty(),
                            onCheckedChange = null,
                        )
                        Text(
                            if (checked.value.size == list.size) "取消全选"
                            else "全选(" + list.size + ")",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .fillMaxWidth(),
                    ) {
                        items(list, key = { it.first }) { (id, name) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checked.value = if (id in checked.value) {
                                            checked.value - id
                                        } else {
                                            checked.value + id
                                        }
                                    }
                                    .padding(horizontal = 4.dp),
                            ) {
                                Checkbox(
                                    checked = id in checked.value,
                                    onCheckedChange = null,
                                )
                                Text(
                                    name.ifBlank { "(unnamed)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
