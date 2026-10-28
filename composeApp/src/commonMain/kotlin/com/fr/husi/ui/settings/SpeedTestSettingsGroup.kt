package com.fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.SliderPreference
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.material3.Text
import com.fr.husi.resources.Res
import com.fr.husi.resources.delete_forever
import com.fr.husi.resources.download
import com.fr.husi.resources.fast_forward
import com.fr.husi.resources.file_upload
import com.fr.husi.resources.speed
import com.fr.husi.resources.speed_test_auto_remove
import com.fr.husi.resources.speed_test_auto_remove_sum
import com.fr.husi.resources.speed_test_connections
import com.fr.husi.resources.speed_test_connections_sum
import com.fr.husi.resources.speed_test_download_seconds
import com.fr.husi.resources.speed_test_download_seconds_sum
import com.fr.husi.resources.speed_test_history_keep
import com.fr.husi.resources.speed_test_history_keep_sum
import com.fr.husi.resources.speed_test_measure_upload
import com.fr.husi.resources.speed_test_measure_upload_sum
import com.fr.husi.resources.speed_test_unit_records
import com.fr.husi.resources.speed_test_unit_seconds
import com.fr.husi.resources.speed_test_upload_seconds
import com.fr.husi.resources.speed_test_upload_seconds_sum
import com.fr.husi.resources.timer
import com.fr.husi.speedtest.SpeedTestSettings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 带宽测速设置页（设置 → 带宽测速）。
 *
 * 审查 P2-10 收尾：`SpeedTestSettings` 的 6 个可调项此前**没有任何 UI
 * 入口**，用户只能吃默认值。这里全部接上，且只读写测速专属 DataStore
 * 文件（speedtest.preferences_pb），不触碰核心配置，改完也不需要
 * reload —— 下一轮测速开始时按快照生效。
 *
 * ⚠️ 这些值与延迟测试（connectionTestURL/Timeout/Concurrent）毫无关系：
 * 带宽测速不走 URL 延迟测试，服务器由 Go 侧自研发现逻辑挑选。
 */
@Composable
internal fun SpeedTestSettingsGroup() {
    val scope = rememberCoroutineScope()

    val maxConnections by SpeedTestSettings.maxConnections.collectAsStateWithLifecycle(1)
    var connectionsPreview by remember { mutableFloatStateOf(maxConnections.toFloat()) }
    // 落盘值变化（含首次从 DataStore 读到真值）时同步预览态：
    // remember 初值取的是 collectAsStateWithLifecycle 的默认值，不同步的话
    // 滑块会一直停在默认值上，和真实设置不一致。
    LaunchedEffect(maxConnections) { connectionsPreview = maxConnections.toFloat() }
    SliderPreference(
        value = maxConnections.toFloat(),
        onValueChange = { value ->
            scope.launch { SpeedTestSettings.setMaxConnections(value.toInt()) }
        },
        sliderValue = connectionsPreview,
        onSliderValueChange = { connectionsPreview = it },
        title = { Text(stringResource(Res.string.speed_test_connections)) },
        summary = { Text(stringResource(Res.string.speed_test_connections_sum)) },
        valueRange = 1f..16f,
        valueSteps = 16,
        icon = { MaskedIcon(Res.drawable.fast_forward, IconMaskColors.IconLightGreen) },
        valueText = { Text(connectionsPreview.toInt().toString()) },
    )

    val downloadSeconds by SpeedTestSettings.downloadSeconds.collectAsStateWithLifecycle(15)
    var downloadPreview by remember { mutableFloatStateOf(downloadSeconds.toFloat()) }
    // 落盘值变化（含首次从 DataStore 读到真值）时同步预览态：
    // remember 初值取的是 collectAsStateWithLifecycle 的默认值，不同步的话
    // 滑块会一直停在默认值上，和真实设置不一致。
    LaunchedEffect(downloadSeconds) { downloadPreview = downloadSeconds.toFloat() }
    SliderPreference(
        value = downloadSeconds.toFloat(),
        onValueChange = { value ->
            scope.launch { SpeedTestSettings.setDownloadSeconds(value.toInt()) }
        },
        sliderValue = downloadPreview,
        onSliderValueChange = { downloadPreview = it },
        title = { Text(stringResource(Res.string.speed_test_download_seconds)) },
        summary = { Text(stringResource(Res.string.speed_test_download_seconds_sum)) },
        valueRange = 3f..60f,
        valueSteps = 57,
        icon = { MaskedIcon(Res.drawable.download, IconMaskColors.IconLightBlue) },
        valueText = {
            Text(stringResource(Res.string.speed_test_unit_seconds, downloadPreview.toInt()))
        },
    )

    val uploadSeconds by SpeedTestSettings.uploadSeconds.collectAsStateWithLifecycle(10)
    var uploadPreview by remember { mutableFloatStateOf(uploadSeconds.toFloat()) }
    // 落盘值变化（含首次从 DataStore 读到真值）时同步预览态：
    // remember 初值取的是 collectAsStateWithLifecycle 的默认值，不同步的话
    // 滑块会一直停在默认值上，和真实设置不一致。
    LaunchedEffect(uploadSeconds) { uploadPreview = uploadSeconds.toFloat() }
    SliderPreference(
        value = uploadSeconds.toFloat(),
        onValueChange = { value ->
            scope.launch { SpeedTestSettings.setUploadSeconds(value.toInt()) }
        },
        sliderValue = uploadPreview,
        onSliderValueChange = { uploadPreview = it },
        title = { Text(stringResource(Res.string.speed_test_upload_seconds)) },
        summary = { Text(stringResource(Res.string.speed_test_upload_seconds_sum)) },
        valueRange = 3f..60f,
        valueSteps = 57,
        icon = { MaskedIcon(Res.drawable.file_upload, IconMaskColors.IconCoral) },
        valueText = {
            Text(stringResource(Res.string.speed_test_unit_seconds, uploadPreview.toInt()))
        },
    )

    val measureUpload by SpeedTestSettings.measureUpload.collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = measureUpload,
        onValueChange = { value ->
            scope.launch { SpeedTestSettings.setMeasureUpload(value) }
        },
        title = { Text(stringResource(Res.string.speed_test_measure_upload)) },
        summary = { Text(stringResource(Res.string.speed_test_measure_upload_sum)) },
        icon = { MaskedIcon(Res.drawable.speed, IconMaskColors.IconCyan) },
    )

    val autoRemove by SpeedTestSettings.autoRemove.collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = autoRemove,
        onValueChange = { value ->
            scope.launch { SpeedTestSettings.setAutoRemove(value) }
        },
        title = { Text(stringResource(Res.string.speed_test_auto_remove)) },
        summary = { Text(stringResource(Res.string.speed_test_auto_remove_sum)) },
        icon = {
            MaskedIcon(
                Res.drawable.delete_forever,
                IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
    )

    val historyKeep by SpeedTestSettings.historyKeep.collectAsStateWithLifecycle(10)
    var historyPreview by remember { mutableFloatStateOf(historyKeep.toFloat()) }
    // 落盘值变化（含首次从 DataStore 读到真值）时同步预览态：
    // remember 初值取的是 collectAsStateWithLifecycle 的默认值，不同步的话
    // 滑块会一直停在默认值上，和真实设置不一致。
    LaunchedEffect(historyKeep) { historyPreview = historyKeep.toFloat() }
    SliderPreference(
        value = historyKeep.toFloat(),
        onValueChange = { value ->
            scope.launch { SpeedTestSettings.setHistoryKeep(value.toInt()) }
        },
        sliderValue = historyPreview,
        onSliderValueChange = { historyPreview = it },
        title = { Text(stringResource(Res.string.speed_test_history_keep)) },
        summary = { Text(stringResource(Res.string.speed_test_history_keep_sum)) },
        valueRange = 1f..50f,
        valueSteps = 49,
        icon = { MaskedIcon(Res.drawable.timer, IconMaskColors.IconWarmGray) },
        valueText = {
            Text(stringResource(Res.string.speed_test_unit_records, historyPreview.toInt()))
        },
    )
}
