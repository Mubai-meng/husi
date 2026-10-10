package com.fr.husi.ui.configuration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItemScope
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import com.ernestoyaquello.dragdropswipelazycolumn.state.rememberDragDropSwipeLazyColumnState
import com.fr.husi.GroupType
import com.fr.husi.compose.BoxedVerticalScrollbar
import com.fr.husi.compose.SheetActionRow
import com.fr.husi.speedtest.SpeedTestCardButton
import com.fr.husi.speedtest.SpeedTestEntity
import com.fr.husi.speedtest.SpeedTestManager
import com.fr.husi.speedtest.formatSpeed
import com.fr.husi.compose.SheetSectionTitle
import com.fr.husi.compose.SimpleIconButton
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.colorForUrlTestDelay
import com.fr.husi.compose.fadingEdge
import com.fr.husi.compose.focusRestoreAnchor
import com.fr.husi.compose.material3.Icon
import com.fr.husi.compose.material3.IconButton
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.rememberFocusRestoreState
import com.fr.husi.compose.setPlainText
import com.fr.husi.database.ProxyEntity
import com.fr.husi.database.displayType
import com.fr.husi.fmt.ValidateResult
import com.fr.husi.fmt.config.ConfigBean
import com.fr.husi.fmt.toUniversalLink
import com.fr.husi.keyevent.isTypeControlPressed
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.blankAsNull
import com.fr.husi.ktx.blurAddress
import com.fr.husi.ktx.readableMessage
import com.fr.husi.ktx.readableUrlTestError
import com.fr.husi.resources.Res
import com.fr.husi.resources.action_export_clipboard
import com.fr.husi.resources.action_export_file
import com.fr.husi.resources.action_export_msg
import com.fr.husi.resources.arrow_outward
import com.fr.husi.resources.available
import com.fr.husi.resources.connection_test_unreachable
import com.fr.husi.resources.content_copy
import com.fr.husi.resources.copy_all
import com.fr.husi.resources.delete
import com.fr.husi.resources.deprecated
import com.fr.husi.resources.drag_indicator
import com.fr.husi.resources.edit
import com.fr.husi.resources.error
import com.fr.husi.resources.error_title
import com.fr.husi.resources.file_export
import com.fr.husi.resources.fingerprint
import com.fr.husi.resources.insecure
import com.fr.husi.resources.internal_link
import com.fr.husi.resources.link
import com.fr.husi.resources.menu_configuration
import com.fr.husi.resources.ok
import com.fr.husi.resources.outbound
import com.fr.husi.resources.qr_code
import com.fr.husi.resources.send
import com.fr.husi.resources.settings
import com.fr.husi.resources.share
import com.fr.husi.resources.share_qr_nfc
import com.fr.husi.resources.standard
import com.fr.husi.resources.traffic
import com.fr.husi.resources.unavailable
import com.fr.husi.resources.warning
import com.fr.husi.ui.NavRoutes
import com.fr.husi.ui.StringOrRes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun GroupHolderScreen(
    modifier: Modifier = Modifier,
    viewModel: GroupProfilesHolderViewModel,
    contentPadding: PaddingValues,
    showActions: Boolean = true,
    canHoldFocus: Boolean,
    onProfileSelect: (Long) -> Unit,
    onOpenProfileEditor: ((NavRoutes.ProfileEditor) -> Unit)? = null,
    showQR: (name: String, url: String) -> Unit,
    onCopySuccess: () -> Unit,
    showSnackbar: (message: StringOrRes) -> Unit,
    showUndoSnackbar: (count: Int, onUndo: () -> Unit) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.hiddenProfiles) {
        if (uiState.hiddenProfiles > 0) {
            showUndoSnackbar(uiState.hiddenProfiles) {
                viewModel.undo()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.commit()
        }
    }
    val showAddress by viewModel.alwaysShowAddress.collectAsStateWithLifecycle(false)
    val blurAddress by viewModel.blurredAddress.collectAsStateWithLifecycle(false)

    // 带宽测速（与延迟测试完全解耦的独立任务系统）：整组进度条 +
    // 节点状态行"测速结果在延迟前"展示。只读 SpeedTestManager 状态，
    // 不触碰任何运行中实例。
    val speedUiState by SpeedTestManager.uiState.collectAsStateWithLifecycle()
    val speedResults by SpeedTestManager.groupResults.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.group.id) {
        SpeedTestManager.loadGroupResults(viewModel.group.id)
    }

    val dragDropListState = rememberDragDropSwipeLazyColumnState()
    val focusRestore = rememberFocusRestoreState()

    LaunchedEffect(uiState.scrollIndex) {
        uiState.scrollIndex?.let { index ->
            // 位置恢复（冷启动/进页面）用瞬时跳转：动画滚动会连续组合
            // 途经的每个卡片（bean 反序列化 + 图标加载），节点多时明显掉帧。
            if (uiState.scrollAnimated) {
                dragDropListState.lazyListState.animateScrollToItem(index)
            } else {
                dragDropListState.lazyListState.scrollToItem(index)
            }
            viewModel.consumeScrollIndex()
        }
    }

    LaunchedEffect(canHoldFocus, focusRestore.isAttached) {
        if (canHoldFocus) {
            focusRestore.restore()
        }
    }

    fun openProfileEditor(profile: ProxyEntity) {
        onOpenProfileEditor?.invoke(
            NavRoutes.ProfileEditor(
                type = profile.type,
                id = profile.id,
                subscription = viewModel.group.type == GroupType.SUBSCRIPTION,
            ),
        )
    }

    var exportConfig by remember { mutableStateOf("") }
    val exportFileLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { file ->
        if (file != null) lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                file.write(exportConfig.encodeToByteArray())
                withContext(Dispatchers.Main) {
                    showSnackbar(StringOrRes.Res(Res.string.action_export_msg))
                }
            } catch (e: Exception) {
                Logs.w(e)
                withContext(Dispatchers.Main) {
                    showSnackbar(StringOrRes.Direct(e.readableMessage))
                }
            }
        }
        exportConfig = ""
    }

    var showErrorAlert by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
        DragDropSwipeLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRestoreAnchor(focusRestore, canHoldFocus)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }

                    fun selected() = uiState.profiles.firstOrNull { it.isSelected }
                    when {
                        !keyEvent.isTypeControlPressed && !keyEvent.isShiftPressed &&
                            keyEvent.key == Key.J -> {
                            viewModel.profileToSelect(1)?.let {
                                onProfileSelect(it)
                                viewModel.scrollToProxy(it, false)
                            }
                            true
                        }

                        !keyEvent.isTypeControlPressed && !keyEvent.isShiftPressed &&
                            keyEvent.key == Key.K -> {
                            viewModel.profileToSelect(-1)?.let {
                                onProfileSelect(it)
                                viewModel.scrollToProxy(it, false)
                            }
                            true
                        }

                        keyEvent.isTypeControlPressed && keyEvent.key == Key.E -> {
                            selected()?.profile?.let(::openProfileEditor)
                            true
                        }

                        !keyEvent.isTypeControlPressed && !keyEvent.isShiftPressed &&
                            keyEvent.key == Key.Delete -> {
                            selected()?.profile?.id?.let(viewModel::undoableRemove)
                            true
                        }

                        keyEvent.isTypeControlPressed && keyEvent.isShiftPressed &&
                            keyEvent.key == Key.C -> {
                            selected()?.profile?.takeIf { it.haveLink() }?.let { entity ->
                                scope.launch {
                                    clipboard.setPlainText(entity.requireBean().toUniversalLink())
                                    onCopySuccess()
                                }
                            }
                            true
                        }

                        keyEvent.isTypeControlPressed && !keyEvent.isShiftPressed &&
                            keyEvent.key == Key.C -> {
                            selected()?.profile?.takeIf { it.haveStandardLink() }?.let { entity ->
                                scope.launch {
                                    clipboard.setPlainText(entity.toStdLink())
                                    onCopySuccess()
                                }
                            }
                            true
                        }

                        keyEvent.isTypeControlPressed && keyEvent.isShiftPressed &&
                            keyEvent.key == Key.Q -> {
                            selected()?.profile?.takeIf { it.haveLink() }?.let { entity ->
                                showQR(entity.displayName(), entity.requireBean().toUniversalLink())
                            }
                            true
                        }

                        keyEvent.isTypeControlPressed && !keyEvent.isShiftPressed &&
                            keyEvent.key == Key.Q -> {
                            selected()?.profile?.takeIf { it.haveStandardLink() }?.let { entity ->
                                showQR(entity.displayName(), entity.toStdLink())
                            }
                            true
                        }

                        else -> false
                    }
                }
                .fadingEdge(dragDropListState.lazyListState),
            state = dragDropListState,
            items = uiState.profiles.toImmutableList(),
            key = { it.profile.id },
            contentType = { 0 },
            contentPadding = contentPadding,
            userScrollEnabled = true,
            onIndicesChangedViaDragAndDrop = { viewModel.submitReordered(it) },
        ) { index, item ->
            DraggableSwipeableItem(
                modifier = Modifier
                    .padding(4.dp)
                    .animateDraggableSwipeableItem(),
                colors = DraggableSwipeableItemColors.createRemembered(
                    containerBackgroundColor = Color.Transparent,
                    containerBackgroundColorWhileDragged = Color.Transparent,
                    clickIndicationColor = Color.Transparent,
                    behindSwipeContainerBackgroundColor = Color.Transparent,
                    behindSwipeIconColor = Color.Transparent,
                ),
                dragDropEnabled = uiState.canReorder,
            ) {
                ProxyCard(
                    profile = item,
                    select = { onProfileSelect(item.profile.id) },
                    edit = {
                        openProfileEditor(item.profile)
                    },
                    delete = { viewModel.undoableRemove(item.profile.id) },
                    showQR = { url ->
                        showQR(item.profile.displayName(), url)
                    },
                    exportToFile = { name, config ->
                        exportConfig = config
                        exportFileLauncher.launch(suggestedName = name, defaultExtension = "json")
                    },
                    showErrorAlert = { showErrorAlert = it },
                    onCopySuccess = onCopySuccess,
                    showAddress = showAddress,
                    blurAddress = blurAddress,
                    showActions = showActions,
                    speedResult = speedResults[item.profile.id],
                    speedLiveRate = speedUiState.liveRates[item.profile.id],
                )
            }
        }

        // 整组带宽测速进度条：悬浮在 tab 行正下方（align TopCenter +
        // contentPadding.top 偏移），不占布局高度。
        // ⚠️ 旧实现是插在列表上方的独立区块：本屏 Column 从屏幕顶开始
        // （列表靠 contentPadding.top 让出顶栏），区块本体被搜索栏+tab
        // 完全遮住、高度却把列表整体下顶 —— 表现为"测速时节点上方多出
        // 一片空白"且进度信息不可见。浮层方案零位移且进度可见。
        if (speedUiState.running) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (speedUiState.total > 0) {
                    Text(
                        text = "带宽测速 ${speedUiState.processed}/${speedUiState.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (speedUiState.total > 0) {
                                speedUiState.processed.toFloat() / speedUiState.total
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
                speedUiState.message?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        }

        BoxedVerticalScrollbar(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = dragDropListState.lazyListState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
        }
    }

    if (showErrorAlert != null) AlertDialog(
        onDismissRequest = { showErrorAlert = null },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showErrorAlert = null
            }
        },
        icon = {
            Icon(vectorResource(Res.drawable.error), null)
        },
        title = { Text(stringResource(Res.string.error_title)) },
        text = { Text(showErrorAlert!!) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraggableSwipeableItemScope<ProfileItem>.ProxyCard(
    modifier: Modifier = Modifier,
    profile: ProfileItem,
    select: () -> Unit,
    edit: () -> Unit,
    delete: () -> Unit,
    showQR: (url: String) -> Unit,
    onCopySuccess: () -> Unit,
    exportToFile: (name: String, config: String) -> Unit,
    showErrorAlert: (String) -> Unit,
    showAddress: Boolean,
    blurAddress: Boolean,
    showActions: Boolean = true,
    speedResult: SpeedTestEntity? = null,
    speedLiveRate: Long? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val entity = profile.profile
    val bean = entity.requireBean()

    val (name, address) = when {
        blurAddress && bean.name.isBlank() -> bean.displayAddress().blurAddress() to null
        blurAddress && showAddress -> bean.displayName() to bean.displayAddress().blurAddress()
        showAddress -> bean.displayName() to bean.displayAddress()
        else -> bean.displayName() to null
    }

    val hasTraffic = entity.tx + entity.rx > 0L
    // 流量文本与安全校验已在 ViewModel 后台线程预计算（formatBytes 走
    // JNI、isInsecure 有校验开销，组合期逐卡执行会掉帧）。
    val trafficText = profile.trafficFormatted?.takeIf { hasTraffic }?.let { (tx, rx) ->
        stringResource(Res.string.traffic, tx, rx)
    }

    // 带宽测速摘要（独立一行, 不与延迟/流量同行 —— 过长文本会挤压
    // 卡片布局导致地址竖排）: 进行中显示实时速率, 完成显示结果。
    val speedLine = when {
        speedLiveRate != null && speedLiveRate > 0L ->
            "测速中 ↓" + formatSpeed(speedLiveRate)

        speedResult != null && speedResult.downloadBps > 0L -> buildString {
            append("↓").append(formatSpeed(speedResult.downloadBps))
            if (speedResult.uploadBps > 0L) {
                append(" ↑").append(formatSpeed(speedResult.uploadBps))
            }
        }

        speedResult != null && speedResult.error != null -> "测速失败"

        else -> null
    }

    val (statusText, statusColor) = when (entity.status) {
        in Int.MIN_VALUE..ProxyEntity.STATUS_INITIAL -> {
            trafficText.orEmpty() to MaterialTheme.colorScheme.onSurfaceVariant
        }

        ProxyEntity.STATUS_AVAILABLE -> {
            stringResource(
                Res.string.available,
                entity.ping,
            ) to colorForUrlTestDelay(entity.ping)
        }

        ProxyEntity.STATUS_UNAVAILABLE -> {
            val text = readableUrlTestError(entity.error)?.let { stringResource(it) }
                ?: stringResource(Res.string.unavailable)
            text to Color.Red
        }

        ProxyEntity.STATUS_UNREACHABLE -> {
            val text = readableUrlTestError(entity.error)?.let { stringResource(it) }
                ?: stringResource(Res.string.connection_test_unreachable)
            text to Color.Red
        }

        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val showMiddleRow =
        address != null || (hasTraffic && entity.status > ProxyEntity.STATUS_INITIAL)

    var showShareSheet by remember { mutableStateOf(false) }
    var showSecurityAlert by remember { mutableStateOf(false) }
    val shareSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    // 安全校验已在 ViewModel 预计算（insecureResult 为 null 表示未开启）。
    val validateResult = if (showActions) {
        profile.insecureResult ?: ValidateResult.Secure.Continue
    } else {
        ValidateResult.Secure.Continue
    }

    OutlinedCard(
        onClick = select,
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(),
        border = if (profile.isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.drag_indicator),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(40.dp)
                    .padding(8.dp)
                    .dragDropModifier(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    if (showActions) {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.edit),
                            contentDescription = stringResource(Res.string.edit),
                            modifier = Modifier.size(40.dp),
                            onClick = edit,
                        )

                        val shareIcon: DrawableResource
                        val shareBackground: Color
                        val shareTint: Color
                        when (validateResult) {
                            is ValidateResult.Insecure -> {
                                shareIcon = Res.drawable.warning
                                shareBackground = Color.Red
                                shareTint = Color.White
                            }

                            is ValidateResult.Deprecated -> {
                                shareIcon = Res.drawable.warning
                                shareBackground = Color.Yellow
                                shareTint = Color.Gray
                            }

                            is ValidateResult.Secure -> {
                                shareIcon = Res.drawable.share
                                shareBackground = Color.Transparent
                                shareTint = MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }

                        Box {
                            val shareTooltipText = when (validateResult) {
                                is ValidateResult.Insecure -> stringResource(Res.string.insecure)
                                is ValidateResult.Deprecated -> stringResource(Res.string.deprecated)
                                is ValidateResult.Secure -> stringResource(Res.string.share)
                            }
                            val shareTooltipState = rememberTooltipState()

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(shareBackground, shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        positioning = TooltipAnchorPosition.Below,
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(shareTooltipText)
                                        }
                                    },
                                    state = shareTooltipState,
                                ) {
                                    IconButton(
                                        onClick = {
                                            when (validateResult) {
                                                is ValidateResult.Insecure, is ValidateResult.Deprecated -> {
                                                    showSecurityAlert = true
                                                }

                                                is ValidateResult.Secure -> {
                                                    showShareSheet = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = vectorResource(shareIcon),
                                            contentDescription = shareTooltipText,
                                            tint = shareTint,
                                        )
                                    }
                                }
                            }

                            if (showShareSheet) {
                                val canNotShareOutbound = entity.type == ProxyEntity.TYPE_CHAIN ||
                                        entity.type == ProxyEntity.TYPE_PROXY_SET ||
                                        entity.mustUsePlugin() ||
                                        (bean as? ConfigBean)?.type == ConfigBean.TYPE_CONFIG

                                ModalBottomSheet(
                                    onDismissRequest = { showShareSheet = false },
                                    sheetState = shareSheetState,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (entity.haveLink()) {
                                            SheetSectionTitle(
                                                text = stringResource(Res.string.share_qr_nfc),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.qr_code),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            if (entity.haveStandardLink()) {
                                                SheetActionRow(
                                                    text = stringResource(Res.string.standard),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.send,
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        showQR(entity.toStdLink())
                                                        showShareSheet = false
                                                    },
                                                )
                                            }
                                            SheetActionRow(
                                                text = stringResource(Res.string.internal_link),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.link),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    showQR(bean.toUniversalLink())
                                                    showShareSheet = false
                                                },
                                            )
                                            HorizontalDivider()
                                            SheetSectionTitle(
                                                text = stringResource(Res.string.action_export_clipboard),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.share),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            if (entity.haveStandardLink()) {
                                                SheetActionRow(
                                                    text = stringResource(Res.string.standard),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.content_copy,
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        scope.launch {
                                                            clipboard.setPlainText(entity.toStdLink())
                                                            onCopySuccess()
                                                        }
                                                        showShareSheet = false
                                                    },
                                                )
                                            }
                                            SheetActionRow(
                                                text = stringResource(Res.string.internal_link),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.fingerprint),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    scope.launch {
                                                        clipboard.setPlainText(bean.toUniversalLink())
                                                        onCopySuccess()
                                                    }
                                                    showShareSheet = false
                                                },
                                            )
                                        }
                                        HorizontalDivider()
                                        SheetSectionTitle(
                                            text = stringResource(Res.string.menu_configuration),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = vectorResource(Res.drawable.settings),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                        )
                                        SheetActionRow(
                                            text = stringResource(Res.string.action_export_clipboard),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = vectorResource(Res.drawable.copy_all),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        clipboard.setPlainText(entity.exportConfig().first)
                                                    }.onSuccess {
                                                        onCopySuccess()
                                                    }.onFailure { e ->
                                                        showErrorAlert(e.readableMessage)
                                                    }
                                                }
                                                showShareSheet = false
                                            },
                                        )
                                        SheetActionRow(
                                            text = stringResource(Res.string.action_export_file),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = vectorResource(Res.drawable.file_export),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        val data = entity.exportConfig()
                                                        exportToFile(data.second, data.first)
                                                    }.onFailure { e ->
                                                        showErrorAlert(e.readableMessage)
                                                    }
                                                }
                                                showShareSheet = false
                                            },
                                        )

                                        if (!canNotShareOutbound) {
                                            HorizontalDivider()
                                            SheetSectionTitle(
                                                text = stringResource(Res.string.outbound),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.arrow_outward),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            SheetActionRow(
                                                text = stringResource(Res.string.action_export_clipboard),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.copy_all),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    scope.launch {
                                                        clipboard.setPlainText(entity.exportOutbound().first)
                                                        onCopySuccess()
                                                    }
                                                    showShareSheet = false
                                                },
                                            )
                                            SheetActionRow(
                                                text = stringResource(Res.string.action_export_file),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.file_export),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    scope.launch {
                                                        val data = entity.exportOutbound()
                                                        exportToFile(data.second, data.first)
                                                    }
                                                    showShareSheet = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 带宽测速按钮（与延迟测试完全独立的任务系统，
                        // 实现见 com.fr.husi.speedtest.SpeedTestCardButton）。
                        SpeedTestCardButton(
                            proxyId = entity.id,
                            groupId = entity.groupId,
                        )

                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                            modifier = Modifier.size(40.dp),
                            onClick = delete,
                        )
                    }
                }

                // 带宽测速摘要独立行: 视觉上位于延迟行之前（用户要求），
                // 单独一行避免长文本挤压地址/流量行导致布局竖排。
                if (speedLine != null) {
                    Text(
                        text = speedLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, end = 16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (showMiddleRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        address?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (hasTraffic && entity.status > ProxyEntity.STATUS_INITIAL) {
                            trafficText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = entity.displayType(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )

                    if (statusText.isNotEmpty()) {
                        val errorText = entity.error?.blankAsNull()
                        Text(
                            text = statusText,
                            modifier = Modifier.clickable {
                                errorText?.let(showErrorAlert)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                        )
                    }
                }
            }
        }
    }

    if (showActions && showSecurityAlert) AlertDialog(
        onDismissRequest = {
            showSecurityAlert = false
            showShareSheet = true
        },
        icon = {
            Icon(vectorResource(Res.drawable.warning), null)
        },
        title = {
            Text(
                stringResource(
                    when (validateResult) {
                        is ValidateResult.Insecure -> Res.string.insecure
                        is ValidateResult.Deprecated -> Res.string.deprecated
                        else -> error("impossible")
                    },
                ),
            )
        },
        text = {
            val textRes = when (validateResult) {
                is ValidateResult.Insecure -> validateResult.textRes
                is ValidateResult.Deprecated -> validateResult.textRes
                else -> error("impossible")
            }
            Text(stringResource(textRes))
        },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showSecurityAlert = false
                showShareSheet = true
            }
        },
    )
}
