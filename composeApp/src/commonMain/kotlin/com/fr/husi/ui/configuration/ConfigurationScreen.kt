@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fr.husi.ui.configuration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.fr.husi.compose.CapsuleHeader
import com.fr.husi.compose.paddingHorizontal
import com.fr.husi.compose.ClipboardContent
import com.fr.husi.compose.CapsuleSearchInputField
import com.fr.husi.compose.CapsuleSearchTopBar
import com.fr.husi.compose.DropdownMenuAction
import com.fr.husi.compose.DropdownMenuActions
import com.fr.husi.compose.QRCodeDialog
import com.fr.husi.compose.SagerFabClearance
import com.fr.husi.compose.ScrollableDialog
import com.fr.husi.compose.SimpleIconButton
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.colorForUrlTestDelay
import com.fr.husi.compose.decodeQRCode
import com.fr.husi.compose.getFirstContent
import com.fr.husi.compose.material3.Icon
import com.fr.husi.compose.material3.PrimaryScrollableTabRow
import com.fr.husi.compose.material3.Tab
import com.fr.husi.compose.material3.Text
import com.fr.husi.database.DataStore
import com.fr.husi.database.ProxyEntity
import com.fr.husi.database.displayType
import com.fr.husi.speedtest.SpeedTestGroupLoader
import com.fr.husi.speedtest.SpeedTestManager
import com.fr.husi.speedtest.SpeedTestMultiSelectDialog
import com.fr.husi.keyevent.isTypeControlPressed
import com.fr.husi.ktx.runOnIoDispatcher
import com.fr.husi.repository.resolveRepository
import com.fr.husi.resources.Res
import com.fr.husi.resources.action_anytls
import com.fr.husi.resources.action_direct
import com.fr.husi.resources.action_http
import com.fr.husi.resources.action_hysteria
import com.fr.husi.resources.action_import
import com.fr.husi.resources.action_import_file
import com.fr.husi.resources.action_juicity
import com.fr.husi.resources.action_masque
import com.fr.husi.resources.action_mieru
import com.fr.husi.resources.action_naive
import com.fr.husi.resources.action_openconnect
import com.fr.husi.resources.action_openvpn
import com.fr.husi.resources.action_shadowquic
import com.fr.husi.resources.action_shadowsocks
import com.fr.husi.resources.action_shadowtls
import com.fr.husi.resources.action_snell
import com.fr.husi.resources.action_socks
import com.fr.husi.resources.action_ssh
import com.fr.husi.resources.action_trojan
import com.fr.husi.resources.action_trusttunnel
import com.fr.husi.resources.action_tuic
import com.fr.husi.resources.action_vless
import com.fr.husi.resources.action_vmess
import com.fr.husi.resources.action_wireguard
import com.fr.husi.resources.add_profile
import com.fr.husi.resources.add_profile_methods_manual_settings
import com.fr.husi.resources.apply
import com.fr.husi.resources.cancel
import com.fr.husi.resources.clear_traffic_statistics
import com.fr.husi.resources.close
import com.fr.husi.resources.connection_test
import com.fr.husi.resources.connection_test_clear_results
import com.fr.husi.resources.connection_test_delete_unavailable
import com.fr.husi.resources.connection_test_domain_not_found
import com.fr.husi.resources.connection_test_error
import com.fr.husi.resources.connection_test_icmp_ping
import com.fr.husi.resources.connection_test_icmp_ping_unavailable
import com.fr.husi.resources.connection_test_refused
import com.fr.husi.resources.connection_test_tcp_ping
import com.fr.husi.resources.connection_test_tcp_ping_unavailable
import com.fr.husi.resources.connection_test_timeout
import com.fr.husi.resources.connection_test_unreachable
import com.fr.husi.resources.connection_test_url_test
import com.fr.husi.resources.copy_success
import com.fr.husi.resources.custom_config
import com.fr.husi.resources.delete_profiles_confirm_prompt
import com.fr.husi.resources.ecg
import com.fr.husi.resources.group_order_by_delay
import com.fr.husi.resources.group_order_by_name
import com.fr.husi.resources.group_order_origin
import com.fr.husi.resources.menu_group
import com.fr.husi.resources.more
import com.fr.husi.resources.more_vert
import com.fr.husi.resources.need_reload
import com.fr.husi.resources.no_proxies_found_in_clipboard
import com.fr.husi.resources.no_proxies_found_in_file
import com.fr.husi.resources.note_add
import com.fr.husi.resources.ok
import com.fr.husi.resources.plugin_unknown
import com.fr.husi.resources.proxy_chain
import com.fr.husi.resources.proxy_set
import com.fr.husi.resources.remove_duplicate
import com.fr.husi.resources.removed
import com.fr.husi.resources.search
import com.fr.husi.resources.search_go
import com.fr.husi.resources.sort_mode
import com.fr.husi.resources.undo
import com.fr.husi.resources.view_list
import com.fr.husi.results.ResultEffect
import com.fr.husi.ui.LocalSnackbarEmitter
import com.fr.husi.ui.MainViewModel
import com.fr.husi.ui.NavRoutes
import com.fr.husi.ui.StringOrRes
import com.fr.husi.ui.profile.ProfileEditorResult
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.reflect.KClass

@Composable
fun ConfigurationScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onOpenGroups: () -> Unit,
    onOpenGroupSettings: (Long) -> Unit,
    vm: ConfigurationScreenViewModel = viewModel { ConfigurationScreenViewModel() },
    onOpenProfileEditor: ((NavRoutes.ProfileEditor) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current
    val clipboard = LocalClipboard.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    ResultEffect<ProfileEditorResult> { result ->
        if (!result.updated) return@ResultEffect
        if (result.profileId != DataStore.selectedProxy.get()) return@ResultEffect
        if (!DataStore.serviceState.started) return@ResultEffect
        snackbar.show(
            StringOrRes.Res(Res.string.need_reload),
            StringOrRes.Res(Res.string.apply),
        ) { action ->
            if (action == SnackbarResult.ActionPerformed) {
                resolveRepository().reloadService()
            }
        }
    }

    val importFile = rememberFilePickerLauncher { file ->
        if (file != null) {
            vm.importFile(
                file = file,
                onProxiesFound = { proxies ->
                    runOnIoDispatcher {
                        mainViewModel.importProfile(proxies)
                    }
                },
                onSubscriptionFound = { uri ->
                    mainViewModel.importSubscription(uri)
                },
                onNoProxies = {
                    snackbar.show(StringOrRes.Res(Res.string.no_proxies_found_in_file))
                },
                onError = { message ->
                    snackbar.show(StringOrRes.Direct(message))
                },
            )
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val hasGroups = uiState.groups.isNotEmpty()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle(
        DataStore.GROUP_NOPE,
    )
    // 响应式收集测速运行状态（不能裸调 isRunning()：StateFlow.value
    // 读取不建立订阅，菜单文案/分支不会随状态变化重组）。
    val speedUiState by SpeedTestManager.uiState.collectAsStateWithLifecycle()
    val speedRunning = speedUiState.running
    val pagerState = rememberPagerState(
        initialPage = uiState.groups
            .indexOfFirst { it.id == selectedGroup }
            .fastCoerceIn(0, (uiState.groups.size - 1).fastCoerceAtLeast(0)),
        pageCount = { uiState.groups.size },
    )
    GroupPagerSelectionSync(
        pagerState = pagerState,
        groups = uiState.groups,
        selectedGroup = selectedGroup,
        onSettledGroupChange = { DataStore.selectedGroup.set(it) },
        onPageChange = {
            vm.clearSearchQuery()
            focusManager.clearFocus()
        },
    )

    var showAddMenu by remember { mutableStateOf(false) }
    var showAddManualMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showConnectionTestMenu by remember { mutableStateOf(false) }
    var showSpeedTestSelect by remember { mutableStateOf(false) }
    var showOrderMenu by remember { mutableStateOf(false) }
    val searchBarState = rememberSearchBarState()
    val searchTextFieldState = vm.searchTextFieldState
    val searchInputField: @Composable () -> Unit = {
        CapsuleSearchInputField(
            textFieldState = searchTextFieldState,
            searchBarState = searchBarState,
            onSearch = { focusManager.clearFocus() },
            placeholder = { Text(stringResource(Res.string.search_go)) },
            leadingIcon = {
                Icon(vectorResource(Res.drawable.search), null)
            },
            trailingIcon = if (searchBarState.currentValue == SearchBarValue.Expanded) {
                {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.cancel),
                        onClick = {
                            vm.clearSearchQuery()
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    )
                }
            } else {
                null
            },
        )
    }

    val currentOrder =
        if (pagerState.pageCount > 0 && pagerState.currentPage < uiState.groups.size) {
            uiState.groups[pagerState.currentPage].order
        } else {
            0
        }

    val windowInsets = WindowInsets.safeDrawing

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()

    LaunchedEffect(Unit) {
        vm.scrollToProxy(DataStore.selectedProxy.get())
    }

    val manualProfileEntries = remember {
        listOf(
            Res.string.proxy_set to ProxyEntity.TYPE_PROXY_SET,
            Res.string.action_socks to ProxyEntity.TYPE_SOCKS,
            Res.string.action_http to ProxyEntity.TYPE_HTTP,
            Res.string.action_shadowsocks to ProxyEntity.TYPE_SS,
            Res.string.action_vmess to ProxyEntity.TYPE_VMESS,
            Res.string.action_vless to ProxyEntity.TYPE_VLESS,
            Res.string.action_trojan to ProxyEntity.TYPE_TROJAN,
            Res.string.action_mieru to ProxyEntity.TYPE_MIERU,
            Res.string.action_naive to ProxyEntity.TYPE_NAIVE,
            Res.string.action_hysteria to ProxyEntity.TYPE_HYSTERIA,
            Res.string.action_tuic to ProxyEntity.TYPE_TUIC,
            Res.string.action_juicity to ProxyEntity.TYPE_JUICITY,
            Res.string.action_direct to ProxyEntity.TYPE_DIRECT,
            Res.string.action_ssh to ProxyEntity.TYPE_SSH,
            Res.string.action_wireguard to ProxyEntity.TYPE_WG,
            Res.string.action_openconnect to ProxyEntity.TYPE_OPENCONNECT,
            Res.string.action_openvpn to ProxyEntity.TYPE_OPENVPN,
            Res.string.action_masque to ProxyEntity.TYPE_MASQUE,
            Res.string.action_shadowtls to ProxyEntity.TYPE_SHADOWTLS,
            Res.string.action_anytls to ProxyEntity.TYPE_ANYTLS,
            Res.string.action_shadowquic to ProxyEntity.TYPE_SHADOWQUIC,
            Res.string.action_trusttunnel to ProxyEntity.TYPE_TRUST_TUNNEL,
            Res.string.action_snell to ProxyEntity.TYPE_SNELL,
            Res.string.custom_config to ProxyEntity.TYPE_CONFIG,
            Res.string.proxy_chain to ProxyEntity.TYPE_CHAIN,
        )
    }

    fun openProfileEditor(type: Int, id: Long = -1L, isSubscription: Boolean = false) {
        showAddManualMenu = false
        onOpenProfileEditor?.invoke(
            NavRoutes.ProfileEditor(
                type = type,
                id = id,
                subscription = isSubscription,
            ),
        )
    }

    fun importFromClipboard() {
        lifecycleOwner.lifecycleScope.launch {
            when (val content = clipboard.getFirstContent()) {
                is ClipboardContent.Text -> mainViewModel.parseProxy(content.text)

                is ClipboardContent.Image -> {
                    val text = withContext(Dispatchers.IO) { decodeQRCode(content.bitmap) }
                    if (text == null) {
                        snackbar.show(
                            StringOrRes.Res(Res.string.no_proxies_found_in_clipboard),
                        )
                    } else {
                        mainViewModel.parseProxy(text)
                    }
                }

                null -> mainViewModel.parseProxy(null)
            }
        }
    }

    fun scrollToSelectedProxyAcrossGroups() {
        focusManager.clearFocus()
        scope.launch {
            searchBarState.animateToCollapsed()
            val proxyId = DataStore.selectedProxy.get()
            val groupId = vm.proxyGroupId(proxyId) ?: return@launch
            val page = uiState.groups.indexOfFirst { it.id == groupId }
            if (page < 0) return@launch
            if (pagerState.currentPage != page) {
                pagerState.animateScrollToPage(page)
            }
            vm.scrollToProxy(groupId, proxyId, fallbackToTop = true)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                val action = vm.handleKeyAction(
                    key = keyEvent.key,
                    isCtrl = keyEvent.isTypeControlPressed,
                    isShift = keyEvent.isShiftPressed,
                    isSearchActive = searchBarState.currentValue == SearchBarValue.Expanded,
                )
                when (action) {
                    KeyAction.Consumed -> true
                    KeyAction.Unhandled -> false

                    KeyAction.ImportClipboard -> {
                        importFromClipboard()
                        true
                    }

                    is KeyAction.SwitchTab -> {
                        val target = pagerState.currentPage + action.delta
                        if (target in 0 until pagerState.pageCount) {
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        true
                    }

                    KeyAction.OpenSearch -> {
                        scope.launch { searchBarState.animateToExpanded() }
                        true
                    }
                }
            },
        topBar = {
            CapsuleHeader(
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
            ) {
                CapsuleSearchTopBar(
                    hazeState = hazeState,
                    inputField = searchInputField,
                    navigationIcon = null,
                    onSearchPillClick = {
                        scope.launch { searchBarState.animateToExpanded() }
                    },
                    onSearchPillLongPress = ::scrollToSelectedProxyAcrossGroups,
                    actions = {
                        CapsuleActionButton {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.view_list),
                                contentDescription = stringResource(Res.string.menu_group),
                                onClick = onOpenGroups,
                            )
                        }
                        CapsuleActionButton {
                            Box {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.note_add),
                                    contentDescription = stringResource(Res.string.add_profile),
                                    onClick = { showAddMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showAddMenu,
                                    onDismissRequest = { showAddMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    DropdownMenuActions(
                                        listOfNotNull(
                                            scannerMenuAction(
                                                onDismissMenu = { showAddMenu = false },
                                            ),
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.action_import),
                                            ) {
                                                showAddMenu = false
                                                importFromClipboard()
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.action_import_file),
                                            ) {
                                                showAddMenu = false
                                                importFile.launch()
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.add_profile_methods_manual_settings),
                                                opensSubmenu = true,
                                            ) {
                                                showAddMenu = false
                                                showAddManualMenu = true
                                            },
                                        ),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAddManualMenu,
                                    onDismissRequest = { showAddManualMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    DropdownMenuActions(
                                        manualProfileEntries.map { (title, type) ->
                                            DropdownMenuAction(text = stringResource(title)) {
                                                openProfileEditor(type)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        CapsuleActionButton {
                            Box {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.more_vert),
                                    contentDescription = stringResource(Res.string.more),
                                    onClick = { showOverflowMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    DropdownMenuActions(
                                        listOf(
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.clear_traffic_statistics),
                                            ) {
                                                showOverflowMenu = false
                                                vm.clearTrafficStatistics(selectedGroup)
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.remove_duplicate),
                                            ) {
                                                showOverflowMenu = false
                                                vm.removeDuplicate(selectedGroup)
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.connection_test),
                                                opensSubmenu = true,
                                            ) {
                                                showOverflowMenu = false
                                                showConnectionTestMenu = true
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.sort_mode),
                                                opensSubmenu = true,
                                            ) {
                                                showOverflowMenu = false
                                                showOrderMenu = true
                                            },
                                        ),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showConnectionTestMenu,
                                    onDismissRequest = { showConnectionTestMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    DropdownMenuActions(
                                        listOf(
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.connection_test_icmp_ping),
                                            ) {
                                                showConnectionTestMenu = false
                                                scope.launch {
                                                    vm.doTest(
                                                        DataStore.currentGroupId(),
                                                        TestType.ICMPPing,
                                                    )
                                                }
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.connection_test_tcp_ping),
                                            ) {
                                                showConnectionTestMenu = false
                                                scope.launch {
                                                    vm.doTest(
                                                        DataStore.currentGroupId(),
                                                        TestType.TCPPing,
                                                    )
                                                }
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.connection_test_url_test),
                                            ) {
                                                showConnectionTestMenu = false
                                                scope.launch {
                                                    vm.doTest(
                                                        DataStore.currentGroupId(),
                                                        TestType.URLTest,
                                                    )
                                                }
                                            },
                                            // ---- 带宽测速（与延迟测试完全独立的任务系统）----
                                            DropdownMenuAction(
                                                text = if (speedRunning) {
                                                    "带宽测速（整组，点击停止）"
                                                } else {
                                                    "带宽测速（整组）"
                                                },
                                            ) {
                                                showConnectionTestMenu = false
                                                // 进行中再次点击 = 停止本次测速
                                                if (speedRunning) {
                                                    SpeedTestManager.cancel()
                                                } else {
                                                    scope.launch {
                                                        val groupId = DataStore.currentGroupId()
                                                        val profiles = SpeedTestGroupLoader.load(groupId)
                                                        if (profiles.isEmpty()) {
                                                            snackbar.show(
                                                                StringOrRes.Direct("分组内没有可测速的节点"),
                                                            )
                                                        } else if (!SpeedTestManager.startGroup(
                                                                groupId,
                                                                profiles.map {
                                                                    SpeedTestManager.ProxyEntityParams(
                                                                        it.first,
                                                                        it.second,
                                                                    )
                                                                },
                                                            )
                                                        ) {
                                                            snackbar.show(
                                                                StringOrRes.Direct("已有测速任务进行中，请先停止当前任务"),
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            DropdownMenuAction(
                                                text = "带宽测速（多选节点）",
                                            ) {
                                                showConnectionTestMenu = false
                                                showSpeedTestSelect = true
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.connection_test_delete_unavailable),
                                            ) {
                                                showConnectionTestMenu = false
                                                vm.deleteUnavailable(selectedGroup)
                                            },
                                            DropdownMenuAction(
                                                text = stringResource(Res.string.connection_test_clear_results),
                                            ) {
                                                showConnectionTestMenu = false
                                                vm.clearResults(selectedGroup)
                                            },
                                        ),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOrderMenu,
                                    onDismissRequest = { showOrderMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    val orders = listOf(
                                        stringResource(Res.string.group_order_origin),
                                        stringResource(Res.string.group_order_by_name),
                                        stringResource(Res.string.group_order_by_delay),
                                    )
                                    orders.forEachIndexed { i, option ->
                                        DropdownMenuItem(
                                            selected = currentOrder == i,
                                            onClick = {
                                                showOrderMenu = false
                                                vm.updateOrder(selectedGroup, i)
                                            },
                                            text = { Text(text = option) },
                                            shapes = MenuDefaults.itemShape(i, orders.size),
                                        )
                                    }
                                }
                            }

                            if (showSpeedTestSelect && selectedGroup != DataStore.GROUP_NOPE) {
                                SpeedTestMultiSelectDialog(
                                    groupId = selectedGroup,
                                    onDismiss = { showSpeedTestSelect = false },
                                )
                            }
                        }
                    },
                    windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior,
                )

                if (hasGroups && uiState.groups.size > 1) PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.fastCoerceIn(
                        0,
                        uiState.groups.size - 1,
                    ),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                ) {
                    uiState.groups.forEachIndexed { index, group ->
                        Tab(
                            text = { Text(group.displayName()) },
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    if (pagerState.currentPage == index) {
                                        vm.scrollToProxy(
                                            group.id,
                                            DataStore.selectedProxy.get(),
                                            fallbackToTop = true,
                                        )
                                    } else {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            },
                            onLongClick = { onOpenGroupSettings(group.id) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        ConfigurationContent(
            modifier = Modifier
                .fillMaxSize()
                .paddingHorizontal(innerPadding)
                .hazeSource(hazeState),
            vm = vm,
            pagerState = pagerState,
            preSelected = null,
            showActions = true,
            onProfileSelect = vm::onProfileSelect,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + SagerFabClearance,
            ),
            canHoldFocus = searchBarState.currentValue != SearchBarValue.Expanded,
            onOpenProfileEditor = onOpenProfileEditor,
        )
    }

    ConfigurationDialogs(
        vm = vm,
        uiState = uiState,
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = searchInputField,
    ) {
        val currentGroup = uiState.groups.getOrNull(pagerState.currentPage)
        val childVm = currentGroup?.let { vm.childViewModels[it.id] }
        if (childVm != null) {
            val expandedScope = rememberCoroutineScope()
            val snackbar = LocalSnackbarEmitter.current
            GroupHolderScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = childVm,
                showActions = true,
                contentPadding = PaddingValues(),
                canHoldFocus = false,
                onProfileSelect = { id ->
                    vm.onProfileSelect(id)
                    expandedScope.launch { searchBarState.animateToCollapsed() }
                },
                onOpenProfileEditor = onOpenProfileEditor?.let { callback ->
                    { route ->
                        expandedScope.launch { searchBarState.animateToCollapsed() }
                        callback(route)
                    }
                },
                showQR = { name, url ->
                    expandedScope.launch { searchBarState.animateToCollapsed() }
                    // QR dialog will be shown in the parent composition
                },
                onCopySuccess = {
                    snackbar.show(StringOrRes.Res(Res.string.copy_success))
                },
                showSnackbar = snackbar::show,
                showUndoSnackbar = { count, onUndo ->
                    snackbar.show(
                        StringOrRes.PluralsRes(Res.plurals.removed, count, count),
                        StringOrRes.Res(Res.string.undo),
                    ) { result ->
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndo()
                        }
                    }
                },
            )
        }
    }

}

@Composable
fun ConfigurationContent(
    modifier: Modifier = Modifier,
    vm: ConfigurationScreenViewModel,
    pagerState: androidx.compose.foundation.pager.PagerState,
    preSelected: Long?,
    showActions: Boolean,
    onProfileSelect: (Long) -> Unit,
    contentPadding: PaddingValues,
    canHoldFocus: Boolean,
    onOpenProfileEditor: ((NavRoutes.ProfileEditor) -> Unit)? = null,
) {
    val snackbar = LocalSnackbarEmitter.current

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val hasGroups = uiState.groups.isNotEmpty()
    var qrCodeInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = modifier) {
        if (hasGroups) {
            val parentLifecycleOwner = LocalLifecycleOwner.current
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val group = uiState.groups[page]
                val pageLifecycleOwner = rememberLifecycleOwner(
                    maxLifecycle = if (pagerState.currentPage == page) {
                        Lifecycle.State.RESUMED
                    } else {
                        Lifecycle.State.CREATED
                    },
                    parent = parentLifecycleOwner,
                )
                val pageViewModel = viewModel<GroupProfilesHolderViewModel>(
                    key = "group-holder-${group.id}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(
                            modelClass: KClass<T>,
                            extras: CreationExtras,
                        ): T {
                            return GroupProfilesHolderViewModel(group, preSelected) as T
                        }
                    },
                )

                DisposableEffect(group.id) {
                    vm.registerChild(group.id, pageViewModel)
                    onDispose {
                        vm.unregisterChild(group.id)
                    }
                }

                CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
                    GroupProfilesHolderLifecycle(pageViewModel, pageLifecycleOwner)
                    GroupHolderScreen(
                        viewModel = pageViewModel,
                        showActions = showActions,
                        contentPadding = contentPadding,
                        // Focusing a page brings it into view, so a page passed during an
                        // animated jump must not take focus or it cancels the jump.
                        canHoldFocus = canHoldFocus && pagerState.settledPage == page,
                        onProfileSelect = onProfileSelect,
                        onOpenProfileEditor = onOpenProfileEditor,
                        showQR = { name, url ->
                            qrCodeInfo = name to url
                        },
                        onCopySuccess = {
                            snackbar.show(StringOrRes.Res(Res.string.copy_success))
                        },
                        showSnackbar = snackbar::show,
                        showUndoSnackbar = { count, onUndo ->
                            snackbar.show(
                                StringOrRes.PluralsRes(Res.plurals.removed, count, count),
                                StringOrRes.Res(Res.string.undo),
                            ) { result ->
                                if (result == SnackbarResult.ActionPerformed) {
                                    onUndo()
                                }
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    qrCodeInfo?.let {
        QRCodeDialog(
            url = it.second,
            name = it.first,
            onDismiss = { qrCodeInfo = null },
            showSnackbar = { message ->
                snackbar.show(StringOrRes.Direct(message))
            },
        )
    }
}

@Composable
private fun GroupProfilesHolderLifecycle(
    viewModel: GroupProfilesHolderViewModel,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    LifecycleStartEffect(viewModel, lifecycleOwner = lifecycleOwner) {
        viewModel.startObserving()
        onStopOrDispose {
            viewModel.stopObserving()
        }
    }
}

@Composable
private fun ConfigurationDialogs(
    vm: ConfigurationScreenViewModel,
    uiState: ConfigurationUiState,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var dialogKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                dialogKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    uiState.alertForDelete?.let { alert ->
        ScrollableDialog(
            onDismissRequest = { vm.dismissAlert() },
            title = {
                Text(
                    pluralStringResource(
                        Res.plurals.delete_profiles_confirm_prompt,
                        alert.size,
                        alert.size,
                    ),
                )
            },
            text = { Text(text = alert.summary) },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    alert.confirm()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    vm.dismissAlert()
                }
            },
        )
    }

    uiState.testState?.let { testState ->
        key(dialogKey) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    TextButton(stringResource(Res.string.cancel)) {
                        vm.cancelTest()
                    }
                },
                icon = {
                    Icon(vectorResource(Res.drawable.ecg), null)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { (testState.processedCount.toDouble() / testState.total.toDouble()).toFloat() },
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        testState.latestResult?.let { result ->
                            val profile = result.profile
                            val (statusText, statusColor) = when (val testResult = result.result) {
                                is TestResult.Success -> {
                                    "${testResult.ping}ms" to colorForUrlTestDelay(testResult.ping)
                                }

                                is TestResult.Failure -> {
                                    val text = when (val reason = testResult.reason) {
                                        FailureReason.InvalidConfig ->
                                            stringResource(
                                                Res.string.connection_test_error,
                                                "Invalid Config",
                                            )

                                        FailureReason.DomainNotFound -> {
                                            stringResource(Res.string.connection_test_domain_not_found)
                                        }

                                        FailureReason.IcmpUnavailable -> {
                                            stringResource(Res.string.connection_test_icmp_ping_unavailable)
                                        }

                                        FailureReason.TcpUnavailable -> {
                                            stringResource(Res.string.connection_test_tcp_ping_unavailable)
                                        }

                                        FailureReason.ConnectionRefused -> {
                                            stringResource(Res.string.connection_test_refused)
                                        }

                                        FailureReason.NetworkUnreachable -> {
                                            stringResource(Res.string.connection_test_unreachable)
                                        }

                                        FailureReason.Timeout -> {
                                            stringResource(Res.string.connection_test_timeout)
                                        }

                                        is FailureReason.Generic -> reason.message ?: "Unknown"

                                        is FailureReason.PluginNotFound -> {
                                            stringResource(Res.string.plugin_unknown, reason.plugin)
                                        }
                                    }
                                    text to MaterialTheme.colorScheme.error
                                }
                            }

                            LaunchedEffect(profile.id, statusText, result.result) {
                                vm.rememberDisplayedError(
                                    profile.id,
                                    statusText.takeIf { result.result is TestResult.Failure },
                                )
                            }

                            Text(profile.displayName())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profile.displayType(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                color = statusColor,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${testState.processedCount} / ${testState.total}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
            )
        }
    }
}
