package com.fr.husi.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.fr.husi.bg.Executable
import com.fr.husi.compose.BoxedVerticalScrollbar
import com.fr.husi.compose.ProvidePreferenceLocals
import com.fr.husi.compose.SimpleIconButton
import com.fr.husi.compose.SimpleTopAppBar
import com.fr.husi.compose.fadingEdge
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.compose.withNavigation
import com.fr.husi.database.DataStore
import com.fr.husi.database.SagerDatabase
import com.fr.husi.ktx.restartApplication
import com.fr.husi.ktx.runOnDefaultDispatcher
import com.fr.husi.repository.resolveRepository
import com.fr.husi.resources.Res
import com.fr.husi.resources.app_update_settings
import com.fr.husi.resources.apply
import com.fr.husi.resources.arrow_back
import com.fr.husi.resources.back
import com.fr.husi.resources.cag_dns
import com.fr.husi.resources.cag_misc
import com.fr.husi.resources.general_settings
import com.fr.husi.resources.inbound_settings
import com.fr.husi.resources.need_reload
import com.fr.husi.resources.need_restart
import com.fr.husi.resources.ntp_category
import com.fr.husi.resources.protocol_settings
import com.fr.husi.resources.route_options
import com.fr.husi.resources.system_daemon
import com.fr.husi.ui.LocalSnackbarEmitter
import com.fr.husi.ui.NavRoutes
import com.fr.husi.ui.StringOrRes
import com.fr.husi.ui.PlatformDaemonSettingsGroup
import com.fr.husi.ui.platformAppUpdateSettings
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SettingsPageScreen(
    kind: NavRoutes.SettingsPage.Kind,
    onBackPress: () -> Unit,
    openAppManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()
    val windowInsets = WindowInsets.safeDrawing
    val snackbar = LocalSnackbarEmitter.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            DataStore.initGlobal()
        }
    }

    fun needReload() {
        if (!DataStore.serviceState.started) return
        snackbar.show(
            StringOrRes.Res(Res.string.need_reload),
            StringOrRes.Res(Res.string.apply),
        ) { result ->
            if (result == SnackbarResult.Dismissed) return@show
            resolveRepository().reloadService()
        }
    }

    fun needRestart() {
        snackbar.show(
            StringOrRes.Res(Res.string.need_restart),
            StringOrRes.Res(Res.string.apply),
        ) { result ->
            if (result == SnackbarResult.Dismissed) return@show
            resolveRepository().stopService()
            runOnDefaultDispatcher {
                delay(500.milliseconds)
                SagerDatabase.instance.close()
                Executable.killAll(true)
                restartApplication()
            }
        }
    }

    val title = when (kind) {
        NavRoutes.SettingsPage.Kind.General -> Res.string.general_settings
        NavRoutes.SettingsPage.Kind.Daemon -> Res.string.system_daemon
        NavRoutes.SettingsPage.Kind.Route -> Res.string.route_options
        NavRoutes.SettingsPage.Kind.Protocol -> Res.string.protocol_settings
        NavRoutes.SettingsPage.Kind.Dns -> Res.string.cag_dns
        NavRoutes.SettingsPage.Kind.Inbound -> Res.string.inbound_settings
        NavRoutes.SettingsPage.Kind.Misc -> Res.string.cag_misc
        NavRoutes.SettingsPage.Kind.Ntp -> Res.string.ntp_category
        NavRoutes.SettingsPage.Kind.AppUpdate -> Res.string.app_update_settings
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                hazeState = hazeState,
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    when (kind) {
                        NavRoutes.SettingsPage.Kind.General -> preferenceGroup {
                            GeneralSettingsGroup(
                                needReload = { needReload() },
                                needRestart = { needRestart() },
                                showMessage = { message ->
                                    snackbar.show(StringOrRes.Direct(message))
                                },
                            )
                        }
                        NavRoutes.SettingsPage.Kind.Daemon -> preferenceGroup {
                            PlatformDaemonSettingsGroup(
                                showMessage = { message ->
                                    snackbar.show(StringOrRes.Direct(message))
                                },
                            )
                        }
                        NavRoutes.SettingsPage.Kind.Route -> preferenceGroup {
                            RouteSettingsGroup(
                                needReload = { needReload() },
                                openAppManager = openAppManager,
                            )
                        }
                        NavRoutes.SettingsPage.Kind.Protocol -> preferenceGroup {
                            ProtocolSettingsGroup(
                                needReload = { needReload() },
                            )
                        }
                        NavRoutes.SettingsPage.Kind.Dns -> preferenceGroup {
                            DnsSettingsGroup(
                                needReload = { needReload() },
                            )
                        }
                        NavRoutes.SettingsPage.Kind.Inbound -> preferenceGroup {
                            InboundSettingsGroup(
                                needReload = { needReload() },
                            )
                        }
                        NavRoutes.SettingsPage.Kind.Misc -> preferenceGroup {
                            MiscSettingsGroup(
                                needReload = { needReload() },
                                needRestart = { needRestart() },
                            )
                        }
                        NavRoutes.SettingsPage.Kind.Ntp -> preferenceGroup {
                            NtpSettingsGroup(
                                needReload = { needReload() },
                            )
                        }
                        NavRoutes.SettingsPage.Kind.AppUpdate -> platformAppUpdateSettings()
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }
}
